package World.cutscene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Modal dialog used to play narrative cutscenes.
 */
public final class CutsceneDialog extends JDialog {

    private CutsceneDialog(Window owner, CutsceneScript script) {
        super(owner, "Cutscene", ModalityType.APPLICATION_MODAL);
        CutscenePanel panel = new CutscenePanel(script);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    public static void play(Window owner, CutsceneScript script) {
        if (script == null || script.slides().isEmpty()) {
            return;
        }
        CutsceneDialog dialog = new CutsceneDialog(owner, script);
        dialog.setVisible(true);
    }

    private static final class CutscenePanel extends JPanel {
        private final List<CutsceneSlide> slides;
        private int slideIndex = 0;
        private int charsVisible = 0;
        private final Timer timer;
        private long tick = 0;
        private boolean fastForward;

        private CutscenePanel(CutsceneScript script) {
            this.slides = script.slides();
            setPreferredSize(new Dimension(720, 420));
            setOpaque(false);
            setFocusable(true);

            timer = new Timer(1000 / 60, e -> {
                tick++;
                if (!fastForward) {
                    charsVisible = Math.min(currentText().length(), charsVisible + 2);
                }
                repaint();
            });
            timer.start();

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        disposeDialog();
                        return;
                    }
                    advance();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    advance();
                }
            });
        }

        private void disposeDialog() {
            timer.stop();
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.dispose();
            }
        }

        private void advance() {
            if (charsVisible < currentText().length()) {
                charsVisible = currentText().length();
                fastForward = true;
                repaint();
                return;
            }
            fastForward = false;
            if (slideIndex + 1 >= slides.size()) {
                disposeDialog();
            } else {
                slideIndex++;
                charsVisible = 0;
            }
        }

        private String currentText() {
            return slides.get(slideIndex).text();
        }

        private CutsceneSlide currentSlide() {
            return slides.get(slideIndex);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                CutsceneSlide slide = currentSlide();
                AnimatedBackdrop backdrop = slide.backdrop() == null ? CutsceneBackgrounds.emberSwirl() : slide.backdrop();
                backdrop.paint(g2, getWidth(), getHeight(), tick);

                drawPortrait(g2, slide.portrait());
                drawTextBox(g2, slide);
            } finally {
                g2.dispose();
            }
        }

        private void drawPortrait(Graphics2D g2, CutscenePortrait portrait) {
            BufferedImage img = portrait == null ? null : portrait.image();
            if (img == null) {
                return;
            }
            int size = Math.min(img.getWidth(), img.getHeight());
            int drawWidth = size;
            int drawHeight = size;
            int x = 32;
            int y = getHeight() - drawHeight - 120;
            g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));
            g2.drawImage(img, x, y, drawWidth, drawHeight, null);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        private void drawTextBox(Graphics2D g2, CutsceneSlide slide) {
            int boxHeight = 150;
            int boxY = getHeight() - boxHeight - 24;
            g2.setColor(new Color(12, 16, 28, 210));
            g2.fillRoundRect(28, boxY, getWidth() - 56, boxHeight, 28, 28);
            g2.setColor(new Color(255, 236, 190, 200));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(28, boxY, getWidth() - 56, boxHeight, 28, 28);

            g2.setColor(new Color(255, 240, 210));
            Font nameFont = g2.getFont().deriveFont(Font.BOLD, 20f);
            Font textFont = g2.getFont().deriveFont(18f);
            g2.setFont(nameFont);
            String speaker = slide.speaker() == null ? "" : slide.speaker();
            g2.drawString(speaker, 48, boxY + 36);

            g2.setFont(textFont);
            String text = currentText().substring(0, Math.min(charsVisible, currentText().length()));
            drawWrappedText(g2, text, 48, boxY + 64, getWidth() - 96);
        }

        private void drawWrappedText(Graphics2D g2, String text, int x, int y, int width) {
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            String remaining = text;
            int cursorY = y;
            while (!remaining.isEmpty()) {
                int len = remaining.length();
                while (len > 0 && fm.stringWidth(remaining.substring(0, len)) > width) {
                    len--;
                }
                if (len <= 0) {
                    break;
                }
                String line = remaining.substring(0, len);
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 0 && len < remaining.length()) {
                    line = line.substring(0, lastSpace);
                    len = lastSpace + 1;
                }
                TextLayout layout = new TextLayout(line, g2.getFont(), g2.getFontRenderContext());
                layout.draw(g2, x, cursorY);
                cursorY += lineHeight;
                remaining = remaining.substring(Math.min(len, remaining.length()));
            }
        }
    }
}
