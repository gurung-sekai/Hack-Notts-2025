package World.gfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Procedurally generated textures so the game avoids bundling external artwork.
 */
public final class DungeonTextures {
    private final BufferedImage[] floorCells;
    private final BufferedImage[] wallCells;
    private final BufferedImage doorFloor;

    private DungeonTextures(BufferedImage[] floorCells, BufferedImage[] wallCells, BufferedImage doorFloor) {
        this.floorCells = floorCells;
        this.wallCells = wallCells;
        this.doorFloor = doorFloor;
    }

    public static DungeonTextures procedural() {
        BufferedImage[] floors = new BufferedImage[4];
        BufferedImage[] walls = new BufferedImage[3];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = makeTile(new Color(18 + i * 4, 64 + i * 3, 78 + i * 4), new Color(12, 42, 56));
        }
        for (int i = 0; i < walls.length; i++) {
            walls[i] = makeTile(new Color(46 + i * 6, 126 + i * 5, 146 + i * 4), new Color(28, 82, 96));
        }
        BufferedImage door = makeTile(new Color(38, 80, 92), new Color(24, 60, 70));
        Graphics2D g = door.createGraphics();
        try {
            g.setColor(new Color(210, 178, 90));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(4, 4, door.getWidth() - 8, door.getHeight() - 8);
        } finally {
            g.dispose();
        }
        return new DungeonTextures(floors, walls, door);
    }

    public boolean isReady() {
        return true;
    }

    public int floorVariants() {
        return floorCells.length;
    }

    public int wallVariants() {
        return wallCells.length;
    }

    public BufferedImage floorVariant(int index) {
        return floorCells[Math.floorMod(index, floorCells.length)];
    }

    public BufferedImage wallVariant(int index) {
        return wallCells[Math.floorMod(index, wallCells.length)];
    }

    public BufferedImage doorFloor() {
        return doorFloor;
    }

    private static BufferedImage makeTile(Color base, Color accent) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(base);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(accent);
            g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
            g.drawLine(0, img.getHeight() / 2, img.getWidth(), img.getHeight() / 2);
            g.drawLine(img.getWidth() / 2, 0, img.getWidth() / 2, img.getHeight());
        } finally {
            g.dispose();
        }
        return img;
    }
}
