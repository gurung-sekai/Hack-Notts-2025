package launcher;

import Battle.scene.BossBattlePanel;
import World.DungeonRooms;
import World.DungeonRoomsSnapshot;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import security.GameSecurity;
import security.integrity.IntegrityCheckReport;
import security.integrity.IntegrityCheckStatus;
import util.ResourceLoader;

/**
 * Swing launcher that lets players adjust runtime settings before starting the game.
 */
public final class GameLauncher {
    private static final DimensionOption[] RESOLUTIONS = {
            new DimensionOption("756 x 468 (original scale)", new Dimension(756, 468)),
            new DimensionOption("1008 x 624 (large)", new Dimension(1008, 624)),
            new DimensionOption("1260 x 780 (extra large)", new Dimension(1260, 780)),
            new DimensionOption("1920 x 1080 (HD)", new Dimension(1920, 1080)),
            new DimensionOption("2560 x 1440 (1440p)", new Dimension(2560, 1440))
    };

    private static final Integer[] REFRESH_OPTIONS = {30, 45, 60, 75, 90, 120, 144, 165, 240};

    private static final String CARD_MENU = "menu";
    private static final String CARD_GAME = "game";
    private static final String CARD_BOSS = "boss";
    private static final KeyStroke ESCAPE_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

    private static final LocaleOption[] LANGUAGES = {
            new LocaleOption("English", Locale.UK),
            new LocaleOption("Cymraeg", new Locale("cy", "GB"))
    };

    private static final String[] BACKGROUND_CANDIDATES = {
            "resources/Cutscene/Throne/ThroneAnim3.png",
            "resources/Cutscene/Dungeon/DungeonAnim2.png",
            "resources/Miscellanious/weapon_lavish_sword.png"
    };

    private static final String[] MUSIC_CANDIDATES = {
            "resources/Miscellanious/title_theme.ogg",
            "resources/Miscellanious/title_theme.wav",
            "resources/Miscellanious/title_theme.mp3"
    };

    private final Path storageDir;
    private final SettingsPersistence settingsPersistence;
    private final SaveManager saveManager;
    private GameSettings settings;

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private MenuPanel menuCard;
    private JPanel gameWrapper;
    private JPanel bossWrapper;
    private DungeonRooms currentGame;
    private GraphicsDevice fullscreenDevice;
    private Font menuFont;
    private final Map<ControlAction, JButton> controlButtons = new EnumMap<>(ControlAction.class);
    private JButton resumeButton;
    private JToggleButton musicToggle;
    private JLabel subtitleLabel;
    private FadingLabel titleLabel;
    private Timer animationTimer;
    private float titleAlpha;
    private float glowPhase;
    private BufferedImage titleBackground;
    private BufferedImage scaledBackground;
    private Dimension scaledBackgroundSize;
    private TitleMusicPlayer musicPlayer;
    private OptionsDialog optionsDialog;
    private final IntegrityCheckReport integrityReport;
    private boolean escapeToCloseEnabled = true;

    public static void main(String[] args) {
        GameSecurity.verifyIntegrity();
        SwingUtilities.invokeLater(() -> {
            GameLauncher launcher = new GameLauncher();
            launcher.show();
        });
    }

    public GameLauncher() {
        storageDir = Path.of(System.getProperty("user.home"), ".dungeonrooms");
        settingsPersistence = new SettingsPersistence(storageDir);
        saveManager = new SaveManager(storageDir);
        settings = settingsPersistence.load().orElseGet(GameSettings::new);
        integrityReport = GameSecurity.verifyIntegrity();
        prepareTitleAssets();
    }

