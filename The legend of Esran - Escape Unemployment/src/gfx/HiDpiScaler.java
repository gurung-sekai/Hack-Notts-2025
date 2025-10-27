package gfx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility that rescales sprites with multi-step sampling and a light sharpen pass so that
 * enlarged boss sprites remain crisp even on high resolution displays.
 */
public final class HiDpiScaler {

    private static final Kernel SHARPEN_KERNEL = new Kernel(3, 3, new float[]{
            0f, -0.05f, 0f,
            -0.05f, 1.3f, -0.05f,
            0f, -0.05f, 0f
    });
    private static final ConvolveOp SHARPEN = new ConvolveOp(SHARPEN_KERNEL, ConvolveOp.EDGE_NO_OP, null);

    private static final Map<BufferedImage, Map<Long, BufferedImage>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HiDpiScaler() {
    }

    public static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight) {
        if (source == null || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }
        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }

        Map<Long, BufferedImage> perImageCache;
        synchronized (CACHE) {
            perImageCache = CACHE.computeIfAbsent(source, key -> createBoundedCache());
        }
        long cacheKey = (((long) targetWidth) << 32) ^ (targetHeight & 0xffffffffL);
        BufferedImage cached = perImageCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BufferedImage scaled = multiStepScale(source, targetWidth, targetHeight);
        perImageCache.put(cacheKey, scaled);
        return scaled;
    }

    private static Map<Long, BufferedImage> createBoundedCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
                return size() > 12;
            }
        });
    }

    private static BufferedImage multiStepScale(BufferedImage source, int targetWidth, int targetHeight) {
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        BufferedImage current = source;

        while (currentWidth != targetWidth || currentHeight != targetHeight) {
            int nextWidth = stepTowards(currentWidth, targetWidth);
            int nextHeight = stepTowards(currentHeight, targetHeight);
            BufferedImage tmp = new BufferedImage(Math.max(1, nextWidth), Math.max(1, nextHeight), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            } finally {
                g2.dispose();
            }
            current = tmp;
            currentWidth = nextWidth;
            currentHeight = nextHeight;
        }

        if (targetWidth < source.getWidth() || targetHeight < source.getHeight()) {
            BufferedImage sharpened = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            SHARPEN.filter(current, sharpened);
            current = sharpened;
        }

        return current;
    }

    private static int stepTowards(int current, int target) {
        if (current == target) {
            return current;
        }
        if (target < current) {
            int next = current / 2;
            if (next < target) {
                next = target;
            }
            if (next == 0) {
                next = target;
            }
            return next;
        } else {
            int next = current * 2;
            if (next > target) {
                next = target;
            }
            if (next == current) {
                next = target;
            }
            return next;
        }
    }
}
