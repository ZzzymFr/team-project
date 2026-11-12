package view.board.doc;

import view.board.BoardTheme;
import view.board.Icons;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

// ribbon button: icon, text, or a dropdown; looks clickable, does nothing yet
class RibbonButton extends JComponent {
    static final int H = 30;
    private static final Color HOVER = new Color(0, 0, 0, 14);
    private static final Color PRESS = new Color(0, 0, 0, 28);
    private static final Color BOX = new Color(0, 0, 0, 30);

    private final Icons.Glyph icon;
    private final String text;
    private final Font textFont;
    private final boolean dropdown;
    private final int minWidth;
    private Color underline;   // colour bar under "A" / highlighter
    private boolean hover;
    private boolean pressed;
    private Runnable action;

    private RibbonButton(Icons.Glyph icon, String text, Font textFont, boolean dropdown, int minWidth) {
        this.icon = icon;
        this.text = text;
        this.textFont = textFont;
        this.dropdown = dropdown;
        this.minWidth = minWidth;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
                if (action != null && contains(e.getPoint())) action.run();
            }
        };
        addMouseListener(m);
    }

    RibbonButton onClick(Runnable callback) {
        action = java.util.Objects.requireNonNull(callback);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return this;
    }

    static RibbonButton icon(Icons.Glyph icon, String tip) {
        RibbonButton b = new RibbonButton(icon, null, null, false, H);
        b.setToolTipText(tip);
        return b;
    }

    // one styled letter, e.g. bold B
    static RibbonButton letter(String letter, int style, boolean underlined, boolean struck, String tip) {
        Font f = BoardTheme.font(15f).deriveFont(style);
        if (underlined || struck) {
            java.util.Map<java.awt.font.TextAttribute, Object> a = new java.util.HashMap<>();
            if (underlined) {
                a.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
            }
            if (struck) {
                a.put(java.awt.font.TextAttribute.STRIKETHROUGH, java.awt.font.TextAttribute.STRIKETHROUGH_ON);
            }
            f = f.deriveFont(a);
        }
        RibbonButton b = new RibbonButton(null, letter, f, false, H);
        b.setToolTipText(tip);
        return b;
    }

    // letter with a colour bar below (text colour, highlight)
    static RibbonButton colored(String letter, Color bar, String tip) {
        RibbonButton b = letter(letter, Font.PLAIN, false, false, tip);
        b.underline = bar;
        return b;
    }

    // boxed dropdown like the font picker
    static RibbonButton dropdown(String text, int width, String tip) {
        RibbonButton b = new RibbonButton(null, text, BoardTheme.font(13f), true, width);
        b.setToolTipText(tip);
        return b;
    }

    // icon + label, for the Insert row
    static RibbonButton labeled(Icons.Glyph icon, String text) {
        return new RibbonButton(icon, text, BoardTheme.font(13f), false, H);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = minWidth;
        if (text != null && !dropdown) {
            FontMetrics fm = getFontMetrics(textFont);
            w = Math.max(w, fm.stringWidth(text) + (icon != null ? 20 + 6 + 16 : 16));
        }
        return new Dimension(w, H);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth();
        int h = getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 8, 8);
        if (dropdown) {
            g2.setColor(Color.WHITE);
            g2.fill(shape);
            g2.setColor(hover ? new Color(0, 0, 0, 60) : BOX);
            g2.draw(shape);
        } else if (pressed || hover) {
            g2.setColor(pressed ? PRESS : HOVER);
            g2.fill(shape);
        }
        g2.setColor(BoardTheme.LABEL);
        if (dropdown) {
            g2.setFont(textFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.clipRect(0, 0, w - 22, h);
            g2.drawString(text, 9, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.setClip(null);
            Path2D chevron = new Path2D.Double();
            chevron.moveTo(w - 16, h / 2.0 - 2);
            chevron.lineTo(w - 12, h / 2.0 + 2);
            chevron.lineTo(w - 8, h / 2.0 - 2);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(chevron);
        } else if (icon != null && text != null) {
            Graphics2D ig = (Graphics2D) g2.create();
            ig.translate(8 + 10, h / 2.0);
            ig.scale(0.85, 0.85);
            icon.paint(ig);
            ig.dispose();
            g2.setFont(textFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 8 + 20 + 6, (h - fm.getHeight()) / 2 + fm.getAscent());
        } else if (icon != null) {
            Graphics2D ig = (Graphics2D) g2.create();
            ig.translate(w / 2.0, h / 2.0);
            ig.scale(0.85, 0.85);
            icon.paint(ig);
            ig.dispose();
        } else {
            g2.setFont(textFont);
            FontMetrics fm = g2.getFontMetrics();
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - (underline != null ? 3 : 0);
            g2.drawString(text, (w - fm.stringWidth(text)) / 2, ty);
            if (underline != null) {
                g2.setColor(underline);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(w / 2.0 - 7, h - 7, w / 2.0 + 7, h - 7));
            }
        }
        g2.dispose();
    }
}