    private void show() {
        frame = new JFrame("Dungeon Rooms");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                releaseFullScreen();
                shutdownCurrentGame();
                stopTitleMusic();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                releaseFullScreen();
                shutdownCurrentGame();
                stopTitleMusic();
            }
        });

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(Color.BLACK);
        menuCard = buildMenuCard();
        cardPanel.add(menuCard, CARD_MENU);

        frame.setContentPane(cardPanel);
        frame.setBackground(Color.BLACK);
        frame.setResizable(false);
        frame.setUndecorated(settings.fullscreen());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        if (settings.fullscreen()) {
            enableFullScreen();
        }

        enableEscapeToClose();

        showMenuCard();

        SwingUtilities.invokeLater(this::reportIntegrityOutcome);
    }

    private void prepareTitleAssets() {
        if (titleBackground == null) {
            for (String candidate : BACKGROUND_CANDIDATES) {
                try {
                    titleBackground = ResourceLoader.image(candidate);
                    if (titleBackground != null) {
                        break;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        if (musicPlayer == null) {
            musicPlayer = locateMusicPlayer();
        }
    }

    private MenuPanel buildMenuCard() {
        controlButtons.clear();

        MenuPanel wrapper = new MenuPanel();
        wrapper.setPreferredSize(new Dimension(1280, 720));
        wrapper.setLayout(new GridBagLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(12, 24, 12, 24);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        titleLabel = new FadingLabel("Dungeon Rooms");
        titleLabel.setFont(loadMenuFont(80f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(new Color(244, 242, 230));
        content.add(titleLabel, gbc);

        gbc.gridy++;
        subtitleLabel = new JLabel("A roguelike quest to reclaim the queen");
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        subtitleLabel.setFont(loadMenuFont(28f));
        subtitleLabel.setForeground(new Color(214, 233, 255, 180));
        content.add(subtitleLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(32, 24, 12, 24);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 12));
        buttons.setOpaque(false);

        JButton newGame = createPrimaryButton("Start New Game", loadMenuFont(30f));
        newGame.addActionListener(e -> launchGame(Optional.empty()));

        resumeButton = createPrimaryButton("Resume Adventure", loadMenuFont(30f));
        resumeButton.addActionListener(e -> resumeGame());
        resumeButton.setEnabled(saveManager.hasSave());

        JButton howToPlay = createPrimaryButton("How to Play", loadMenuFont(26f));
        howToPlay.addActionListener(e -> showHowToPlay());

        JButton options = createPrimaryButton("Options", loadMenuFont(26f));
        options.addActionListener(e -> showOptionsDialog());

        JButton quit = createPrimaryButton("Quit", loadMenuFont(26f));
        quit.addActionListener(e -> closeLauncher());

        buttons.add(newGame);
        buttons.add(resumeButton);
        buttons.add(howToPlay);
        buttons.add(options);
        buttons.add(quit);
        content.add(buttons, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(18, 24, 32, 24);
        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        toggles.setOpaque(false);
        musicToggle = createMenuToggle("Music");
        musicToggle.addActionListener(e -> {
            settings.setMusicEnabled(musicToggle.isSelected());
            if (settings.musicEnabled()) {
                startTitleMusic();
            } else {
                stopTitleMusic();
            }
            applyMusicSettings();
            persistSettings();
        });
        toggles.add(musicToggle);
        content.add(toggles, gbc);

        GridBagConstraints outer = new GridBagConstraints();
        outer.gridx = 0;
        outer.gridy = 0;
        outer.weightx = 1.0;
        outer.weighty = 1.0;
        outer.anchor = GridBagConstraints.CENTER;
        wrapper.add(content, outer);

        return wrapper;
    }

    private void showOptionsDialog() {
        if (optionsDialog == null) {
            optionsDialog = new OptionsDialog();
        }
        optionsDialog.open();
    }

    private void resumeGame() {
        boolean hadSave = saveManager.hasSave();
        Optional<DungeonRoomsSnapshot> snapshot = saveManager.load();
        if (snapshot.isEmpty()) {
            String message = hadSave
                    ? "Your previous save could not be loaded. A new adventure will begin."
                    : "No saved game was found. Starting a fresh run.";
            JOptionPane.showMessageDialog(frame, message, "Resume", hadSave
                    ? JOptionPane.WARNING_MESSAGE
                    : JOptionPane.INFORMATION_MESSAGE);
            launchGame(Optional.empty());
            return;
        }
        launchGame(snapshot);
    }

    private void launchGame(Optional<DungeonRoomsSnapshot> snapshot) {
        stopMenuAnimation();
        stopTitleMusic();

        disableEscapeToClose();

        GameSettings launchSettings = new GameSettings(settings);
        ControlsProfile controlsCopy = launchSettings.controls();
        LanguageBundle bundle = new LanguageBundle(launchSettings.language());

        Consumer<DungeonRoomsSnapshot> saver = snap -> {
            try {
                saveManager.save(snap);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };

        Runnable exit = () -> SwingUtilities.invokeLater(() -> {
            shutdownCurrentGame();
            if (gameWrapper != null) {
                cardPanel.remove(gameWrapper);
                gameWrapper = null;
            }
            if (bossWrapper != null) {
                cardPanel.remove(bossWrapper);
                bossWrapper = null;
            }
            currentGame = null;
            if (resumeButton != null) {
                resumeButton.setEnabled(saveManager.hasSave());
            }
            showMenuCard();
        });

        if (gameWrapper != null) {
            cardPanel.remove(gameWrapper);
            gameWrapper = null;
        }

        DungeonRoomsSnapshot data = snapshot.orElse(null);
        DungeonRooms.Difficulty difficulty = data != null ? data.difficulty() : promptDifficulty();
        DungeonRooms panel = new DungeonRooms(launchSettings, controlsCopy, bundle, saver, exit, data,
                new SwingBossBattleHost(), difficulty);
        panel.setPreferredSize(launchSettings.resolution());
        panel.setMinimumSize(launchSettings.resolution());
        panel.setMaximumSize(launchSettings.resolution());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.BLACK);
        wrapper.add(panel, BorderLayout.CENTER);

        currentGame = panel;
        gameWrapper = wrapper;
        cardPanel.add(wrapper, CARD_GAME);
        cardLayout.show(cardPanel, CARD_GAME);
        cardPanel.revalidate();
        cardPanel.repaint();

        SwingUtilities.invokeLater(panel::requestFocusInWindow);
    }

    private DungeonRooms.Difficulty promptDifficulty() {
        String[] options = {
                "Easy - respawn at last checkpoint",
                "Hard - no respawns"
        };
        int choice = JOptionPane.showOptionDialog(frame,
                "Choose your challenge.",
                "Select Difficulty",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        return choice == 1 ? DungeonRooms.Difficulty.HARD : DungeonRooms.Difficulty.EASY;
    }

    private void enableFullScreen() {
        if (!settings.fullscreen()) {
            return;
        }
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (device.isFullScreenSupported()) {
            fullscreenDevice = device;
            fullscreenDevice.setFullScreenWindow(frame);
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private void releaseFullScreen() {
        if (fullscreenDevice != null && fullscreenDevice.getFullScreenWindow() == frame) {
            fullscreenDevice.setFullScreenWindow(null);
        }
        fullscreenDevice = null;
    }

    private void applyDisplaySettings() {
        if (frame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            releaseFullScreen();
            frame.dispose();
            frame.setUndecorated(settings.fullscreen());
            frame.setContentPane(cardPanel);
            frame.pack();
            if (settings.fullscreen()) {
                frame.setVisible(true);
                enableFullScreen();
            } else {
                frame.setSize(menuCard.getPreferredSize());
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    private void reportIntegrityOutcome() {
        if (frame == null || integrityReport == null) {
            return;
        }

        IntegrityCheckStatus status = integrityReport.status();
        switch (status) {
            case VERIFIED, SKIPPED_ALREADY_VERIFIED -> {
                return;
            }
            case VERIFIED_WITH_FAILURES -> {
                StringBuilder builder = new StringBuilder();
                builder.append("The launcher detected modified or missing files:\n\n");
                integrityReport.failedEntries().stream()
                        .limit(5)
                        .forEach(path -> builder.append(" • ").append(path).append('\n'));
                if (integrityReport.failedEntries().size() > 5) {
                    builder.append('\n').append("Additional files were also affected.");
                }
                builder.append("\nPlease reinstall or verify your game files to restore integrity checks.");
                JOptionPane.showMessageDialog(frame, builder.toString(),
                        "Security warning", JOptionPane.WARNING_MESSAGE);
            }
            case SKIPPED_MANIFEST_MISSING -> JOptionPane.showMessageDialog(frame,
                    "The security manifest could not be found. Runtime checks were skipped.",
                    "Security warning", JOptionPane.WARNING_MESSAGE);
            case SKIPPED_MANIFEST_UNTRUSTED -> JOptionPane.showMessageDialog(frame,
                    "The security manifest failed validation and was ignored. Runtime checks were skipped.",
                    "Security warning", JOptionPane.ERROR_MESSAGE);
            case SKIPPED_NO_ENTRIES -> JOptionPane.showMessageDialog(frame,
                    "The security manifest did not contain any entries. Runtime checks were skipped.",
                    "Security notice", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void shutdownCurrentGame() {
        if (currentGame != null) {
            currentGame.shutdown();
        }
    }

    private void showMenuCard() {
        enableEscapeToClose();
        if (resumeButton != null) {
            resumeButton.setEnabled(saveManager.hasSave());
        }
        if (musicToggle != null) {
            musicToggle.setSelected(settings.musicEnabled());
            musicToggle.setEnabled(musicPlayer != null);
        }
        cardLayout.show(cardPanel, CARD_MENU);
        cardPanel.revalidate();
        cardPanel.repaint();
        if (menuCard != null) {
            menuCard.requestFocusInWindow();
        }
        startMenuAnimation();
        applyMusicSettings();
        if (settings.musicEnabled()) {
            startTitleMusic();
        }
    }

    private void closeLauncher() {
        if (frame != null) {
            stopMenuAnimation();
            stopTitleMusic();
            if (musicPlayer != null) {
                musicPlayer.close();
            }
            disableEscapeToClose();
            frame.dispose();
        }
    }

    private void enableEscapeToClose() {
        if (frame == null) {
            return;
        }
        disableEscapeToClose();
        frame.getRootPane().registerKeyboardAction(e -> closeLauncher(),
                ESCAPE_KEYSTROKE,
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void disableEscapeToClose() {
        if (frame == null) {
            return;
        }
        frame.getRootPane().unregisterKeyboardAction(ESCAPE_KEYSTROKE);
    }

    private void startMenuAnimation() {
        stopMenuAnimation();
        titleAlpha = 0f;
        glowPhase = 0f;
        animationTimer = new Timer(16, e -> {
            titleAlpha = Math.min(1f, titleAlpha + 0.02f);
            glowPhase += 0.04f;
            if (titleLabel != null) {
                titleLabel.setAlpha(titleAlpha);
            }
            if (subtitleLabel != null) {
                float pulse = (float) ((Math.sin(glowPhase) + 1f) * 0.5f);
                subtitleLabel.setForeground(new Color(214, 233, 255, (int) (170 + 60 * pulse)));
            }
            if (menuCard != null) {
                menuCard.repaint();
            }
        });
        animationTimer.start();
    }

    private void stopMenuAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    private void applyMusicSettings() {
        if (musicToggle != null) {
            musicToggle.setSelected(settings.musicEnabled());
            musicToggle.setEnabled(musicPlayer != null);
        }
        if (musicPlayer != null && musicPlayer.isReady()) {
            musicPlayer.setVolume(settings.musicVolume());
            if (!settings.musicEnabled()) {
                musicPlayer.stop();
            }
        }
    }

    private void startTitleMusic() {
        if (!settings.musicEnabled()) {
            return;
        }
        if (musicPlayer == null) {
            musicPlayer = locateMusicPlayer();
        }
        if (musicPlayer != null && musicPlayer.prepare()) {
            musicPlayer.playLoop(settings.musicVolume());
            if (musicToggle != null) {
                musicToggle.setEnabled(true);
                musicToggle.setSelected(true);
            }
        } else {
            settings.setMusicEnabled(false);
            if (musicToggle != null) {
                musicToggle.setEnabled(false);
                musicToggle.setSelected(false);
            }
            persistSettings();
        }
    }

    private void stopTitleMusic() {
        if (musicPlayer != null) {
            musicPlayer.stop();
        }
    }

    private TitleMusicPlayer locateMusicPlayer() {
        for (String candidate : MUSIC_CANDIDATES) {
            try (InputStream in = ResourceLoader.open(candidate)) {
                if (in != null) {
                    return new TitleMusicPlayer(candidate);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private void showHowToPlay() {
        ControlsProfile profile = settings.mutableControls();
        String message = "Explore the Ember Caverns, unlock sealed doors, and defeat guardians to recover " +
                "lost relics.\n\n" +
                String.format(
                        "Move: %s/%s/%s/%s\n" +
                                "Shoot: %s\n" +
                                "Dash (invulnerable burst): %s\n" +
                                "Parry (reflect projectiles): %s\n" +
                                "Special (ring/pulse): %s\n" +
                                "Reroll obstacles: %s\n" +
                                "Pause & access menu: %s",
                        key(profile, ControlAction.MOVE_UP),
                        key(profile, ControlAction.MOVE_DOWN),
                        key(profile, ControlAction.MOVE_LEFT),
                        key(profile, ControlAction.MOVE_RIGHT),
                        key(profile, ControlAction.SHOOT),
                        key(profile, ControlAction.DASH),
                        key(profile, ControlAction.PARRY),
                        key(profile, ControlAction.SPECIAL),
                        key(profile, ControlAction.REROLL),
                        key(profile, ControlAction.PAUSE));
        JOptionPane.showMessageDialog(frame, message, "How to play", JOptionPane.INFORMATION_MESSAGE);
    }

    private void promptRebind(ControlAction action) {
        JOptionPane optionPane = new JOptionPane("Press a key for " + action.name().replace('_', ' '),
                JOptionPane.QUESTION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        JDialog dialog = optionPane.createDialog(frame, "Rebind " + action.name());
        dialog.setModal(true);
        dialog.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                settings.mutableControls().rebind(action, e.getKeyCode());
                updateControlButton(action);
                dialog.dispose();
            }
        });
        dialog.setVisible(true);
    }

    private void updateControlButton(ControlAction action) {
        JButton button = controlButtons.get(action);
        if (button != null) {
            button.setText(controlLabel(action));
        }
    }

    private String controlLabel(ControlAction action) {
        return key(settings.mutableControls(), action);
    }

    private String key(ControlsProfile profile, ControlAction action) {
        return KeyEvent.getKeyText(profile.keyFor(action));
    }

    private JComboBox<DimensionOption> styleComboBox(JComboBox<DimensionOption> box, Font font) {
        box.setFont(font);
        box.setForeground(new Color(236, 240, 248));
        box.setBackground(new Color(30, 44, 62, 220));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setForeground(isSelected ? new Color(18, 24, 32) : new Color(240, 242, 250));
                c.setBackground(isSelected ? new Color(210, 230, 252) : new Color(20, 30, 42));
                return c;
            }
        });
        return box;
    }

    private JComboBox<Integer> styleComboBox(JComboBox<Integer> box, Font font, Color fg) {
        box.setFont(font);
        box.setForeground(fg);
        box.setBackground(new Color(30, 44, 62, 220));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setForeground(isSelected ? new Color(18, 24, 32) : new Color(240, 242, 250));
                c.setBackground(isSelected ? new Color(210, 230, 252) : new Color(20, 30, 42));
                return c;
            }
        });
        return box;
    }

    private JButton createPrimaryButton(String label, Font font) {
        MenuButton button = new MenuButton(label);
        button.setFont(font);
        button.setForeground(new Color(248, 246, 236));
        return button;
    }

    private JToggleButton createMenuToggle(String label) {
        MenuToggle toggle = new MenuToggle(label);
        toggle.setFont(loadMenuFont(22f));
        toggle.setForeground(new Color(240, 242, 252));
        toggle.setSelected(settings.musicEnabled());
        toggle.setEnabled(musicPlayer != null);
        return toggle;
    }

    private JButton createControlButton(String label, Font font) {
        MenuButton button = new MenuButton(label);
        button.setFont(font);
        return button;
    }

    private Font loadMenuFont(float size) {
        if (menuFont == null) {
            try (InputStream in = ResourceLoader.open("resources/Font/ThaleahFat.ttf")) {
                if (in != null) {
                    menuFont = Font.createFont(Font.TRUETYPE_FONT, in);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(menuFont);
                } else {
                    menuFont = new JLabel().getFont();
                }
            } catch (FontFormatException | IOException ex) {
                menuFont = new JLabel().getFont();
            }
        }
        return menuFont.deriveFont(size);
    }

    private DimensionOption findResolution(Dimension resolution) {
        for (DimensionOption option : RESOLUTIONS) {
            if (option.dimension().equals(resolution)) {
                return option;
            }
        }
        return RESOLUTIONS[0];
    }

    private Integer findRefreshOption(int refreshRate) {
        for (Integer option : REFRESH_OPTIONS) {
            if (option == refreshRate) {
                return option;
            }
        }
        return REFRESH_OPTIONS[2];
    }

    private LocaleOption findLanguage(Locale locale) {
        for (LocaleOption option : LANGUAGES) {
            if (option.locale().toLanguageTag().equalsIgnoreCase(locale.toLanguageTag())) {
                return option;
            }
        }
        return LANGUAGES[0];
    }

    private TitleMusicPlayer getOrCreateMusicPlayer() {
        if (musicPlayer == null) {
            musicPlayer = locateMusicPlayer();
        }
        return musicPlayer;
    }

    private void persistSettings() {
        try {
            settingsPersistence.save(settings);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Unable to save settings: " + ex.getMessage(),
                    "Launcher", JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class SwingBossBattleHost implements DungeonRooms.BossBattleHost {
        @Override
        public void runBossBattle(BossBattlePanel.BossKind kind,
                                  BossBattlePanel.BattleTuning tuning,
                                  Consumer<BossBattlePanel.Outcome> outcomeHandler) {
            BossBattlePanel panel = BossBattlePanel.create(kind, tuning, outcome -> {
                outcomeHandler.accept(outcome);
                SwingUtilities.invokeLater(() -> {
                    if (bossWrapper != null) {
                        cardPanel.remove(bossWrapper);
                        bossWrapper = null;
                    }
                    cardLayout.show(cardPanel, CARD_GAME);
                    cardPanel.revalidate();
                    cardPanel.repaint();
                    if (currentGame != null) {
                        currentGame.requestFocusInWindow();
                    }
                });
            });
            panel.setOpaque(true);
            panel.setBackground(Color.BLACK);

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(Color.BLACK);
            wrapper.add(panel, BorderLayout.CENTER);

            if (bossWrapper != null) {
                cardPanel.remove(bossWrapper);
            }
            bossWrapper = wrapper;
            cardPanel.add(wrapper, CARD_BOSS);
            cardLayout.show(cardPanel, CARD_BOSS);
            cardPanel.revalidate();
            cardPanel.repaint();
            SwingUtilities.invokeLater(panel::requestFocusInWindow);
        }

        @Override
        public void runBossBattle(BossBattlePanel.BossKind kind, Consumer<BossBattlePanel.Outcome> outcomeHandler) {
            runBossBattle(kind, null, outcomeHandler);
        }
    }

    private final class MenuPanel extends JPanel {
        private MenuPanel() {
            setOpaque(false);
            setFocusable(true);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (titleBackground != null) {
                    paintBackground(g2, getWidth(), getHeight());
                } else {
                    GradientPaint gradient = new GradientPaint(0, 0, new Color(8, 12, 24),
                            getWidth(), getHeight(), new Color(26, 36, 56));
                    g2.setPaint(gradient);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
                g2.setColor(new Color(8, 14, 24, 220));
                g2.fillRect(0, 0, getWidth(), getHeight());
                float pulse = (float) ((Math.sin(glowPhase) + 1f) * 0.5f);
                g2.setComposite(AlphaComposite.SrcOver.derive(0.15f + 0.12f * pulse));
                RadialGradientPaint halo = new RadialGradientPaint(new Point2D.Float(getWidth() / 2f, getHeight() / 2f),
                        Math.max(getWidth(), getHeight()),
                        new float[]{0f, 1f},
                        new Color[]{new Color(96, 144, 220, 180), new Color(8, 12, 24, 0)});
                g2.setPaint(halo);
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }

        private void paintBackground(Graphics2D g2, int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            if (scaledBackground == null || scaledBackgroundSize == null
                    || scaledBackgroundSize.width != width || scaledBackgroundSize.height != height) {
                scaledBackground = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D bg = scaledBackground.createGraphics();
                try {
                    bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    double scale = Math.max(width / (double) titleBackground.getWidth(),
                            height / (double) titleBackground.getHeight());
                    int drawW = (int) Math.round(titleBackground.getWidth() * scale);
                    int drawH = (int) Math.round(titleBackground.getHeight() * scale);
                    int x = (width - drawW) / 2;
                    int y = (height - drawH) / 2;
                    bg.drawImage(titleBackground, x, y, drawW, drawH, null);
                } finally {
                    bg.dispose();
                }
                scaledBackgroundSize = new Dimension(width, height);
            }
            g2.drawImage(scaledBackground, 0, 0, null);
        }
    }

    private final class OptionsDialog extends JDialog {
        private final JComboBox<DimensionOption> resolutionBox;
        private final JComboBox<Integer> refreshBox;
        private final JComboBox<LocaleOption> languageBox;
        private final JCheckBox fullscreenToggle;
        private final JCheckBox cutsceneToggle;
        private final JCheckBox musicEnableToggle;
        private final JSlider musicSlider;
        private final JSlider sfxSlider;

        private OptionsDialog() {
            super(frame, "Options", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            JPanel root = new JPanel(new GridBagLayout());
            root.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
            root.setBackground(new Color(12, 18, 30, 240));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            JLabel display = new JLabel("Display");
            display.setFont(loadMenuFont(28f));
            display.setForeground(new Color(240, 244, 252));
            root.add(display, gbc);

            gbc.gridy++;
            JPanel resolutionRow = new JPanel(new GridBagLayout());
            resolutionRow.setOpaque(false);
            GridBagConstraints row = new GridBagConstraints();
            row.gridx = 0;
            row.gridy = 0;
            row.insets = new Insets(4, 4, 4, 12);
            JLabel resolutionLabel = new JLabel("Resolution");
            resolutionLabel.setFont(loadMenuFont(20f));
            resolutionLabel.setForeground(new Color(210, 226, 248));
            resolutionRow.add(resolutionLabel, row);

            row.gridx = 1;
            resolutionBox = styleComboBox(new JComboBox<>(RESOLUTIONS), loadMenuFont(20f));
            resolutionRow.add(resolutionBox, row);
            root.add(resolutionRow, gbc);

            gbc.gridy++;
            JPanel refreshRow = new JPanel(new GridBagLayout());
            refreshRow.setOpaque(false);
            GridBagConstraints refreshConstraints = new GridBagConstraints();
            refreshConstraints.gridx = 0;
            refreshConstraints.gridy = 0;
            refreshConstraints.insets = new Insets(4, 4, 4, 12);
            JLabel refreshLabel = new JLabel("Refresh rate");
            refreshLabel.setFont(loadMenuFont(20f));
            refreshLabel.setForeground(new Color(210, 226, 248));
            refreshRow.add(refreshLabel, refreshConstraints);

            refreshConstraints.gridx = 1;
            refreshBox = styleComboBox(new JComboBox<>(REFRESH_OPTIONS), loadMenuFont(20f), new Color(236, 240, 248));
            refreshRow.add(refreshBox, refreshConstraints);
            root.add(refreshRow, gbc);

            gbc.gridy++;
            fullscreenToggle = new JCheckBox("Enable fullscreen");
            fullscreenToggle.setOpaque(false);
            fullscreenToggle.setFont(loadMenuFont(20f));
            fullscreenToggle.setForeground(new Color(210, 226, 248));
            root.add(fullscreenToggle, gbc);

            gbc.gridy++;
            JPanel languageRow = new JPanel(new GridBagLayout());
            languageRow.setOpaque(false);
            GridBagConstraints langConstraints = new GridBagConstraints();
            langConstraints.gridx = 0;
            langConstraints.gridy = 0;
            langConstraints.insets = new Insets(4, 4, 4, 12);
            JLabel languageLabel = new JLabel("Language");
            languageLabel.setFont(loadMenuFont(20f));
            languageLabel.setForeground(new Color(210, 226, 248));
            languageRow.add(languageLabel, langConstraints);

            langConstraints.gridx = 1;
            languageBox = new JComboBox<>(LANGUAGES);
            languageBox.setFont(loadMenuFont(20f));
            languageBox.setRenderer(new DefaultListCellRenderer());
            languageRow.add(languageBox, langConstraints);
            root.add(languageRow, gbc);

            gbc.gridy++;
            JLabel audioLabel = new JLabel("Audio");
            audioLabel.setFont(loadMenuFont(28f));
            audioLabel.setForeground(new Color(240, 244, 252));
            root.add(audioLabel, gbc);

            gbc.gridy++;
            musicEnableToggle = new JCheckBox("Enable music");
            musicEnableToggle.setOpaque(false);
            musicEnableToggle.setFont(loadMenuFont(20f));
            musicEnableToggle.setForeground(new Color(210, 226, 248));
            root.add(musicEnableToggle, gbc);

            gbc.gridy++;
            musicSlider = new JSlider(0, 100);
            musicSlider.setOpaque(false);
            musicSlider.setMajorTickSpacing(25);
            musicSlider.setMinorTickSpacing(5);
            musicSlider.setPaintTicks(true);
            musicSlider.setPaintLabels(true);
            musicSlider.setForeground(new Color(210, 226, 248));
            musicSlider.addChangeListener(new VolumePreview(true));
            root.add(labeledSlider("Music volume", musicSlider), gbc);

            gbc.gridy++;
            sfxSlider = new JSlider(0, 100);
            sfxSlider.setOpaque(false);
            sfxSlider.setMajorTickSpacing(25);
            sfxSlider.setMinorTickSpacing(5);
            sfxSlider.setPaintTicks(true);
            sfxSlider.setPaintLabels(true);
            sfxSlider.setForeground(new Color(210, 226, 248));
            sfxSlider.addChangeListener(new VolumePreview(false));
            root.add(labeledSlider("SFX volume", sfxSlider), gbc);

            gbc.gridy++;
            JLabel narrativeLabel = new JLabel("Narrative");
            narrativeLabel.setFont(loadMenuFont(28f));
            narrativeLabel.setForeground(new Color(240, 244, 252));
            root.add(narrativeLabel, gbc);

            gbc.gridy++;
            cutsceneToggle = new JCheckBox("Enable cutscenes");
            cutsceneToggle.setOpaque(false);
            cutsceneToggle.setFont(loadMenuFont(20f));
            cutsceneToggle.setForeground(new Color(210, 226, 248));
            root.add(cutsceneToggle, gbc);

            gbc.gridy++;
            JLabel controlsLabel = new JLabel("Key bindings");
            controlsLabel.setFont(loadMenuFont(28f));
            controlsLabel.setForeground(new Color(240, 244, 252));
            root.add(controlsLabel, gbc);

            gbc.gridy++;
            JPanel controlsPanel = new JPanel(new GridBagLayout());
            controlsPanel.setOpaque(false);
            GridBagConstraints cg = new GridBagConstraints();
            cg.gridx = 0;
            cg.gridy = 0;
            cg.insets = new Insets(4, 4, 4, 8);
            cg.anchor = GridBagConstraints.WEST;
            for (ControlAction action : ControlAction.values()) {
                JLabel label = new JLabel(action.name().replace('_', ' '));
                label.setFont(loadMenuFont(20f));
                label.setForeground(new Color(210, 226, 248));
                controlsPanel.add(label, cg);

                cg.gridx = 1;
                JButton button = createControlButton(controlLabel(action), loadMenuFont(20f));
                button.addActionListener(e -> promptRebind(action));
                controlButtons.put(action, button);
                controlsPanel.add(button, cg);
                cg.gridx = 0;
                cg.gridy++;
            }
            root.add(controlsPanel, gbc);

            gbc.gridy++;
            gbc.insets = new Insets(18, 8, 0, 8);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            actions.setOpaque(false);
            JButton cancel = createPrimaryButton("Cancel", loadMenuFont(22f));
            cancel.addActionListener(e -> dispose());
            JButton apply = createPrimaryButton("Apply", loadMenuFont(22f));
            apply.addActionListener(e -> {
                applyChanges();
                dispose();
            });
            actions.add(cancel);
            actions.add(apply);
            root.add(actions, gbc);

            setContentPane(root);
            syncFromSettings();
            pack();
            setLocationRelativeTo(frame);
        }

        private JPanel labeledSlider(String label, JSlider slider) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            JLabel caption = new JLabel(label);
            caption.setFont(loadMenuFont(20f));
            caption.setForeground(new Color(210, 226, 248));
            panel.add(caption, BorderLayout.NORTH);
            panel.add(slider, BorderLayout.CENTER);
            return panel;
        }

        void open() {
            syncFromSettings();
            setVisible(true);
        }

        private void syncFromSettings() {
            resolutionBox.setSelectedItem(findResolution(settings.resolution()));
            refreshBox.setSelectedItem(findRefreshOption(settings.refreshRate()));
            languageBox.setSelectedItem(findLanguage(settings.language()));
            fullscreenToggle.setSelected(settings.fullscreen());
            musicEnableToggle.setSelected(settings.musicEnabled());
            cutsceneToggle.setSelected(settings.cutscenesEnabled());
            musicSlider.setValue(Math.round(settings.musicVolume() * 100f));
            sfxSlider.setValue(Math.round(settings.sfxVolume() * 100f));
            for (ControlAction action : ControlAction.values()) {
                updateControlButton(action);
            }
        }

        private void applyChanges() {
            DimensionOption resolution = (DimensionOption) resolutionBox.getSelectedItem();
            if (resolution != null) {
                settings.setResolution(resolution.dimension());
            }
            Integer refresh = (Integer) refreshBox.getSelectedItem();
            if (refresh != null) {
                settings.setRefreshRate(refresh);
            }
            LocaleOption language = (LocaleOption) languageBox.getSelectedItem();
            if (language != null) {
                settings.setLanguage(language.locale());
            }
            settings.setFullscreen(fullscreenToggle.isSelected());
            settings.setMusicEnabled(musicEnableToggle.isSelected());
            settings.setMusicVolume(musicSlider.getValue() / 100f);
            settings.setSfxVolume(sfxSlider.getValue() / 100f);
            settings.setCutscenesEnabled(cutsceneToggle.isSelected());
            persistSettings();
            applyDisplaySettings();
            applyMusicSettings();
            if (settings.musicEnabled()) {
                startTitleMusic();
            } else {
                stopTitleMusic();
            }
        }

        private final class VolumePreview implements ChangeListener {
            private final boolean music;

            private VolumePreview(boolean music) {
                this.music = music;
            }

            @Override
            public void stateChanged(ChangeEvent e) {
                if (music && musicPlayer != null && musicPlayer.isReady()) {
                    musicPlayer.setVolume(musicSlider.getValue() / 100f);
                }
            }
        }
    }

    private static final class MenuButton extends JButton {
        private MenuButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 26, 12, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = new Color(32, 46, 66, 210);
                Color hover = new Color(76, 118, 168, 230);
                Color disabled = new Color(24, 30, 40, 180);
                Color draw = !isEnabled() ? disabled : (getModel().isRollover() ? hover : base);
                g2.setColor(draw);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(255, 246, 214, isEnabled() ? 200 : 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }

        @Override
        public void setContentAreaFilled(boolean b) {
        }
    }

    private static final class MenuToggle extends JToggleButton {
        private MenuToggle(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = new Color(32, 48, 68, 210);
                Color hover = new Color(66, 102, 146, 220);
                Color active = new Color(94, 148, 206, 230);
                Color disabled = new Color(24, 32, 44, 160);
                Color draw;
                if (!isEnabled()) {
                    draw = disabled;
                } else if (isSelected()) {
                    draw = active;
                } else if (getModel().isRollover()) {
                    draw = hover;
                } else {
                    draw = base;
                }
                g2.setColor(draw);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(new Color(255, 246, 214, isEnabled() ? 200 : 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }

        @Override
        public void setContentAreaFilled(boolean b) {
        }
    }

    private static final class FadingLabel extends JLabel {
        private float alpha = 0f;

        private FadingLabel(String text) {
            super(text);
            setOpaque(false);
        }

        void setAlpha(float alpha) {
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                super.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        }
    }

    private record DimensionOption(String label, Dimension dimension) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record LocaleOption(String label, Locale locale) {
        @Override
        public String toString() {
            return label;
        }
    }
}
