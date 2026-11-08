package view;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * Things placed on the A4 sheets besides the ink, such as the calculations, graphs and notes of a
 * PDF document. The {@link DrawingCanvas} paints them on the paper under the ink and hands them the
 * lasso tool's clicks; everything is in world coordinates (see {@link Paper}).
 *
 * <p>Before each change the layer calls {@link Host#beforeChange()}, so one Undo steps back through
 * ink and items alike: the canvas keeps the layer's {@link #saveState() state} with its own.
 */
public interface PaperLayer {
    /** What the canvas offers the layer. */
    interface Host {
        /** Call before changing anything, so it can be undone. */
        void beforeChange();

        /** Call after a change (or a new selection), so the canvas repaints. */
        void changed();

        /** The canvas zoom (1 = 100%), for sizing handles on screen. */
        double zoom();

        /** How many sheets the canvas shows now. */
        int pageCount();

        /** The world point in the middle of the view, e.g. where to put something new. */
        Point2D viewCenter();

        /** Switches the canvas to the tool that moves items, e.g. after inserting one. */
        void useSelectTool();

        /** The canvas component, e.g. to own a dialog. */
        Component component();
    }

    void setHost(Host host);

    /** How many sheets the items reach: the last sheet with an item on it plus one, 0 if none. */
    int pagesUsed();

    /** Paints the items, and the selection, in world coordinates. */
    void paint(Graphics2D world);

    /** A left press with the lasso tool: true if it was on an item, which is then selected and can be dragged. */
    boolean press(Point2D world, int clickCount);

    /** The mouse moved with the button down after a press the layer took. */
    void drag(Point2D world);

    void release();

    /** Shows the item's menu if the point is on one; true if it was. */
    boolean showMenu(Component owner, Point screen, Point2D world);

    /** The cursor to show over this point with the lasso tool, or null if it is not over an item. */
    Cursor cursorAt(Point2D world);

    boolean hasSelection();

    /** Unselects the item; true if one was selected. */
    boolean clearSelection();

    /** Removes the selected item, if any (calling {@link Host#beforeChange()} first). */
    void deleteSelection();

    /** Something dragged onto the paper and dropped there; true if the layer took it. */
    boolean drop(String data, Point2D world);

    /** Everything needed to put the items back as they are now. */
    Object saveState();

    void restoreState(Object state);
}
