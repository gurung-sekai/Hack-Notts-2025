package launcher;

import Battle.scene.BossBattlePanel;
import World.DungeonRooms;
import World.DungeonRoomsSnapshot;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
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
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

    private static final LocaleOption[] LANGUAGES = {
        new LocaleOption("English", Locale.UK),
        new LocaleOption("Cymraeg", new Locale("cy", "GB"))
    };

    private final Path storageDir;
    private final SettingsPersistence settingsPersistence;
    private final SaveManager saveManager;
    private GameSettings settings;

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JPanel menuCard;
    private JPanel gameWrapper;
    private JPanel bossWrapper;
    private DungeonRooms currentGame;
    private GraphicsDevice fullscreenDevice;
    private Font menuFont;
    private JComboBox<DimensionOption> resolutionBox;
    private JComboBox<Integer> refreshBox;
    private JComboBox<LocaleOption> languageBox;
    private final Map<ControlAction, JButton> controlButtons = new EnumMap<>(ControlAction.class);
    private JButton resumeButton;
    private JButton quitButton;
    private final IntegrityCheckReport integrityReport;

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
    }

    private void show() {
        frame = new JFrame("Dungeon Rooms Launcher");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                releaseFullScreen();
                shutdownCurrentGame();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                releaseFullScreen();
                shutdownCurrentGame();
            }
        });

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(Color.BLACK);
        menuCard = buildMenuCard();
        cardPanel.add(menuCard, CARD_MENU);

        frame.setContentPane(cardPanel);
        frame.setUndecorated(true);
        frame.setBackground(Color.BLACK);
        frame.setResizable(false);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        enableFullScreen();

        frame.getRootPane().registerKeyboardAction(e -> closeLauncher(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        showMenuCard();

        SwingUtilities.invokeLater(this::reportIntegrityOutcome);
    }

    private JPanel buildMenuCard() {
        controlButtons.clear();

        Font titleFont = loadMenuFont(72f);
        Font headingFont = loadMenuFont(32f);
        Font textFont = loadMenuFont(24f);
        Font buttonFont = loadMenuFont(28f);
        Font controlFont = loadMenuFont(20f);

        resolutionBox = styleComboBox(new JComboBox<>(RESOLUTIONS), textFont);
        resolutionBox.setSelectedItem(findResolution(settings.resolution()));

        refreshBox = styleComboBox(new JComboBox<>(REFRESH_OPTIONS), textFont);
        refreshBox.setSelectedItem(findRefreshOption(settings.refreshRate()));

        languageBox = styleComboBox(new JComboBox<>(LANGUAGES), textFont);
        languageBox.setSelectedItem(findLanguage(settings.language()));

        GlassPanel content = new GlassPanel();
        content.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(12, 24, 12, 24);
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel title = new JLabel("Dungeon Rooms");
        title.setFont(titleFont);
        title.setForeground(new Color(247, 242, 224));
        content.add(title, gbc);

        gbc.gridy++;
        JLabel subtitle = new JLabel("A handcrafted roguelike adventure");
        subtitle.setFont(loadMenuFont(26f));
        subtitle.setForeground(new Color(214, 233, 255));
        content.add(subtitle, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel displayHeading = new JLabel("Display settings");
        displayHeading.setFont(headingFont);
        displayHeading.setForeground(new Color(247, 242, 224));
        content.add(displayHeading, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(6, 24, 6, 12);
        JLabel resolutionLabel = new JLabel("Resolution");
        resolutionLabel.setFont(textFont);
        resolutionLabel.setForeground(new Color(224, 234, 247));
        content.add(resolutionLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(6, 12, 6, 24);
        content.add(resolutionBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets = new Insets(6, 24, 6, 12);
        JLabel refreshLabel = new JLabel("Refresh rate");
        refreshLabel.setFont(textFont);
        refreshLabel.setForeground(new Color(224, 234, 247));
        content.add(refreshLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(6, 12, 6, 24);
        content.add(refreshBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets = new Insets(6, 24, 6, 12);
        JLabel languageLabel = new JLabel("Language");
        languageLabel.setFont(textFont);
        languageLabel.setForeground(new Color(224, 234, 247));
        content.add(languageLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(6, 12, 6, 24);
        content.add(languageBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(18, 24, 6, 24);
        JLabel controlsHeading = new JLabel("Controls");
        controlsHeading.setFont(headingFont);
        controlsHeading.setForeground(new Color(247, 242, 224));
        content.add(controlsHeading, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(6, 24, 12, 24);
        JPanel controlsPanel = new JPanel(new GridBagLayout());
        controlsPanel.setOpaque(false);
        GridBagConstraints cg = new GridBagConstraints();
        cg.insets = new Insets(4, 6, 4, 6);
        cg.anchor = GridBagConstraints.WEST;
        cg.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        for (ControlAction action : ControlAction.values()) {
            cg.gridx = 0;
            cg.gridy = row;
            JLabel actionLabel = new JLabel(action.name().replace('_', ' '));
            actionLabel.setFont(textFont);
            actionLabel.setForeground(new Color(214, 233, 255));
            controlsPanel.add(actionLabel, cg);

            cg.gridx = 1;
            JButton button = createControlButton(controlLabel(action), controlFont);
            button.addActionListener(e -> promptRebind(action));
            controlButtons.put(action, button);
            controlsPanel.add(button, cg);
            row++;
        }
        content.add(controlsPanel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(18, 24, 12, 24);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        buttons.setOpaque(false);

        JButton howToPlay = createPrimaryButton("How to play", buttonFont);
        howToPlay.addActionListener(e -> showHowToPlay());

        JButton saveSettings = createPrimaryButton("Save settings", buttonFont);
        saveSettings.addActionListener(e -> saveSettings());

        resumeButton = createPrimaryButton("Resume game", buttonFont);
        resumeButton.addActionListener(e -> resumeGame());
        resumeButton.setEnabled(saveManager.hasSave());

        JButton newGame = createPrimaryButton("New game", buttonFont);
        newGame.addActionListener(e -> launchGame(Optional.empty()));

        quitButton = createPrimaryButton("Quit", buttonFont);
        quitButton.addActionListener(e -> closeLauncher());

        buttons.add(howToPlay);
        buttons.add(saveSettings);
        buttons.add(resumeButton);
        buttons.add(newGame);
        buttons.add(quitButton);

        content.add(buttons, gbc);

        MenuPanel wrapper = new MenuPanel(content);
        wrapper.setPreferredSize(new Dimension(1280, 720));
        return wrapper;
    }

    private void saveSettings() {
        updateSettingsFromUi();
        try {
            settingsPersistence.save(settings);
            JOptionPane.showMessageDialog(frame, "Settings saved.", "Launcher", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Unable to save settings: " + ex.getMessage(),
                    "Launcher", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showHowToPlay() {
        updateSettingsFromUi();
        ControlsProfile profile = settings.mutableControls();
        String message = "Explore the Ember Caverns, unlock sealed doors, and defeat guardians to recover " +
                "lost relics.\n\n" +
                String.format("Move: %s/%s/%s/%s\nShoot: %s\nReroll obstacles: %s\nPause & access menu: %s",
                        key(profile, ControlAction.MOVE_UP),
                        key(profile, ControlAction.MOVE_DOWN),
                        key(profile, ControlAction.MOVE_LEFT),
                        key(profile, ControlAction.MOVE_RIGHT),
                        key(profile, ControlAction.SHOOT),
                        key(profile, ControlAction.REROLL),
                        key(profile, ControlAction.PAUSE));
        JOptionPane.showMessageDialog(frame, message, "How to play", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resumeGame() {
        Optional<DungeonRoomsSnapshot> snapshot = saveManager.load();
        if (snapshot.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No saved game was found.", "Resume", JOptionPane.WARNING_MESSAGE);
            resumeButton.setEnabled(false);
            return;
        }
        launchGame(snapshot);
    }

    private void launchGame(Optional<DungeonRoomsSnapshot> snapshot) {
        updateSettingsFromUi();
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
            resumeButton.setEnabled(saveManager.hasSave());
            showMenuCard();
        });

        if (gameWrapper != null) {
            cardPanel.remove(gameWrapper);
            gameWrapper = null;
        }

        DungeonRoomsSnapshot data = snapshot.orElse(null);
        DungeonRooms panel = new DungeonRooms(launchSettings, controlsCopy, bundle, saver, exit, data,
                new SwingBossBattleHost());
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

    private void enableFullScreen() {
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
        if (resolutionBox != null) {
            resolutionBox.setSelectedItem(findResolution(settings.resolution()));
        }
        if (refreshBox != null) {
            refreshBox.setSelectedItem(findRefreshOption(settings.refreshRate()));
        }
        if (languageBox != null) {
            languageBox.setSelectedItem(findLanguage(settings.language()));
        }
        if (resumeButton != null) {
            resumeButton.setEnabled(saveManager.hasSave());
        }
        cardLayout.show(cardPanel, CARD_MENU);
        cardPanel.revalidate();
        cardPanel.repaint();
        if (menuCard != null) {
            menuCard.requestFocusInWindow();
        }
    }

    private void closeLauncher() {
        if (frame != null) {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        }
    }

    private void updateSettingsFromUi() {
        DimensionOption res = (DimensionOption) resolutionBox.getSelectedItem();
        if (res != null) {
            settings.setResolution(res.dimension());
        }
        Integer refresh = (Integer) refreshBox.getSelectedItem();
        if (refresh != null) {
            settings.setRefreshRate(refresh);
        }
        LocaleOption localeOption = (LocaleOption) languageBox.getSelectedItem();
        if (localeOption != null) {
            settings.setLanguage(localeOption.locale());
        }
        // Controls buttons already update settings in promptRebind
    }

    private Integer findRefreshOption(int refreshRate) {
        for (Integer option : REFRESH_OPTIONS) {
            if (option == refreshRate) {
                return option;
            }
        }
        return REFRESH_OPTIONS[Math.min(REFRESH_OPTIONS.length - 1, 2)]; // default to 60 Hz when absent
    }

    private void promptRebind(ControlAction action) {
        JDialog dialog = new JDialog(frame, "Rebind " + action.name(), true);
        JLabel instructions = new JLabel("Press a key for " + action.name(), JLabel.CENTER);
        instructions.setFocusable(true);
        instructions.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                ControlsProfile profile = settings.mutableControls();
                profile.rebind(action, e.getKeyCode());
                settings.setControls(profile);
                controlButtons.get(action).setText(controlLabel(action));
                dialog.dispose();
            }
        });
        dialog.add(instructions);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(260, 120);
        dialog.setLocationRelativeTo(frame);
        SwingUtilities.invokeLater(instructions::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private DimensionOption findResolution(Dimension current) {
        for (DimensionOption option : RESOLUTIONS) {
            if (option.dimension().equals(current)) {
                return option;
            }
        }
        return RESOLUTIONS[0];
    }

    private LocaleOption findLanguage(Locale locale) {
        for (LocaleOption option : LANGUAGES) {
            if (option.locale().getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                return option;
            }
        }
        return LANGUAGES[0];
    }

    private String controlLabel(ControlAction action) {
        Integer code = settings.mutableControls().view().get(action);
        return code == null ? "Unbound" : KeyEvent.getKeyText(code);
    }

    private String key(ControlsProfile profile, ControlAction action) {
        return KeyEvent.getKeyText(profile.view().get(action));
    }

    private <T> JComboBox<T> styleComboBox(JComboBox<T> combo, Font font) {
        combo.setFont(font);
        combo.setForeground(new Color(242, 246, 255));
        combo.setBackground(new Color(26, 44, 58));
        combo.setOpaque(true);
        combo.setPreferredSize(new Dimension(280, 44));
        combo.setMaximumRowCount(10);
        combo.setFocusable(false);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 84, 104)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setFont(font);
                if (isSelected) {
                    label.setBackground(new Color(244, 223, 138));
                    label.setForeground(new Color(24, 28, 36));
                } else {
                    label.setBackground(new Color(26, 44, 58));
                    label.setForeground(new Color(230, 238, 252));
                }
                return label;
            }
        });
        return combo;
    }

    private JButton createPrimaryButton(String text, Font font) {
        JButton button = new JButton(text);
        button.setFont(font);
        button.setForeground(new Color(28, 32, 40));
        button.setBackground(new Color(244, 223, 138));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(12, 22, 12, 22));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createControlButton(String label, Font font) {
        JButton button = new JButton(label);
        button.setFont(font);
        button.setForeground(new Color(236, 240, 248));
        button.setBackground(new Color(34, 50, 66));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    private final class SwingBossBattleHost implements DungeonRooms.BossBattleHost {
        @Override
        public void runBossBattle(BossBattlePanel.BossKind kind, Consumer<BossBattlePanel.Outcome> outcomeHandler) {
            BossBattlePanel panel = BossBattlePanel.create(kind, outcome -> {
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
    }

    private static final class MenuPanel extends JPanel {
        private MenuPanel(JPanel content) {
            setOpaque(false);
            setLayout(new GridBagLayout());
            setFocusable(true);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.anchor = GridBagConstraints.CENTER;
            add(content, gbc);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(12, 18, 28),
                        getWidth(), getHeight(), new Color(48, 68, 92));
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class GlassPanel extends JPanel {
        private GlassPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(8, 16, 24, 200));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 36, 36);
                g2.setColor(new Color(120, 170, 210, 120));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 32, 32);
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

        @Override
        public Dimension dimension() {
            return new Dimension(dimension);
        }
    }

    private record LocaleOption(String label, Locale locale) {
        @Override
        public String toString() {
            return label;
        }
    }
}
