package view.canvas;

import view.glass.GlassTextField;
import view.stack.Fonts;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;

// x / y range boxes that hang under the graph card
final class GraphRangeBar extends JPanel {
    private static final Color BORDER = new Color(203, 213, 225);
    private final GlassTextField xRange = new GlassTextField(6, "-10, 10");
    private final GlassTextField yRange = new GlassTextField(6, "auto");

    GraphRangeBar(Runnable onEnter) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setOpaque(false);
        xRange.setToolTipText("x range, e.g. -5, 5. Empty: -10 to 10.");
        yRange.setToolTipText("y range, e.g. -2, 8. Empty or auto: chosen from the function.");
        xRange.addActionListener(e -> onEnter.run());
        yRange.addActionListener(e -> onEnter.run());
        add(axis("x"));
        add(xRange);
        add(axis("y"));
        add(yRange);
        addMouseListener(new MouseAdapter() { });  // clicks stay off the canvas
        setVisible(false);
    }

    String xText() {
        return xRange.getText();
    }

    String yText() {
        return yRange.getText();
    }

    void setRangeText(String x, String y) {
        xRange.setText(x);
        yRange.setText(y);
    }

    private static JLabel axis(String name) {
        JLabel label = new JLabel(name);
        label.setFont(Fonts.ui(Font.ITALIC, 15f));
        label.setForeground(new Color(51, 65, 85));
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 235));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.dispose();
    }
}
