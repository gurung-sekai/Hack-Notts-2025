package launcher;

import World.DungeonRooms;
import World.DungeonRoomsSnapshot;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Swing launcher that lets players adjust runtime settings before starting the game.
 */
public final class GameLauncher {
    private static final DimensionOption[] RESOLUTIONS = {
            new DimensionOption("756 x 468 (original scale)", new Dimension(756, 468)),
            new DimensionOption("1008 x 624 (large)", new Dimension(1008, 624)),
            new DimensionOption("1260 x 780 (extra large)", new Dimension(1260, 780))
    };

    private static final Integer[] REFRESH_OPTIONS = {30, 45, 60, 75, 90, 120};

    private static final LocaleOption[] LANGUAGES = {
        new LocaleOption("English", Locale.UK),
        new LocaleOption("Cymraeg", new Locale("cy", "GB"))
    };

    private final Path storageDir;
    private final SettingsPersistence settingsPersistence;
    private final SaveManager saveManager;
    private GameSettings settings;

    private JFrame frame;
    private JComboBox<DimensionOption> resolutionBox;
    private JComboBox<Integer> refreshBox;
    private JComboBox<LocaleOption> languageBox;
    private final Map<ControlAction, JButton> controlButtons = new EnumMap<>(ControlAction.class);
    private JButton resumeButton;

    public static void main(String[] args) {
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
    }

    private void show() {
        frame = new JFrame("Dungeon Rooms Launcher");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(buildContent());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        form.add(new JLabel("Resolution"), gbc);
        gbc.gridx = 1;
        resolutionBox = new JComboBox<>(RESOLUTIONS);
        resolutionBox.setSelectedItem(findResolution(settings.resolution()));
        form.add(resolutionBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("Refresh rate"), gbc);
        gbc.gridx = 1;
        refreshBox = new JComboBox<>(REFRESH_OPTIONS);
        refreshBox.setSelectedItem(settings.refreshRate());
        form.add(refreshBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("Language"), gbc);
        gbc.gridx = 1;
        languageBox = new JComboBox<>(LANGUAGES);
        languageBox.setSelectedItem(findLanguage(settings.language()));
        form.add(languageBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        form.add(new JLabel("Controls"), gbc);

        gbc.gridy++;
        JPanel controlsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints cg = new GridBagConstraints();
        cg.insets = new Insets(2, 2, 2, 2);
        cg.anchor = GridBagConstraints.WEST;
        cg.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        for (ControlAction action : ControlAction.values()) {
            cg.gridx = 0;
            cg.gridy = row;
            controlsPanel.add(new JLabel(action.name().replace('_', ' ')), cg);
            cg.gridx = 1;
            JButton button = new JButton(controlLabel(action));
            button.addActionListener(e -> promptRebind(action));
            controlButtons.put(action, button);
            controlsPanel.add(button, cg);
            row++;
        }
        form.add(controlsPanel, gbc);

        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveSettings = new JButton("Save settings");
        saveSettings.addActionListener(e -> saveSettings());
        JButton howToPlay = new JButton("How to play");
        howToPlay.addActionListener(e -> showHowToPlay());
        JButton newGame = new JButton("New game");
        newGame.addActionListener(e -> launchGame(Optional.empty()));
        resumeButton = new JButton("Resume game");
        resumeButton.addActionListener(e -> resumeGame());
        resumeButton.setEnabled(saveManager.hasSave());

        buttons.add(howToPlay);
        buttons.add(saveSettings);
        buttons.add(resumeButton);
        buttons.add(newGame);

        root.add(buttons, BorderLayout.SOUTH);
        return root;
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

        JFrame gameFrame = new JFrame("Dungeon Rooms");
        Consumer<DungeonRoomsSnapshot> saver = snap -> {
            try {
                saveManager.save(snap);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };
        Runnable exit = () -> SwingUtilities.invokeLater(() -> {
            panelShutdown(gameFrame);
            gameFrame.dispose();
            frame.setVisible(true);
            resumeButton.setEnabled(saveManager.hasSave());
        });
        DungeonRoomsSnapshot data = snapshot.orElse(null);
        DungeonRooms panel = new DungeonRooms(launchSettings, controlsCopy, bundle, saver, exit, data);
        gameFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        gameFrame.setContentPane(panel);
        gameFrame.pack();
        gameFrame.setLocationRelativeTo(frame);
        gameFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panelShutdown(gameFrame);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                frame.setVisible(true);
                resumeButton.setEnabled(saveManager.hasSave());
            }
        });
        frame.setVisible(false);
        gameFrame.setVisible(true);
        panel.requestFocusInWindow();
    }

    private void panelShutdown(JFrame gameFrame) {
        if (gameFrame.getContentPane() instanceof DungeonRooms rooms) {
            rooms.shutdown();
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
