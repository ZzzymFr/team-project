package view;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The canvas the graph is drawn on. Shows the image scaled to fit, or a centred message.
 */
public class GraphCanvas extends JPanel {
    private BufferedImage image;
    private String message = "Write a function and press Graph.";

    public GraphCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(800, 500));
    }

    public void showImage(BufferedImage image) {
        this.image = image;
        this.message = null;
        repaint();
    }

    public void showMessage(String message) {
        this.image = null;
        this.message = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (image != null) {
            double scale = Math.min((double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight());
            int w = (int) (image.getWidth() * scale);
            int h = (int) (image.getHeight() * scale);
            g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        } else if (message != null) {
            g2.setColor(Color.DARK_GRAY);
            FontMetrics fm = g2.getFontMetrics();
            int x = Math.max(8, (getWidth() - fm.stringWidth(message)) / 2);
            g2.drawString(message, x, getHeight() / 2);
        }
        g2.dispose();
    }
}
