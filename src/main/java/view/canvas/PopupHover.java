package view.canvas;

import view.answer.EqualsSpot;
import view.answer.Formula;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// mouse resting on an unanswered formula's "=" brings its popup back
final class PopupHover {
    static final int SHOW_MILLIS = 300;
    static final int HIDE_MILLIS = 300;

    // the answer page
    interface Host {
        boolean canOffer();                  // nothing else going on
        void offer(long formulaId);          // put the popup up for it (may read it first)
        void withdraw();                     // take it down
        Rectangle popupBounds();             // what you see of it, area coords; null if hidden
        String latexOf(long id, long revision);  // read already, null if not
    }

    private final SharedCanvas canvas;
    private final JComponent area;
    private final Host host;
    private final Map<Long, Spot> spots = new HashMap<>();
    private final Timer showTimer = new Timer(SHOW_MILLIS, e -> rested());
    private final Timer hideTimer = new Timer(HIDE_MILLIS, e -> maybeLeave());
    private long under = -1;    // formula whose "=" the mouse is on
    private long offered = -1;  // the one we put the popup up for

    PopupHover(SharedCanvas canvas, JComponent area, Host host) {
        this.canvas = canvas;
        this.area = area;
        this.host = host;
        showTimer.setRepeats(false);
        canvas.getDrawingCanvas().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                moved(e.getPoint());
            }
        });
        canvas.getDrawingCanvas().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                moved(null);  // onto the popup, or out of the canvas
            }
        });
    }

    // popup went away some other way
    void forget() {
        offered = -1;
        hideTimer.stop();
    }

    long offered() {
        return offered;
    }

    // canvas coords, null = off the canvas
    void moved(Point p) {
        long now = p == null ? -1 : formulaAt(p);
        if (offered >= 0) {
            if (now == offered) {
                hideTimer.stop();
            } else if (!hideTimer.isRunning()) {
                hideTimer.start();
            }
            return;
        }
        if (now != under) {
            showTimer.stop();
        }
        under = now;
        if (under >= 0 && !showTimer.isRunning()) {
            showTimer.restart();
        }
    }

    private void rested() {
        if (under < 0 || offered >= 0 || !host.canOffer()) {
            return;
        }
        offered = under;
        host.offer(under);
    }

    // keep it while the mouse is on the popup or back on the "="
    private void maybeLeave() {
        Point p = pointer();
        Rectangle popup = host.popupBounds();
        if (p != null && popup != null && popup.contains(p)) {
            return;
        }
        Point onCanvas = p == null ? null : SwingUtilities.convertPoint(area, p, canvas.getDrawingCanvas());
        if (onCanvas != null && formulaAt(onCanvas) == offered) {
            hideTimer.stop();
            return;
        }
        hideTimer.stop();
        offered = -1;
        under = -1;
        host.withdraw();
    }

    // area coords, null if unknown (headless)
    private Point pointer() {
        if (!area.isShowing() || GraphicsEnvironment.isHeadless()) {
            return null;
        }
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return null;
        }
        Point p = info.getLocation();
        SwingUtilities.convertPointFromScreen(p, area);
        return p;
    }

    // unanswered formula whose "=" is under p, -1 if none
    long formulaAt(Point p) {
        for (Formula f : canvas.getFormulas()) {
            Rectangle world = f.getAnswer() == null ? spotOf(f).world : null;
            if (world != null && canvas.worldToScreen(world).contains(p)) {
                return f.getId();
            }
        }
        return -1;
    }

    private Spot spotOf(Formula f) {
        String latex = host.latexOf(f.getId(), f.getRevision());
        Spot s = spots.get(f.getId());
        if (s == null || s.revision != f.getRevision() || !Objects.equals(s.latex, latex)) {
            s = new Spot(f.getRevision(), latex, EqualsSpot.find(f.getStrokes(), f.getCharHeight(), latex));
            spots.put(f.getId(), s);
        }
        return s;
    }

    // cached per formula, redone when the ink or the read changes
    private static final class Spot {
        final long revision;
        final String latex;
        final Rectangle world;

        Spot(long revision, String latex, Rectangle world) {
            this.revision = revision;
            this.latex = latex;
            this.world = world;
        }
    }
}
