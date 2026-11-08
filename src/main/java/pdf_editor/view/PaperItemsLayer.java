package pdf_editor.view;

import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.MaterialRenderer;
import pdf_editor.data_access.NoteRenderer;
import pdf_editor.domain.PdfDocument;
import view.Paper;
import view.PaperLayer;
import view.stack.Fonts;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The PDF document's items (calculations, graphs and notes) on the canvas's A4 sheets: page i of
 * the document is sheet i, so what the canvas shows is what the PDF gets. Placements are kept in
 * PDF points by the {@link PdfEditorController}; here they are drawn and moved in world units.
 *
 * <p>With the lasso tool, click an item to select it, drag it to move it (onto another sheet too),
 * drag its corner to resize it, double-click a note to edit it, right-click for its menu.
 */
public final class PaperItemsLayer implements PaperLayer {
    /** World units per PDF point: an A4 sheet is 794 units and 595 points wide. */
    static final double UNIT = Paper.PAGE_WIDTH / (double) PdfDocument.PAGE_WIDTH;
    private static final int RENDER_SCALE = 2;          // pixels per point in the cached pictures, as in the PDF
    private static final int HANDLE = 10;               // the resize handle, on screen
    private static final Color SELECTION = new Color(37, 99, 235);
    private static final float GRAPH_WIDTH = 260, GRAPH_HEIGHT = 180, ITEM_WIDTH = 300, ITEM_HEIGHT = 100;

    private final PdfEditorController controller;
    private final Map<PdfDocument.Placement, BufferedImage> pictures = new WeakHashMap<>();
    private Host host;
    private Drag drag;

    /** A press on an item, and how it is being dragged. */
    private static final class Drag {
        final Point2D start;
        final PdfDocument.Placement item;
        final boolean resizing;
        final double grabX, grabY;   // the press, from the item's top-left corner, in world units
        boolean started;

        Drag(Point2D start, PdfDocument.Placement item, Rectangle2D box, boolean resizing) {
            this.start = start;
            this.item = item;
            this.resizing = resizing;
            this.grabX = start.getX() - box.getX();
            this.grabY = start.getY() - box.getY();
        }
    }

    /** An item under a point: the sheet it is on and the item. */
    private record Hit(int page, PdfDocument.Placement item) { }

    public PaperItemsLayer(PdfEditorController controller) {
        this.controller = Objects.requireNonNull(controller);
    }

    @Override
    public void setHost(Host host) {
        this.host = host;
    }

    // ---------------------------------------------------------------- coordinates

    /** Where an item is on the canvas, in world units. */
    static Rectangle2D worldBox(int page, PdfDocument.Placement p) {
        Rectangle sheet = Paper.page(page);
        return new Rectangle2D.Double(sheet.x + p.x() * UNIT, sheet.y + p.y() * UNIT, p.width() * UNIT, p.height() * UNIT);
    }

    /** A world point in the points of sheet {@code page}. */
    private static float[] toPoints(int page, Point2D world) {
        Rectangle sheet = Paper.page(page);
        return new float[]{(float) ((world.getX() - sheet.x) / UNIT), (float) ((world.getY() - sheet.y) / UNIT)};
    }

    /** The sheet nearest a world point (the one above, in a gap), among those the canvas shows. */
    private int sheetNear(Point2D world) {
        return Paper.pageFor(new Rectangle((int) world.getX(), (int) world.getY(), 0, 0), Math.max(1, host.pageCount()));
    }

    private Hit hitAt(Point2D world) {
        PdfDocument document = controller.documentSnapshot();
        for (int page = 0; page < document.pageCount(); page++) {
            List<PdfDocument.Placement> items = document.placements(page);
            for (int i = items.size() - 1; i >= 0; i--) {
                if (worldBox(page, items.get(i)).contains(world)) {
                    return new Hit(page, items.get(i));
                }
            }
        }
        return null;
    }

    private boolean onResizeHandle(Rectangle2D box, Point2D world) {
        double reach = HANDLE / host.zoom();
        return world.getX() >= box.getMaxX() - reach && world.getY() >= box.getMaxY() - reach;
    }

    // ---------------------------------------------------------------- painting

    @Override
    public int pagesUsed() {
        PdfDocument document = controller.documentSnapshot();
        for (int page = document.pageCount() - 1; page >= 0; page--) {
            if (!document.placements(page).isEmpty()) {
                return page + 1;
            }
        }
        return 0;
    }

