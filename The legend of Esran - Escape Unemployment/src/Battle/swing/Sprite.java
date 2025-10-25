package Battle.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Sprite {
    private final Image[] frames;
    private final int w, h;

    public Sprite(Image[] frames, int w, int h) {
        this.frames = frames;
        this.w = w; this.h = h;
    }

    public void draw(Graphics2D g, int x, int y) {
        if (frames != null && frames.length > 0) {
            g.drawImage(frames[(int)(System.currentTimeMillis()/120)%frames.length], x, y, w, h, null);
        } else {
            // fallback
            g.setColor(new Color(255,255,255,40));
            g.fillRect(x, y, w, h);
            g.setColor(Color.WHITE);
            g.drawRect(x, y, w, h);
        }
    }

    public static Sprite placeholder(Color c, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c); g.fillRoundRect(0,0,w,h,16,16);
        g.setColor(Color.BLACK); g.drawRoundRect(0,0,w,h,16,16);
        g.dispose();
        return new Sprite(new Image[]{img}, w, h);
        // Later: load from PNG/gif and pass frames[] here.
    }
}
