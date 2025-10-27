package com.hacknotts.extractor.loader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class SpriteSheetLoader {

    public SpriteSheet load(Path path) throws IOException {
        BufferedImage input = ImageIO.read(path.toFile());
        if (input == null) {
            throw new IOException("Unable to read image " + path);
        }
        BufferedImage converted = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = converted.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(input, 0, 0, null);
        g2d.dispose();
        return new SpriteSheet(path, converted);
    }
}
