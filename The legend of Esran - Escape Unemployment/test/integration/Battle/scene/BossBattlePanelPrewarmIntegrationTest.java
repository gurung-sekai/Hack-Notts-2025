package integration.battle.scene;

import Battle.scene.BossBattlePanel;
import gfx.HiDpiScaler;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.FutureTask;

public final class BossBattlePanelPrewarmIntegrationTest {

    public static void main(String[] args) throws Exception {
        FutureTask<BossBattlePanel> panelTask = new FutureTask<>(
                () -> BossBattlePanel.create(BossBattlePanel.BossKind.BIG_ZOMBIE, outcome -> { })
        );
        SwingUtilities.invokeAndWait(panelTask);
        BossBattlePanel panel = panelTask.get();

        try {
            SwingUtilities.invokeAndWait(() -> panel.setSize(960, 540));
            BufferedImage canvas = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                Graphics2D g2 = canvas.createGraphics();
                try {
                    panel.paint(g2);
                } finally {
                    g2.dispose();
                }
            });

            Field cacheField = HiDpiScaler.class.getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<?, ?> cache = (Map<?, ?>) cacheField.get(null);
            int cacheSize;
            synchronized (cache) {
                cacheSize = cache.size();
            }
            if (cacheSize <= 0) {
                throw new AssertionError("Expected HiDPI cache to contain entries after paint");
            }
            System.out.println("BossBattlePanelPrewarmIntegrationTest passed");
        } finally {
            SwingUtilities.invokeAndWait(panel::shutdown);
        }
    }
}
