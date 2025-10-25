package Battle.scene;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class NineSlice {
    private final BufferedImage lt,t,rt,l,c,r,lb,b,rb;
    private final int e;

    /** Try to load a UI borders image from a few likely places; fallback to a generated skin. */
    public static BufferedImage loadWhole(String preferredPath){
        BufferedImage img = tryLoad(preferredPath);
        if (img == null) {
            // Common alternatives (fix possible "Pixel" vs "Pxiel", with/without /resources)
            String[] candidates = {
                    "/UI/Pixel Art UI borders.png",
                    "/UI/Pxiel Art UI borders.png",
                    "/resources/UI/Pixel Art UI borders.png",
                    "/resources/UI/Pxiel Art UI borders.png"
            };
            for (String p : candidates) {
                img = tryLoad(p);
                if (img != null) break;
            }
        }
        if (img == null) {
            img = generateDefaultSkin(24, 24);
        }
        return img;
    }

    private static BufferedImage tryLoad(String path){
        if (path == null) return null;
        InputStream in = null;
        try {
            in = NineSlice.class.getResourceAsStream(path);
            if (in == null) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) {
                    in = cl.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
                }
            }
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException ignored) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static BufferedImage generateDefaultSkin(int w, int h){
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(28, 32, 38)); g.fillRect(0,0,w,h);
        g.setColor(new Color(46, 54, 66)); g.fillRect(2,2,w-4,h-4);
        g.setColor(new Color(240, 220, 120)); g.drawRect(1,1,w-3,h-3);
        g.dispose();
        return img;
    }

    public NineSlice(BufferedImage img, int edge){
        int W = img.getWidth(), H = img.getHeight();
        int E = Math.min(edge, Math.min(W,H)/2);
        lt = img.getSubimage(0,0,E,E);
        t  = img.getSubimage(E,0,W-2*E,E);
        rt = img.getSubimage(W-E,0,E,E);
        l  = img.getSubimage(0,E,E,H-2*E);
        c  = img.getSubimage(E,E,W-2*E,H-2*E);
        r  = img.getSubimage(W-E,E,E,H-2*E);
        lb = img.getSubimage(0,H-E,E,E);
        b  = img.getSubimage(E,H-E,W-2*E,E);
        rb = img.getSubimage(W-E,H-E,E,E);
        e = E;
    }

    public void draw(Graphics2D g, int x, int y, int w, int h){
        int mw = Math.max(0,w-2*e), mh = Math.max(0,h-2*e);
        g.drawImage(lt,x,y,e,e,null);
        g.drawImage(rt,x+e+mw,y,e,e,null);
        g.drawImage(lb,x,y+e+mh,e,e,null);
        g.drawImage(rb,x+e+mw,y+e+mh,e,e,null);
        g.drawImage(t, x+e,y,mw,e,null);
        g.drawImage(b, x+e,y+e+mh,mw,e,null);
        g.drawImage(l, x,y+e,e,mh,null);
        g.drawImage(r, x+e,y+e,e,mh,null);
        g.drawImage(c, x+e,y+e,mw,mh,null);
    }
}
