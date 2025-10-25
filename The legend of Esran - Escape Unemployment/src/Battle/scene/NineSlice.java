package battle.scene;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

public class NineSlice {
    private final BufferedImage lt,t,rt,l,c,r,lb,b,rb;
    private final int e;

    public static BufferedImage loadWhole(String path){
        try { return ImageIO.read(NineSlice.class.getResource(path)); }
        catch(Exception e){ throw new RuntimeException("Missing UI borders: "+path, e); }
    }

    /** Build 9-slice from a full image; edge is corner thickness in px. */
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
