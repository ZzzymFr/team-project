package view;

import entity.InkPoint;
import entity.Stroke;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A white panel the user writes on with the mouse or a pen; keeps the ink as strokes. */
public class DrawingCanvas extends JPanel {
    private static final float INK_WIDTH = 4f;
    private static final Color INK_COLOR = Color.BLACK;

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke;

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 500));
        MouseAdapter pen = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                currentStroke = new Stroke();
                strokes.add(currentStroke);
                record(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (currentStroke != null) {
                    record(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                currentStroke = null;
            }
        };
        addMouseListener(pen);
        addMouseMotionListener(pen);
    }

    private void record(MouseEvent e) {
        currentStroke.addPoint(new InkPoint(e.getX(), e.getY(), e.getWhen()));
        repaint();
    }

    public List<Stroke> getStrokes() {
        return Collections.unmodifiableList(strokes);
    }

    public boolean isBlank() {
        return strokes.isEmpty();
    }

    /** Removes the most recent stroke. */
    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            currentStroke = null;
            repaint();
        }
    }

    public void clear() {
        strokes.clear();
        currentStroke = null;
        repaint();
    }

    /** Renders the ink as dark strokes on a white background, the size of the canvas. */
    public BufferedImage toImage() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        drawStrokes(g2);
        g2.dispose();
        return image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        drawStrokes(g2);
        g2.dispose();
    }

    private void drawStrokes(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setStroke(new BasicStroke(INK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(INK_COLOR);
        for (Stroke stroke : strokes) {
            List<InkPoint> points = stroke.getPoints();
            // A single point (a tap, e.g. a decimal point) is drawn as a dot by the round cap.
            InkPoint previous = points.get(0);
            for (InkPoint point : points) {
                g2.drawLine(previous.getX(), previous.getY(), point.getX(), point.getY());
                previous = point;
            }
        }
    }
}
