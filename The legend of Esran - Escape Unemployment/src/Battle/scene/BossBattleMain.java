package Battle.scene;

import javax.swing.*;

public class BossBattleMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Boss Battle");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            BossBattlePanel panel = BossBattlePanel.create(
                    BossBattlePanel.BossKind.OGRE_WARLORD,
                    outcome -> {
                        System.out.println("Battle ended with outcome: " + outcome + " — return to world here.");
                        f.dispose();
                    });

            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
