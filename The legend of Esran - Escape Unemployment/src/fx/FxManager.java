package fx;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Keeps and updates/renders all active effects. */
public class FxManager {
    private final List<Effect> list = new ArrayList<>();

    public void add(Effect e) { list.add(e); }

    public void update(double dt) {
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            e.update(dt);
            if (e.dead()) { list.remove(i); i--; }
        }
    }

    public void render(Graphics2D g, int camX, int camY) {
        for (var e : list) e.render(g, camX, camY);
    }
}
