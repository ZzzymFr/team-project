package view.glass;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Line icons drawn with Java2D in a style similar to SF Symbols (SF Symbols itself is licensed for
 * Apple-platform apps only). Each is on a 20x20 grid centred at (0, 0); the caller sets the colour.
 */
public final class GlassIcons {
    /** Paints one icon. */
    public interface Glyph {
        void paint(Graphics2D g);
    }

    private GlassIcons() {
    }

    private static void prepare(Graphics2D g) {
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public static void scribble(Graphics2D g) {
        prepare(g);
        Path2D p = new Path2D.Double();
        p.moveTo(-8, 5);
        p.curveTo(-6, -7, -3, -7, -1, 0);
        p.curveTo(1, 7, 5, 7, 8, -5);
        g.draw(p);
    }

    public static void document(Graphics2D g) {
        prepare(g);
        Path2D p = new Path2D.Double();
        p.moveTo(-6, -8.5);
        p.lineTo(2, -8.5);
        p.lineTo(6.5, -4);
        p.lineTo(6.5, 8.5);
        p.lineTo(-6, 8.5);
        p.closePath();
        g.draw(p);
        Path2D fold = new Path2D.Double();
        fold.moveTo(2, -8.5);
        fold.lineTo(2, -4);
        fold.lineTo(6.5, -4);
        g.draw(fold);
        g.draw(new Line2D.Double(-3, 0, 3.5, 0));
        g.draw(new Line2D.Double(-3, 4, 3.5, 4));
    }

    public static void equalCircle(Graphics2D g) {
        prepare(g);
        g.draw(new Ellipse2D.Double(-8.5, -8.5, 17, 17));
        g.draw(new Line2D.Double(-4, -2.2, 4, -2.2));
        g.draw(new Line2D.Double(-4, 2.2, 4, 2.2));
    }

    public static void pencil(Graphics2D g) {
        prepare(g);
        AffineTransform t = g.getTransform();
        g.rotate(-Math.PI / 4);
        Path2D p = new Path2D.Double();
        p.moveTo(-6, -2.8);
        p.lineTo(8, -2.8);
        p.lineTo(8, 2.8);
        p.lineTo(-6, 2.8);
        p.lineTo(-10.5, 0);
        p.closePath();
        g.draw(p);
        g.draw(new Line2D.Double(-6, -2.8, -6, 2.8));
        g.draw(new Line2D.Double(5, -2.8, 5, 2.8));
        g.setTransform(t);
    }

    public static void eraser(Graphics2D g) {
        prepare(g);
        AffineTransform t = g.getTransform();
        g.rotate(-Math.PI / 4);
        RoundRectangle2D body = new RoundRectangle2D.Double(-8.5, -3.8, 17, 7.6, 3, 3);
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(-9, -5, 7.5, 10));
        g.fill(body);
        g.setClip(clip);
        g.draw(body);
        g.setTransform(t);
        g.draw(new Line2D.Double(-2, 8.5, 8, 8.5));
    }

    /** A dashed loop with a short tail, for the selection tool. */
    public static void lasso(Graphics2D g) {
        prepare(g);
        Stroke solid = g.getStroke();
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[]{2.6f, 3.2f}, 0f));
        g.draw(new Ellipse2D.Double(-8.5, -8, 17, 12.5));
        g.setStroke(solid);
        Path2D tail = new Path2D.Double();
        tail.moveTo(-3.5, 4);
        tail.curveTo(-5.5, 6.5, -2.5, 8, -4.5, 9.5);
        g.draw(tail);
    }

    public static void undo(Graphics2D g) {
        prepare(g);
        Path2D p = new Path2D.Double();
        p.moveTo(-7, -3);
        p.lineTo(2, -3);
        p.append(new Arc2D.Double(-2.5, -3, 9, 9, 90, -180, Arc2D.OPEN), true);
        p.lineTo(-3, 6);
        g.draw(p);
        Path2D head = new Path2D.Double();
        head.moveTo(-3.5, -6.5);
        head.lineTo(-7, -3);
        head.lineTo(-3.5, 0.5);
        g.draw(head);
    }

    public static void trash(Graphics2D g) {
        prepare(g);
        g.draw(new Line2D.Double(-7.5, -5.5, 7.5, -5.5));
        Path2D lid = new Path2D.Double();
        lid.moveTo(-2.5, -5.5);
        lid.lineTo(-2.5, -8);
        lid.lineTo(2.5, -8);
        lid.lineTo(2.5, -5.5);
        g.draw(lid);
        Path2D can = new Path2D.Double();
        can.moveTo(-5.5, -5.5);
        can.lineTo(-4.5, 8.5);
        can.lineTo(4.5, 8.5);
        can.lineTo(5.5, -5.5);
        g.draw(can);
        g.draw(new Line2D.Double(-1.8, -2, -1.5, 5.5));
        g.draw(new Line2D.Double(1.8, -2, 1.5, 5.5));
    }
}
