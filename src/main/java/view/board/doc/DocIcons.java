package view.board.doc;

import view.board.Icons;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

// ribbon glyphs, same 20x20 grid as Icons
final class DocIcons {
    private DocIcons() {
    }

    private static void prep(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    static void redo(Graphics2D g) {
        AffineTransform t = g.getTransform();
        g.scale(-1, 1);
        Icons.undo(g);
        g.setTransform(t);
    }

    // four lines, short ones placed by alignment: 0 left, 1 centre, 2 right, 3 justify
    static Icons.Glyph align(int how) {
        return g -> {
            prep(g);
            double[] widths = {14, 9, 14, 9};
            for (int i = 0; i < 4; i++) {
                double w = how == 3 ? 14 : widths[i];
                double x0 = how == 0 || how == 3 ? -7 : how == 1 ? -w / 2 : 7 - w;
                double y = -6 + i * 4;
                g.draw(new Line2D.Double(x0, y, x0 + w, y));
            }
        };
    }

    static void bullets(Graphics2D g) {
        prep(g);
        for (int i = 0; i < 3; i++) {
            double y = -5 + i * 5;
            g.fill(new Ellipse2D.Double(-8, y - 1.3, 2.6, 2.6));
            g.draw(new Line2D.Double(-2.5, y, 8, y));
        }
    }

    static void numbered(Graphics2D g) {
        prep(g);
        g.setFont(g.getFont().deriveFont(5.5f));
        for (int i = 0; i < 3; i++) {
            double y = -5 + i * 5;
            g.drawString(String.valueOf(i + 1), -9f, (float) y + 2f);
            g.draw(new Line2D.Double(-2.5, y, 8, y));
        }
    }

    static void image(Graphics2D g) {
        prep(g);
        g.draw(new RoundRectangle2D.Double(-8.5, -7, 17, 14, 3, 3));
        Path2D m = new Path2D.Double();
        m.moveTo(-8, 5);
        m.lineTo(-3, -0.5);
        m.lineTo(1, 3);
        m.lineTo(4, 0.5);
        m.lineTo(8, 4.5);
        g.draw(m);
        g.fill(new Ellipse2D.Double(2.5, -4.5, 2.6, 2.6));
    }

    // Σ for "insert formula"
    static void formula(Graphics2D g) {
        prep(g);
        Path2D s = new Path2D.Double();
        s.moveTo(6, -7);
        s.lineTo(-6, -7);
        s.lineTo(1, 0);
        s.lineTo(-6, 7);
        s.lineTo(6, 7);
        g.draw(s);
    }

    static void table(Graphics2D g) {
        prep(g);
        g.draw(new RoundRectangle2D.Double(-8, -7, 16, 14, 2, 2));
        g.draw(new Line2D.Double(-8, -2.3, 8, -2.3));
        g.draw(new Line2D.Double(-8, 2.3, 8, 2.3));
        g.draw(new Line2D.Double(-2.7, -7, -2.7, 7));
        g.draw(new Line2D.Double(2.7, -7, 2.7, 7));
    }

    static void pageBreak(Graphics2D g) {
        prep(g);
        g.draw(new Line2D.Double(-6, -8, -6, -3));
        g.draw(new Line2D.Double(6, -8, 6, -3));
        g.draw(new Line2D.Double(-6, -3, 6, -3));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{2f, 2f}, 0f));
        g.draw(new Line2D.Double(-9, 0.5, 9, 0.5));
        prep(g);
        g.draw(new Line2D.Double(-6, 4, -6, 8));
        g.draw(new Line2D.Double(6, 4, 6, 8));
        g.draw(new Line2D.Double(-6, 4, 6, 4));
    }

    static void comment(Graphics2D g) {
        prep(g);
        Path2D p = new Path2D.Double();
        p.append(new RoundRectangle2D.Double(-8, -7, 16, 11, 4, 4), false);
        g.draw(p);
        Path2D tail = new Path2D.Double();
        tail.moveTo(-3, 4);
        tail.lineTo(-5, 8);
        tail.lineTo(1, 4);
        g.draw(tail);
    }
}
