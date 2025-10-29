package World.cutscene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Modal dialog used to play narrative cutscenes.
 */
public final class CutsceneDialog extends JDialog {

    private CutsceneDialog(Window owner, CutsceneScript script) {
        super(owner, "Cutscene", ModalityType.APPLICATION_MODAL);
        Rectangle bounds = preferredBounds(owner);
        CutscenePanel panel = new CutscenePanel(script, bounds.getSize());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(panel);
        pack();
        setBounds(bounds);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
    }

    private static Rectangle preferredBounds(Window owner) {
        GraphicsConfiguration configuration = owner == null
                ? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                : owner.getGraphicsConfiguration();
        Rectangle screenBounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int width = Math.max(800, screenBounds.width - insets.left - insets.right);
        int height = Math.max(600, screenBounds.height - insets.top - insets.bottom);
        return new Rectangle(screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                width,
                height);
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

        private CutscenePanel(CutsceneScript script, Dimension preferredSize) {
            this.slides = script.slides();
            setPreferredSize(preferredSize);
            setMinimumSize(preferredSize);
            setMaximumSize(preferredSize);
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

                int textBoxMargin = Math.max(32, (int) (getHeight() * 0.04));
                int textBoxHeight = drawTextBox(g2, slide, textBoxMargin);
                drawPortrait(g2, slide.portrait(), textBoxHeight, textBoxMargin);
            } finally {
                g2.dispose();
            }
        }

        private void drawPortrait(Graphics2D g2, CutscenePortrait portrait, int textBoxHeight, int textBoxMargin) {
            BufferedImage img = portrait == null ? null : portrait.image();
            if (img == null) {
                return;
            }
            double maxPortraitWidth = getWidth() * 0.32;
            double maxPortraitHeight = getHeight() - (textBoxHeight + textBoxMargin * 3);
            maxPortraitHeight = Math.max(maxPortraitHeight, getHeight() * 0.4);
            double scale = Math.min(maxPortraitWidth / img.getWidth(), maxPortraitHeight / img.getHeight());
            scale = Math.max(scale, 0.2);
            int drawWidth = (int) Math.round(img.getWidth() * scale);
            int drawHeight = (int) Math.round(img.getHeight() * scale);
            int x = textBoxMargin;
            int y = getHeight() - textBoxHeight - textBoxMargin * 2 - drawHeight;
            g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));
            g2.drawImage(img, x, y, drawWidth, drawHeight, null);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        private int drawTextBox(Graphics2D g2, CutsceneSlide slide, int margin) {
            int boxWidth = getWidth() - margin * 2;
            int boxHeight = Math.max((int) (getHeight() * 0.26), 200);
            int boxY = getHeight() - boxHeight - margin;
            g2.setColor(new Color(12, 16, 28, 220));
            g2.fillRoundRect(margin, boxY, boxWidth, boxHeight, margin, margin);
            g2.setColor(new Color(255, 236, 190, 210));
            g2.setStroke(new BasicStroke(Math.max(2f, margin / 12f)));
            g2.drawRoundRect(margin, boxY, boxWidth, boxHeight, margin, margin);

            g2.setColor(new Color(255, 240, 210));
            float nameSize = Math.max(28f, getHeight() * 0.045f);
            float textSize = Math.max(24f, getHeight() * 0.035f);
            Font nameFont = g2.getFont().deriveFont(Font.BOLD, nameSize);
            Font textFont = g2.getFont().deriveFont(textSize);
            g2.setFont(nameFont);
            String speaker = slide.speaker() == null ? "" : slide.speaker();
            int speakerBaseline = boxY + (int) (margin * 1.2 + nameSize);
            g2.drawString(speaker, margin * 2, speakerBaseline);

            g2.setFont(textFont);
            String text = currentText().substring(0, Math.min(charsVisible, currentText().length()));
            int textTop = speakerBaseline + (int) (margin * 0.8);
            drawWrappedText(g2, text, margin * 2, textTop, boxWidth - margin * 2);
            return boxHeight;
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
