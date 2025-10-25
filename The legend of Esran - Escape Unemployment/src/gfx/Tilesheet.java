package gfx;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Tilesheet {
    public final BufferedImage sheet;
    public final int tileW, tileH;
    public final int cols, rows;

    public Tilesheet(String path, int tileW, int tileH) {
        try {
            this.sheet = ImageIO.read(Tilesheet.class.getResource(path));
        } catch (Exception e) {
            throw new RuntimeException("Cannot load tilesheet: " + path, e);
        }
        this.tileW = tileW;
        this.tileH = tileH;
        this.cols = sheet.getWidth() / tileW;
        this.rows = sheet.getHeight() / tileH;
    }

    public void draw(Graphics2D g, int col, int row, int x, int y, int outSize) {
        int sx = col * tileW;
        int sy = row * tileH;
        g.drawImage(sheet, x, y, x + outSize, y + outSize, sx, sy, sx + tileW, sy + tileH, null);
    }
}
