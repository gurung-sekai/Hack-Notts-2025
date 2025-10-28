package unit.world;

import World.UndertaleText;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class UndertaleTextAvailabilityTest {

    private UndertaleTextAvailabilityTest() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("World.UndertaleText");
        if (type == null) {
            throw new AssertionError("World.UndertaleText failed to load");
        }

        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            UndertaleText.apply(graphics, 12f);
        } finally {
            graphics.dispose();
        }

        System.out.println("UndertaleTextAvailabilityTest passed");
    }
}
