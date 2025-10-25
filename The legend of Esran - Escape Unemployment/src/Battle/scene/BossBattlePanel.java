package battle.scene;

import fx.FXLibrary;
import fx.FxManager;
import gfx.AnimatedSprite;
import gfx.SpriteFactory;
import World.gfx.Tilesheet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Turn-based boss battle scene (original, non-derivative style).
 * - uses your existing sprites, FX, UI border, font, tiles
 * - no Run option; loss -> Game Over, then callback
 * - hero drawn in back-facing style (mirrored & lowered)
 * - arena prefers "Set 1.png" tiles; falls back to atlas_floor
 *
 * Hook from world:
 *   var panel = BossBattlePanel.create(BossKind.OGRE_WARLORD, () -> { /* return to world */ });
        *   frame.setContentPane(panel); frame.revalidate(); panel.requestFocusInWindow();
 */
public class BossBattlePanel extends JPanel {

    // -------- choose a boss --------
    public enum BossKind { BIG_ZOMBIE, OGRE_WARLORD, SKELETON_LORD, PUMPKIN_KING, IMP_OVERLORD, WIZARD_ARCHON }

    public static BossBattlePanel create(BossKind kind, Runnable onEnd){
        AnimatedSprite heroSp = SpriteFactory.knightMale(72);
        heroSp.setState(AnimatedSprite.State.IDLE);

        AnimatedSprite bossSp;
        String bossName; String aff;
        switch (kind) {
            case OGRE_WARLORD -> { bossSp = SpriteFactory.ogre(112); bossName="Ogre Warlord"; aff="STONE"; }
            case SKELETON_LORD -> { bossSp = SpriteFactory.skeleton(96); bossName="Skeleton Lord"; aff="SHADOW"; }
            case PUMPKIN_KING -> { bossSp = SpriteFactory.pumpkinDude(96); bossName="Pumpkin King"; aff="VERDANT"; }
            case IMP_OVERLORD -> { bossSp = SpriteFactory.imp(80); bossName="Imp Overlord"; aff="FLAME"; }
            case WIZARD_ARCHON -> { bossSp = SpriteFactory.wizardMale(100); bossName="Archon"; aff="ARCANE"; }
            default -> { bossSp = SpriteFactory.bigZombie(120); bossName="Dread Husk"; aff="SHADOW"; }
        }
        bossSp.setState(AnimatedSprite.State.IDLE);

        FighterView hero = new FighterView("Hero", "STONE", 120, 30, heroSp);
        FighterView boss = new FighterView(bossName, aff, 180, 40, bossSp);
        return new BossBattlePanel(hero, boss, onEnd);
    }

    // -------- fighter view (lightweight) --------
    public static class FighterView {
        public final String name, affinity;
        public int hp, hpMax;
        public int mp, mpMax;
        public AnimatedSprite sprite;
        public FighterView(String name, String affinity, int hp, int mp, AnimatedSprite sprite) {
            this.name = name; this.affinity = affinity;
            this.hpMax = this.hp = hp; this.mpMax = this.mp = mp; this.sprite = sprite;
        }
    }

    // -------- state machine --------
    private enum Phase { PLAYER_SELECT, PLAYER_ANIM, ENEMY_ANIM, WIN, LOSE, GAME_OVER }
    private Phase phase = Phase.PLAYER_SELECT;

    private final FighterView hero;
    private final FighterView boss;
    private final Runnable onEnd;

    private final FxManager fx = new FxManager();
    private long lastNs=0;
    private double shake=0, shakeTime=0;

    // arena tiles: prefer Set 1.png; else atlas_floor
    private Tilesheet arenaSheet;
    private final int TILE = 32;
    private final int ARENA_W_TILES = 22, ARENA_H_TILES = 10;

    // UI
    private final NineSlice panel;
    private final Font pixel14, pixel18;

    // commands (no Run)
    private final String[] cmds = {"Fight", "Guard", "Item"};
    private int cmdIndex = 0;

