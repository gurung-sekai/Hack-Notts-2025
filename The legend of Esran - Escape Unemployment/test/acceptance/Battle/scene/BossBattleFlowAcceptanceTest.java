package acceptance.battle.scene;

import Battle.scene.BossBattlePanel;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.FutureTask;

public final class BossBattleFlowAcceptanceTest {

    public static void main(String[] args) throws Exception {
        FutureTask<BossBattlePanel> panelTask = new FutureTask<>(
                () -> BossBattlePanel.create(BossBattlePanel.BossKind.BIG_ZOMBIE, outcome -> { })
        );
        SwingUtilities.invokeAndWait(panelTask);
        BossBattlePanel panel = panelTask.get();

        try {
            SwingUtilities.invokeAndWait(() -> panel.setSize(960, 540));

            Method chooseCommand = BossBattlePanel.class.getDeclaredMethod("chooseCommand");
            chooseCommand.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    chooseCommand.invoke(panel);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            });

            Thread.sleep(1500L);

            Field phaseField = BossBattlePanel.class.getDeclaredField("phase");
            phaseField.setAccessible(true);
            FutureTask<Object> phaseTask = new FutureTask<>(() -> phaseField.get(panel));
            SwingUtilities.invokeAndWait(phaseTask);
            Object phase = phaseTask.get();

            if (phase == null) {
                throw new AssertionError("Phase should never be null");
            }
            String phaseName = ((Enum<?>) phase).name();
            boolean acceptable = "PLAYER_SELECT".equals(phaseName)
                    || "WIN".equals(phaseName)
                    || "GAME_OVER".equals(phaseName);
            if (!acceptable) {
                throw new AssertionError("Unexpected phase after resolution: " + phaseName);
            }

            System.out.println("BossBattleFlowAcceptanceTest passed");
        } finally {
            SwingUtilities.invokeAndWait(panel::shutdown);
        }
    }
}