    @Override
    public void paint(Graphics2D world) {
        Graphics2D g = (Graphics2D) world.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        PdfDocument document = controller.documentSnapshot();
        PdfDocument.Placement selected = controller.selected();
        for (int page = 0; page < document.pageCount(); page++) {
            for (PdfDocument.Placement p : document.placements(page)) {
                Rectangle2D box = worldBox(page, p);
                BufferedImage picture = picture(p);
                Graphics2D item = (Graphics2D) g.create();
                item.translate(box.getX(), box.getY());
                item.scale(box.getWidth() / picture.getWidth(), box.getHeight() / picture.getHeight());
                item.drawImage(picture, 0, 0, null);
                item.dispose();
                if (selected != null && selected.id().equals(p.id()) && page == controller.currentPage()) {
                    paintSelection(g, box);
                }
            }
        }
        g.dispose();
    }

    /** The item drawn as in the PDF, at a few pixels per point; kept until the item changes. */
    private BufferedImage picture(PdfDocument.Placement p) {
        return pictures.computeIfAbsent(p, item -> {
            int w = Math.min(2400, Math.max(8, Math.round(item.width() * RENDER_SCALE)));
            int h = Math.min(2400, Math.max(8, Math.round(item.height() * RENDER_SCALE)));
            return item.isNote() ? NoteRenderer.render(item, w, h) : MaterialRenderer.render(item.material(), w, h);
        });
    }