    // damage floaters
    private static class Dmg {
        double x,y,t,life=0.9; String text; Color col;
        void upd(double dt){ t+=dt; }
        boolean dead(){ return t>=life; }
        void draw(Graphics2D g, Font f){
            double a = Math.max(0, 1 - t/life);
            int dy = (int)(-10 - 18*t);
            Composite prev = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,(float)a));
            g.setFont(f);
            g.setColor(Color.BLACK);
            g.drawString(text,(int)x+1,(int)y+dy+1);
            g.setColor(col);
            g.drawString(text,(int)x,(int)y+dy);
            g.setComposite(prev);
        }
    }
    private final List<Dmg> dmgs = new ArrayList<>();

    public BossBattlePanel(FighterView hero, FighterView boss, Runnable onEnd){
        this.hero = hero; this.boss = boss; this.onEnd = onEnd;
        setPreferredSize(new Dimension(800, 480));
        setBackground(Color.BLACK);

        // tilesheet: try Set 1.png, fallback to floor atlas
        try {
            arenaSheet = new Tilesheet("/resources/tiles/Set 1.png", 16, 16);
        } catch (Throwable t) {
            arenaSheet = new Tilesheet("/resources/tiles/atlas_floor-16x16.png", 16, 16);
        }

        // UI border (your exact file name)
        BufferedImage borders = NineSlice.loadWhole("/resources/UI/Pxiel Art UI borders.png");
        panel = new NineSlice(borders, 6);

        // font
        Font pf;
        try{
            pf = Font.createFont(Font.TRUETYPE_FONT,
                    BossBattlePanel.class.getResourceAsStream("/resources/fonts/ThaleahFat.ttf"));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(pf);
        }catch(Exception e){
            pf = new Font("Monospaced", Font.BOLD, 12);
        }
        pixel14 = pf.deriveFont(Font.PLAIN, 14f);
        pixel18 = pf.deriveFont(Font.PLAIN, 18f);

        // input
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (phase == Phase.PLAYER_SELECT) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> { cmdIndex = (cmdIndex+cmds.length-1)%cmds.length; repaint(); }
                        case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> { cmdIndex = (cmdIndex+1)%cmds.length; repaint(); }
                        case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> chooseCommand();
                    }
                } else if (phase == Phase.GAME_OVER) {
                    // any key after game over -> callback
                    if (onEnd != null) onEnd.run();
                }
            }
        });

        // loop
        new Timer(1000/60, e -> tick()).start();
    }

    // ---------- update ----------
    private void tick(){
        long now = System.nanoTime();
        if (lastNs == 0) lastNs = now;
        double dt = (now - lastNs)/1e9; lastNs = now;

        hero.sprite.update(dt);
        boss.sprite.update(dt);
        fx.update(dt);
        for (int i=0;i<dmgs.size();i++){ dmgs.get(i).upd(dt); if (dmgs.get(i).dead()) { dmgs.remove(i); i--; } }
        if (shakeTime > 0) { shakeTime -= dt; shake = 3; } else { shake = 0; }

        repaint();
    }

    // ---------- actions ----------
    private void chooseCommand(){
        switch (cmdIndex){
            case 0 -> playerAttack();
            case 1 -> guard();
            case 2 -> itemHeal();
        }
    }

    private void playerAttack(){
        phase = Phase.PLAYER_ANIM;
        double bx = getWidth() - 240, by = 180;
        // pick an FX based on boss affinity vibe
        fx.add(FXLibrary.thunderStrike(bx, by));
        int dmg = 18 + (int)(Math.random()*10);
        boss.hp = Math.max(0, boss.hp - dmg);
        dmgs.add(newDmg(bx+24, by-12,  String.valueOf(dmg), new Color(0xE4504D)));
        shakeTime = 0.20;

        new javax.swing.Timer(420, e -> enemyTurn()).start();
    }

    private void guard(){
        phase = Phase.PLAYER_ANIM;
        double hx = 180, hy = 220;
        fx.add(FXLibrary.shieldBlockScreen(hx, hy));
        dmgs.add(newDmg(hx, hy-6, "Guard", new Color(0xF2C14E)));
        new javax.swing.Timer(300, e -> enemyTurn()).start();
    }

    private void itemHeal(){
        phase = Phase.PLAYER_ANIM;
        double hx = 180, hy = 220;
        fx.add(FXLibrary.smokeLarge(hx, hy));
        int heal = 22;
        hero.hp = Math.min(hero.hpMax, hero.hp + heal);
        dmgs.add(newDmg(hx, hy-24, "+"+heal, new Color(0x5DA9E9)));
        new javax.swing.Timer(350, e -> enemyTurn()).start();
    }

    private void enemyTurn(){
        if (boss.hp <= 0) { phase = Phase.WIN; endBattle(true); return; }

        phase = Phase.ENEMY_ANIM;

        double hx = 180, hy = 220;
        int move = (int)(Math.random()*3);
        int dmg;
        switch (move){
            default -> { fx.add(FXLibrary.fireBreath(hx-60, hy-10, +1, 0)); dmg = 14 + (int)(Math.random()*8); }
            case 1 -> { fx.add(FXLibrary.thunderSplash(hx, hy)); dmg = 16 + (int)(Math.random()*10); }
            case 2 -> { fx.add(FXLibrary.smokeSmall(hx, hy)); dmg = 10 + (int)(Math.random()*6); }
        }
        hero.hp = Math.max(0, hero.hp - dmg);
        dmgs.add(newDmg(hx, hy-18, "-"+dmg, new Color(0xFF6B6B)));
        shakeTime = 0.20;

        new javax.swing.Timer(460, e -> {
            if (hero.hp <= 0) { phase = Phase.LOSE; gameOver(); }
            else phase = Phase.PLAYER_SELECT;
        }).start();
    }

    private void endBattle(boolean playerWon){
        if (playerWon) {
            // brief banner then return to world
            new javax.swing.Timer(600, e -> { if (onEnd != null) onEnd.run(); }).start();
        } else {
            gameOver();
        }
    }

    private void gameOver(){
        phase = Phase.GAME_OVER;
        // wait for any key; onKey handler will call onEnd()
    }

    private static Dmg newDmg(double x,double y,String s,Color c){ Dmg d=new Dmg(); d.x=x; d.y=y; d.text=s; d.col=c; return d; }

    // ---------- paint ----------
    @Override protected void paintComponent(Graphics g0){
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        if (shake > 0){
            g.translate((int)((Math.random()-0.5)*shake), (int)((Math.random()-0.5)*shake));
        }

        drawArena(g);

        // hero (back-ish): mirror horizontally and place lower
        drawSpriteMirrored(g, hero.sprite.frame(), 140, 230, hero.sprite.w(), hero.sprite.h(), true);

        // boss (front)
        var bf = boss.sprite.frame();
        if (bf != null) g.drawImage(bf, getWidth()-260, 120, boss.sprite.w(), boss.sprite.h(), null);

        drawHUD(g);

        // damage numbers last
        for (var d : dmgs) d.draw(g, pixel18);

        if (phase == Phase.GAME_OVER) drawGameOver(g);
    }

    private void drawArena(Graphics2D g){
        int W = getWidth(), H = getHeight();

        // subdued sky
        var grad = new GradientPaint(0,0,new Color(18,22,28), 0,H/2,new Color(14,18,22));
        Paint old = g.getPaint(); g.setPaint(grad); g.fillRect(0,0,W,H); g.setPaint(old);

        // tiled band of ground with Set / floor atlas
        int startY = H - ARENA_H_TILES*TILE + 28;
        for (int y=0;y<ARENA_H_TILES;y++){
            for (int x=0;x<ARENA_W_TILES;x++){
                int idx = Math.floorMod((x*73856093 ^ y*19349663), Math.max(1, arenaSheet.cols*arenaSheet.rows));
                int c = idx % arenaSheet.cols, r = idx / arenaSheet.cols;
                arenaSheet.draw(g, c, r, x*TILE, startY + y*TILE, TILE);
            }
        }

        // vignette
        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(Color.BLACK); g.fillRect(0,0,W,24);
        g.fillRect(0,H-24,W,24);
        g.setComposite(prev);
    }

    private void drawHUD(Graphics2D g){
        int W = getWidth(), H = getHeight();

        // player info bottom-left
        int infoW = 300, infoH = 80, pad = 16;
        panel.draw(g, pad, H - infoH - pad, infoW, infoH);
        g.setFont(pixel14);
        label(g, hero.name, pad+20, H - infoH - pad + 24);
        label(g, "HP: "+hero.hp+"/"+hero.hpMax, pad+20, H - infoH - pad + 44);

        // boss info top-right
        panel.draw(g, W - infoW - pad, pad, infoW, infoH);
        label(g, boss.name, W - infoW - pad + 20, pad + 24);
        label(g, "HP: "+boss.hp+"/"+boss.hpMax, W - infoW - pad + 20, pad + 44);

        // command box bottom-right
        int cmdW = 420, cmdH = 110;
        panel.draw(g, W - cmdW - pad, H - cmdH - pad, cmdW, cmdH);
        g.setFont(pixel18);
        String tip = (phase==Phase.PLAYER_SELECT? "Choose an action" :
                phase==Phase.PLAYER_ANIM ? "…" :
                        phase==Phase.ENEMY_ANIM  ? "Enemy acts…" :
                                phase==Phase.WIN         ? "Victory!" :
                                        phase==Phase.LOSE        ? "Defeated…" :
                                                "GAME OVER — press any key");
        label(g, tip, W - cmdW - pad + 12, H - cmdH - pad + 22);

        if (phase == Phase.PLAYER_SELECT) {
            int x = W - cmdW - pad + 20, y = H - cmdH - pad + 52;
            for (int i=0;i<cmds.length;i++){
                boolean sel = (i==cmdIndex);
                drawOption(g, cmds[i], x + (i%2)*180, y + (i/2)*28, sel);
            }
        }
    }

    private void drawGameOver(Graphics2D g){
        int W=getWidth(), H=getHeight();
        Composite prev = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(Color.BLACK); g.fillRect(0,0,W,H);
        g.setComposite(prev);

        g.setFont(pixel18.deriveFont(24f));
        String s = "GAME OVER";
        int sw = g.getFontMetrics().stringWidth(s);
        g.setColor(Color.BLACK); g.drawString(s, (W-sw)/2+2, H/2+2);
        g.setColor(Color.WHITE); g.drawString(s, (W-sw)/2,   H/2);
        g.setFont(pixel14);
        String s2 = "Press any key";
        int sw2 = g.getFontMetrics().stringWidth(s2);
        g.setColor(Color.LIGHT_GRAY); g.drawString(s2, (W-sw2)/2, H/2+28);
    }

    private void label(Graphics2D g, String s, int x, int y){
        g.setColor(Color.BLACK); g.drawString(s, x+1, y+1);
        g.setColor(Color.WHITE); g.drawString(s, x, y);
    }

    private void drawOption(Graphics2D g, String s, int x, int y, boolean selected){
        if (selected){
            g.setColor(new Color(0xF2C14E));
            g.fillRect(x-6, y-14, g.getFontMetrics().stringWidth(s)+12, 18);
            g.setColor(Color.BLACK); g.drawString(s, x, y);
        } else {
            g.setColor(Color.WHITE); g.drawString(s, x, y);
        }
    }

    private void drawSpriteMirrored(Graphics2D g, Image img, int x, int y, int w, int h, boolean mirror){
        if (img == null) return;
        if (!mirror) { g.drawImage(img, x, y, w, h, null); return; }
        AffineTransform old = g.getTransform();
        AffineTransform tr = new AffineTransform(old);
        tr.translate(x + w, y);
        tr.scale(-1, 1);
        g.setTransform(tr);
        g.drawImage(img, 0, 0, w, h, null);
        g.setTransform(old);
    }
}
