package view.answer;

import interface_adapter.TextRun;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// answer text drawn like pen ink: kalam's centre line traced w/ the same round pen
public final class AnswerInk {
    private static final float REF = 240f;  // font size the centre lines get traced at
    private static final int PAD = 2;
    private static final double TWIG = 1.0;  // twigs up to this x pen width get cut
    private static final int CACHE_SIZE = 64;
    private static final Map<Key, Path2D> LINES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Path2D> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static double digitRatio;  // centre line height of "0" per point

    private AnswerInk() {
    }

    // font size whose "0" centre line is charHeight tall, same as a pen stroke's point box
    public static synchronized float sizeFor(double charHeight) {
        if (digitRatio == 0) {
            digitRatio = centerline("0", null).getBounds2D().getHeight() / REF;
        }
        return (float) (charHeight / digitRatio);
    }

    // ink box at (0,0): centre line box + half a pen all round
    public static Rectangle2D bounds(String text, List<TextRun> runs, float size, double penWidth) {
        Rectangle2D line = scaled(text, runs, size).getBounds2D();
        return new Rectangle2D.Double(0, 0, line.getWidth() + penWidth, line.getHeight() + penWidth);
    }

    // centre line where the answer sits, world coords
    public static Path2D placed(CanvasAnswer a, double penWidth) {
        Path2D line = scaled(a.getText(), a.getRuns(), a.getFontSize());
        Rectangle2D b = line.getBounds2D();
        line.transform(AffineTransform.getTranslateInstance(a.getX() + penWidth / 2 - b.getX(),
                a.getY() + penWidth / 2 - b.getY()));
        return line;
    }

    // filled ink outline, world coords
    public static Shape outline(CanvasAnswer a, double penWidth) {
        return pen(penWidth).createStrokedShape(placed(a, penWidth));
    }

    public static void paint(Graphics2D world, CanvasAnswer a, Color color, double penWidth) {
        Graphics2D g = (Graphics2D) world.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(pen(penWidth));
        g.setColor(color);
        g.draw(placed(a, penWidth));
        g.dispose();
    }

    // eraser dragged from (x1,y1) to (x2,y2) w/ this radius hits actual ink, not just the box
    public static boolean touches(CanvasAnswer a, double penWidth, double x1, double y1, double x2, double y2,
                                  double radius) {
        Rectangle2D box = a.getBox();
        box.setRect(box.getX() - radius, box.getY() - radius, box.getWidth() + 2 * radius,
                box.getHeight() + 2 * radius);
        if (!box.intersectsLine(x1, y1, x2, y2)) {
            return false;
        }
        double reach = radius + penWidth / 2;
        double[] c = new double[6];
        for (PathIterator it = placed(a, penWidth).getPathIterator(null); !it.isDone(); it.next()) {
            it.currentSegment(c);
            if (Line2D.ptSegDist(x1, y1, x2, y2, c[0], c[1]) <= reach) {
                return true;
            }
        }
        return false;
    }

    // same answer after a resize, box refit since the pen width doesn't scale
    public static CanvasAnswer refit(CanvasAnswer a, double penWidth) {
        Rectangle2D b = bounds(a.getText(), a.getRuns(), a.getFontSize(), penWidth);
        return a.sized(b.getWidth(), b.getHeight());
    }

    private static BasicStroke pen(double width) {
        return new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    // centre line at this size, fresh copy
    private static Path2D scaled(String text, List<TextRun> runs, float size) {
        Path2D line = new Path2D.Double(centerline(text, runs));
        line.transform(AffineTransform.getScaleInstance(size / REF, size / REF));
        return line;
    }

    // traced once per text at REF size, baseline origin
    static synchronized Path2D centerline(String text, List<TextRun> runs) {
        Key key = new Key(text, runs);
        Path2D line = LINES.get(key);
        if (line == null) {
            line = trace(runs == null ? AnswerFont.outline(text, REF) : AnswerFont.outline(runs, REF));
            LINES.put(key, line);
        }
        return line;
    }

    // raster the glyphs, thin to 1px, join neighbouring pixels w/ tiny segments
    private static Path2D trace(Shape outline) {
        Rectangle2D b = outline.getBounds2D();
        int w = (int) Math.ceil(b.getWidth()) + 2 * PAD;
        int h = (int) Math.ceil(b.getHeight()) + 2 * PAD;
        Path2D line = new Path2D.Double();
        if (b.isEmpty()) {
            return line;
        }
        boolean[] filled = raster(outline, b, w, h);
        boolean[] thin = Skeleton.thin(filled, w, h);
        boolean[] on = Skeleton.prune(thin, w, h, (int) Math.round(TWIG * thickness(filled, thin)));
        double ox = b.getX() - PAD + 0.5;
        double oy = b.getY() - PAD + 0.5;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!on[y * w + x]) {
                    continue;
                }
                boolean east = x + 1 < w && on[y * w + x + 1];
                boolean south = y + 1 < h && on[(y + 1) * w + x];
                boolean joined = false;
                if (east) {
                    joined |= link(line, ox, oy, x, y, x + 1, y);
                }
                if (south) {
                    joined |= link(line, ox, oy, x, y, x, y + 1);
                }
                // diagonals only when no square path covers them
                if (y + 1 < h && x + 1 < w && on[(y + 1) * w + x + 1] && !east && !on[(y + 1) * w + x]) {
                    joined |= link(line, ox, oy, x, y, x + 1, y + 1);
                }
                if (y + 1 < h && x > 0 && on[(y + 1) * w + x - 1] && !south && !on[y * w + x - 1]) {
                    joined |= link(line, ox, oy, x, y, x - 1, y + 1);
                }
                if (!joined && !hasNeighbour(on, w, h, x, y)) {
                    link(line, ox, oy, x, y, x, y);  // lone dot, round cap draws it
                }
            }
        }
        return line;
    }

    // rough pen width of the glyphs: ink area / centre line length
    private static double thickness(boolean[] filled, boolean[] thin) {
        int area = 0;
        int line = 0;
        for (int i = 0; i < filled.length; i++) {
            area += filled[i] ? 1 : 0;
            line += thin[i] ? 1 : 0;
        }
        return line == 0 ? 0 : (double) area / line;
    }

    private static boolean link(Path2D line, double ox, double oy, int x1, int y1, int x2, int y2) {
        line.moveTo(ox + x1, oy + y1);
        line.lineTo(ox + x2, oy + y2);
        return true;
    }

    private static boolean hasNeighbour(boolean[] on, int w, int h, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < w && ny < h && on[ny * w + nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean[] raster(Shape outline, Rectangle2D b, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(PAD - b.getX(), PAD - b.getY());
        g.setColor(Color.WHITE);
        g.fill(outline);
        g.dispose();
        boolean[] on = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                on[y * w + x] = img.getRaster().getSample(x, y, 0) > 127;
            }
        }
        return on;
    }

    private static final class Key {
        private final String text;
        private final List<TextRun> runs;

        Key(String text, List<TextRun> runs) {
            this.text = text;
            this.runs = runs;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && text.equals(((Key) o).text) && Objects.equals(runs, ((Key) o).runs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, runs);
        }
    }
}
