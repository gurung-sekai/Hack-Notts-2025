package com.hacknotts.extractor.loader;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public record SpriteSheet(Path path, BufferedImage image) {
}