    private void paintSelection(Graphics2D g, Rectangle2D box) {
        double unit = 1 / host.zoom();
        Graphics2D s = (Graphics2D) g.create();
        s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        s.setColor(SELECTION);
        s.setStroke(new BasicStroke((float) (1.5 * unit)));
        s.draw(box);
        double size = HANDLE * unit;
        Rectangle2D handle = new Rectangle2D.Double(box.getMaxX() - size, box.getMaxY() - size, size, size);
        s.fill(handle);
        s.dispose();
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean press(Point2D world, int clickCount) {
        drag = null;
        Hit hit = hitAt(world);
        if (hit == null) {
            return false;
        }
        select(hit);
        Rectangle2D box = worldBox(hit.page(), hit.item());
        if (clickCount == 2 && hit.item().isNote()) {
            SwingUtilities.invokeLater(this::editSelectedNote);
        } else {
            drag = new Drag(world, hit.item(), box, onResizeHandle(box, world));
        }
        host.changed();
        return true;
    }

    @Override
    public void drag(Point2D world) {
        if (drag == null || controller.selected() == null) {
            return;
        }
        if (!drag.started) {
            if (world.distance(drag.start) * host.zoom() < 2) {
                return;  // a click, not a drag
            }
            host.beforeChange();
            drag.started = true;
        }
        PdfDocument.Placement item = drag.item;
        if (drag.resizing) {
            float dx = (float) ((world.getX() - drag.start.getX()) / UNIT);
            float dy = (float) ((world.getY() - drag.start.getY()) / UNIT);
            controller.transform(item.x(), item.y(), item.width() + dx, item.height() + dy);
        } else {
            int page = sheetNear(world);
            if (page != controller.currentPage()) {
                controller.ensurePages(page + 1);
                controller.moveSelectedToPage(page);
            }
            float[] corner = toPoints(page, new Point2D.Double(world.getX() - drag.grabX, world.getY() - drag.grabY));
            controller.transform(corner[0], corner[1], item.width(), item.height());
        }
        host.changed();
    }

    @Override
    public void release() {
        drag = null;
    }

    @Override
    public Cursor cursorAt(Point2D world) {
        Hit hit = hitAt(world);
        if (hit == null) {
            return null;
        }
        boolean corner = isSelected(hit) && onResizeHandle(worldBox(hit.page(), hit.item()), world);
        return Cursor.getPredefinedCursor(corner ? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR);
    }

    @Override
    public boolean showMenu(Component owner, Point screen, Point2D world) {
        Hit hit = hitAt(world);
        if (hit == null) {
            return false;
        }
        select(hit);
        host.changed();
        JPopupMenu menu = new JPopupMenu();
        PdfDocument.Placement item = hit.item();
        if (item.isNote()) {
            menuItem(menu, "Edit Note...", this::editSelectedNote);
        } else if (canSplit()) {
            menuItem(menu, "Separate Formula and Result", this::split);
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        menuItem(menu, "Bring to Front", () -> arrange(2));
        menuItem(menu, "Bring Forward", () -> arrange(1));
        menuItem(menu, "Send Backward", () -> arrange(-1));
        menuItem(menu, "Send to Back", () -> arrange(-2));
        JMenu move = new JMenu("Move to Page");
        move.setFont(Fonts.ui(Font.PLAIN, 13f));
        for (int page = 0; page < Math.max(host.pageCount(), controller.pageCount()); page++) {
            int target = page;
            if (target != hit.page()) {
                menuItem(move.getPopupMenu(), "Page " + (target + 1), () -> moveTo(target));
            }
        }
        menu.add(move);
        menu.addSeparator();
        menuItem(menu, "Delete", this::deleteSelection);
        menu.show(owner, screen.x, screen.y);
        return true;
    }

    private static void menuItem(JPopupMenu menu, String title, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.setFont(Fonts.ui(Font.PLAIN, 13f));
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void select(Hit hit) {
        controller.switchPage(hit.page());
        controller.select(hit.item().id());
    }

    private boolean isSelected(Hit hit) {
        PdfDocument.Placement selected = controller.selected();
        return selected != null && selected.id().equals(hit.item().id()) && controller.currentPage() == hit.page();
    }

    // ---------------------------------------------------------------- selection and commands

    @Override
    public boolean hasSelection() {
        return controller.selected() != null;
    }

    @Override
    public boolean clearSelection() {
        boolean had = hasSelection();
        controller.clearSelection();
        return had;
    }

    /** True if the selected item is a calculation that can be split into its formula and its result. */
    public boolean canSplit() {
        PdfDocument.Placement p = controller.selected();
        return p != null && !p.isNote() && !p.material().isGraph() && p.material().hasResult();
    }

    @Override
    public void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        host.beforeChange();
        controller.deleteSelected();
        host.changed();
    }

    /** Changes the selected item's stacking: 2 to the front, 1 forward, -1 backward, -2 to the back. */
    public void arrange(int direction) {
        if (!hasSelection()) {
            return;
        }
        host.beforeChange();
        controller.arrange(direction);
        host.changed();
    }

    /** Splits the selected calculation into its formula and its result, side by side. */
    public void split() {
        if (!canSplit()) {
            return;
        }
        host.beforeChange();
        controller.splitSelected();
        host.changed();
    }

    private void moveTo(int page) {
        host.beforeChange();
        controller.ensurePages(page + 1);
        controller.moveSelectedToPage(page);
        host.changed();
    }

    /** Puts a library item on the sheet nearest {@code world}, centred there, selected. */
    public void insert(MathLibrary.Material material, Point2D world) {
        host.useSelectTool();
        host.beforeChange();
        int page = sheetNear(world);
        controller.ensurePages(page + 1);
        controller.switchPage(page);
        float w = material.isGraph() ? GRAPH_WIDTH : ITEM_WIDTH;
        float h = material.isGraph() ? GRAPH_HEIGHT : ITEM_HEIGHT;
        float[] at = toPoints(page, world);
        controller.place(material, at[0] - w / 2, at[1] - h / 2, w, h);
        host.changed();
    }

    /** Puts a library item in the middle of the view. */
    public void insert(MathLibrary.Material material) {
        insert(material, host.viewCenter());
    }

    @Override
    public boolean drop(String data, Point2D world) {
        MathLibrary.Material material = controller.library().find(data);
        if (material == null) {
            return false;
        }
        insert(material, world);
        return true;
    }

    /** Adds a text note in the middle of the view and opens it for editing. */
    public void addNote() {
        host.useSelectTool();
        host.beforeChange();
        Point2D centre = host.viewCenter();
        int page = sheetNear(centre);
        controller.ensurePages(page + 1);
        controller.switchPage(page);
        PdfDocument.Placement note = controller.addNote();
        float[] at = toPoints(page, centre);
        controller.transform(at[0] - note.width() / 2, at[1] - note.height() / 2, note.width(), note.height());
        host.changed();
        editNote(false);
    }

    /** Opens the selected note for editing. */
    public void editSelectedNote() {
        editNote(true);
    }

    private void editNote(boolean undoable) {
        PdfDocument.Placement note = controller.selected();
        if (note == null || !note.isNote()) {
            return;
        }
        List<PdfDocument.NoteSpan> spans = RichNoteEditor.edit(host.component(), note);
        if (spans == null) {
            return;
        }
        if (undoable) {
            host.beforeChange();
        }
        controller.updateRichNote(spans);
        host.changed();
    }

    /**
     * Lays out every library item on the paper, from the sheet in the middle of the view on, as one
     * undo step. Returns how many items were placed.
     */
    public int autoLayout() {
        if (controller.library().items().isEmpty()) {
            return 0;
        }
        host.beforeChange();
        int page = sheetNear(host.viewCenter());
        controller.ensurePages(page + 1);
        controller.switchPage(page);
        int count = controller.autoLayout();
        controller.clearSelection();
        host.changed();
        return count;
    }

    // ---------------------------------------------------------------- undo

    @Override
    public Object saveState() {
        return controller.documentSnapshot();
    }

    @Override
    public void restoreState(Object state) {
        controller.restore((PdfDocument) state);
        drag = null;
    }
}
