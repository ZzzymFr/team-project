package view.glass;

import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A text field made of liquid glass: a clear capsule with a light rim and top highlight. It takes
 * a blue tint while you type in it, and shows a grey placeholder while it is empty.
 * Works like any JTextField (getText, addActionListener, ...).
 */
public class GlassTextField extends JTextField {
    private static final int HEIGHT = 40;
    private static final int TEXT_PADDING = 18;
    private static final Color PLACEHOLDER = new Color(148, 163, 184);

    private final String placeholder;
    private boolean hovering;

    public GlassTextField(int columns, String placeholder) {
        super(columns);
        this.placeholder = placeholder;
        setFont(Fonts.ui(Font.PLAIN, 15f));
        setForeground(Glass.TEXT);
        setCaretColor(Glass.ACCENT);
        setSelectionColor(new Color(0, 122, 255, 60));
        setOpaque(false);
        int s = Glass.SHADOW;
        setBorder(BorderFactory.createEmptyBorder(s, s + TEXT_PADDING, s, s + TEXT_PADDING));
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, HEIGHT + 2 * Glass.SHADOW);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        double s = Glass.SHADOW;
        boolean focused = isFocusOwner();
        Glass.paint(g2, Glass.capsule(s, s, getWidth() - 2 * s, getHeight() - 2 * s),
                focused ? Glass.ACCENT : null, focused ? 0.3 : 0, hovering && !focused ? 0.6 : 0, 0, 0);
        if (getText().isEmpty() && !focused && placeholder != null) {
            g2.setFont(getFont());
            g2.setColor(PLACEHOLDER);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(placeholder, getInsets().left, baseline);
        }
        g2.dispose();
        super.paintComponent(g);  // text, caret and selection on top of the glass
    }
}
