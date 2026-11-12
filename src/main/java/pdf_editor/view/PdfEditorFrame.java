package pdf_editor.view;

import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.MaterialRenderer;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Swing view for the PDF studio. All document mutations and export operations are delegated. */
public final class PdfEditorFrame extends JFrame {
    private final PdfEditorController controller;
    private final MathLibrary library;
    private final DefaultListModel<MathLibrary.Material> listModel = new DefaultListModel<>();
    private final JList<MathLibrary.Material> shelf = new JList<>(listModel);
    private final PdfPageCanvas canvas;
    private JScrollPane pageScroll;
    private final JLabel pageZoomLabel = new JLabel("100%");
    private final JSlider pageZoomSlider = new JSlider(35, 300, 100);
    private boolean syncingZoom;
    private final JPanel editor = new JPanel(new BorderLayout(12, 12));
    private static final Color BACKDROP = new Color(243, 247, 253);
    private static final Color INK = new Color(35, 52, 77);
    private static final Color MUTED = new Color(113, 131, 157);
    private final JPanel preview = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel pageLabel = new JLabel();
    private final JLabel status = new JLabel("Drag an item from the library onto the page.");
    private Runnable stopLibraryNotifications = () -> {};
    private int previewPage = 0;
    private List<BufferedImage> previewImages = new ArrayList<>();

    public PdfEditorFrame(PdfEditorController controller, BufferedImage sourceSnapshot) {
        super("Math Lab - PDF Editor");
        this.controller = java.util.Objects.requireNonNull(controller);
        this.library = controller.library();
        this.canvas = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
            @Override public void contextMenu(MouseEvent event) { showItemMenu(event); }
            @Override public void editNote() { PdfEditorFrame.this.editNote(); }
            @Override public void editFormula() { PdfEditorFrame.this.editFormula(); }
        });
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1120, 850); setMinimumSize(new Dimension(830, 650));
        setLocationRelativeTo(null);
        editor.setBackground(BACKDROP);
        editor.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setupShelf();
        RoundedPanel left = new RoundedPanel(new BorderLayout(0, 12), new Color(255, 255, 255, 238), 24);
        left.setBorder(BorderFactory.createEmptyBorder(18, 16, 16, 16));
        JLabel shelfTitle = new JLabel("MATERIAL LIBRARY");
        shelfTitle.setFont(Fonts.ui(Font.BOLD, 16f)); shelfTitle.setForeground(INK);
        JLabel hint = new JLabel("Drag a card onto the A4 page");
        hint.setFont(Fonts.ui(Font.PLAIN, 12f)); hint.setForeground(MUTED);
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        heading.add(shelfTitle, BorderLayout.NORTH);
        heading.add(hint, BorderLayout.SOUTH);
        left.add(heading, BorderLayout.NORTH);
        JScrollPane shelfScroll = glassScroll(shelf);
        shelfScroll.getViewport().setOpaque(false);
        if (sourceSnapshot == null) {
            left.add(shelfScroll, BorderLayout.CENTER);
        } else {
            JTabbedPane sources = new JTabbedPane();
            sources.addTab("Library", shelfScroll);
            CanvasRegionSelector selector = new CanvasRegionSelector(sourceSnapshot);
            JPanel sourceTab = new JPanel(new BorderLayout(0, 8));
            JPanel selectionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 4));
            JToggleButton rectangle = new JToggleButton("Rectangle", true);
            JToggleButton lasso = new JToggleButton("Lasso");
            ButtonGroup group = new ButtonGroup(); group.add(rectangle); group.add(lasso);
            rectangle.addActionListener(e -> selector.setFreehand(false));
            lasso.addActionListener(e -> selector.setFreehand(true));
            JButton add = button("Add Selection to Page", () -> {
                BufferedImage region = selector.selectedRegion();
                if (region == null) { status.setText("Select a region on Canvas first."); return; }
                MathLibrary.Material item = MathLibrary.region("Canvas Region", region);
                float ratio = (float) region.getHeight() / Math.max(1, region.getWidth());
                float w = Math.min(460, region.getWidth());
                float h = Math.min(650, w * ratio);
                controller.place(item, 55, 65, w, h);
                canvas.repaint();
                status.setText("Region added to Page " + (controller.currentPage() + 1) + ".");
            });
            selectionBar.add(rectangle); selectionBar.add(lasso);
            selectionBar.add(button("-", () -> selector.setZoom(selector.zoom() / 1.25)));
            selectionBar.add(button("+", () -> selector.setZoom(selector.zoom() * 1.25)));
            selectionBar.add(button("Fit", selector::fitToView));
            JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
            actionBar.add(add); actionBar.add(button("Clear", selector::clearSelection));
            JPanel controls = new JPanel(new BorderLayout());
            controls.add(selectionBar, BorderLayout.NORTH);
            controls.add(actionBar, BorderLayout.SOUTH);
            sourceTab.add(controls, BorderLayout.NORTH);
            JScrollPane sourceScroll = new JScrollPane(selector);
            selector.attachScrollPane(sourceScroll);
            sourceTab.add(sourceScroll, BorderLayout.CENTER);
            sources.addTab("Canvas", sourceTab);
            left.add(sources, BorderLayout.CENTER);
        }
        left.setPreferredSize(new Dimension(320, 600));
        editor.add(left, BorderLayout.WEST);
        RoundedPanel stage = new RoundedPanel(new BorderLayout(), new Color(233, 240, 250), 24);
        stage.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        // Center the sheet in the viewport when it fits. Scrollbars appear automatically when zoomed in.
        JPanel pageCenter = new JPanel(new GridBagLayout());
        pageCenter.setBackground(new Color(233, 240, 250));
        pageCenter.add(canvas, new GridBagConstraints());
        pageScroll = glassScroll(pageCenter);
        pageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(30);
        pageScroll.getHorizontalScrollBar().setUI(new GlassScrollBarUI());
        pageScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 9));
        pageScroll.getViewport().setBackground(new Color(233, 240, 250));
        // GridBagLayout's centered child is naturally sized to the page's preferred dimensions.
        stage.add(pageScroll, BorderLayout.CENTER);
        pageScroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) { changePageZoom(canvas.zoom() * Math.pow(1.12, -e.getPreciseWheelRotation())); e.consume(); }
        });
        canvas.addMouseWheelListener(e -> {
            if (e.isControlDown()) { changePageZoom(canvas.zoom() * Math.pow(1.12, -e.getPreciseWheelRotation())); e.consume(); }
        });
        editor.add(stage, BorderLayout.CENTER);
        JPanel toolbar = editorToolbar();
        editor.add(toolbar, BorderLayout.NORTH);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                toolbar.revalidate(); editor.revalidate();
            }
        });
        status.setFont(Fonts.ui(Font.PLAIN, 12f));
        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(2, 5, 0, 0));
        editor.add(status, BorderLayout.SOUTH);
        center.add(editor, "edit"); center.add(preview, "preview");
        setContentPane(center);
        stopLibraryNotifications = library.onChange(() -> SwingUtilities.invokeLater(this::refreshShelf));
        refreshShelf(); updatePageLabel();
    }
    @Override public void dispose() {
        stopLibraryNotifications.run();
        super.dispose();
    }

    private void setupShelf() {
        shelf.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shelf.setFixedCellHeight(112);
        shelf.setOpaque(false);
        shelf.setBackground(Color.WHITE);
        shelf.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        shelf.setCellRenderer((list, item, index, selected, focused) -> {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 9, 4));
            RoundedPanel card = new RoundedPanel(new BorderLayout(0, 7),
                    selected ? new Color(222, 235, 253) : new Color(247, 250, 254), 17);
            card.setBorder(BorderFactory.createEmptyBorder(10, 12, 9, 12));
            if (item != null) {
                JLabel title = new JLabel(item.title());
                title.setFont(Fonts.ui(Font.BOLD, 13f));
                title.setForeground(INK);
                JLabel type = new JLabel(item.isGraph() ? "GRAPH" : "FORMULA + RESULT");
                type.setFont(Fonts.ui(Font.BOLD, 10f));
                type.setForeground(new Color(60, 105, 181));
                JPanel heading = new JPanel(new BorderLayout(5, 0));
                heading.setOpaque(false);
                heading.add(title, BorderLayout.CENTER);
                heading.add(type, BorderLayout.EAST);
                card.add(heading, BorderLayout.NORTH);
                JLabel content = new JLabel(new ImageIcon(MaterialRenderer.render(item, 240, 62)));
                content.setHorizontalAlignment(SwingConstants.LEFT);
                card.add(content, BorderLayout.CENTER);
            }
            wrapper.add(card, BorderLayout.CENTER);
            return wrapper;
        });
        shelf.setDragEnabled(true);
        shelf.setTransferHandler(new TransferHandler() {
            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                MathLibrary.Material m = shelf.getSelectedValue();
                return m == null ? null : new StringSelection(m.id());
            }
            @Override public int getSourceActions(JComponent c) { return COPY; }
        });
    }
    private static JScrollPane glassScroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUI(new GlassScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }
    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;
        RoundedPanel(LayoutManager layout, Color fill, int arc) {
            super(layout); this.fill = fill; this.arc = arc; setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(new Color(208, 221, 238, 140));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    private static final class GlassScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(163, 185, 214, 175); trackColor = new Color(0, 0, 0, 0); }
        @Override protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }
        private JButton invisibleButton() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); b.setMinimumSize(new Dimension(0, 0)); b.setMaximumSize(new Dimension(0, 0)); return b; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
            if (bounds.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? new Color(115, 153, 204, 215) : new Color(160, 183, 212, 170));
            g2.fillRoundRect(bounds.x + 2, bounds.y + 3, Math.max(3, bounds.width - 4), Math.max(4, bounds.height - 6), 9, 9);
            g2.dispose();
        }
    }
    private void refreshShelf() {
        listModel.clear();
        for (MathLibrary.Material material : library.items()) listModel.addElement(material);
    }
    private JPanel editorToolbar() {
        JPanel tools = new JPanel(new CenteredWrapLayout(9, 8));
        tools.setOpaque(false);
        pageLabel.setFont(Fonts.ui(Font.BOLD, 14f));
        tools.add(button("< Page", () -> switchPage(controller.currentPage() - 1)));
        tools.add(pageLabel);
        tools.add(button("Page >", () -> switchPage(controller.currentPage() + 1)));
        tools.add(button("+ Page", () -> { controller.addPage(); updatePageLabel(); canvas.repaint(); }));
        tools.add(button("Delete Page", () -> {
            if (!controller.deletePage()) { messageGlass("Cannot delete page", "At least one page is required."); return; }
            updatePageLabel(); canvas.repaint();
        }));
        tools.add(button("Delete Item", () -> { controller.deleteSelected(); canvas.repaint(); }));
        tools.add(button("Undo", this::undo)); tools.add(button("Redo", this::redo));
        tools.add(menuButton("Arrange", arrangeMenu()));
        tools.add(button("+ Note", this::addNote));
        tools.add(button("Auto Layout", this::autoLayout));
        tools.add(button("−", () -> changePageZoom(canvas.zoom() / 1.15)));
        pageZoomSlider.setPreferredSize(new Dimension(112, 29));
        pageZoomSlider.setOpaque(false);
        pageZoomSlider.setToolTipText("A4 zoom (view only; PDF dimensions stay unchanged)");
        pageZoomSlider.addChangeListener(e -> { if (!syncingZoom) changePageZoom(pageZoomSlider.getValue() / 100.0); });
        tools.add(pageZoomSlider);
        pageZoomLabel.setFont(Fonts.ui(Font.BOLD, 12f));
        pageZoomLabel.setForeground(INK);
        tools.add(pageZoomLabel);
        tools.add(button("+", () -> changePageZoom(canvas.zoom() * 1.15)));
        tools.add(button("Fit", this::fitPage));
        tools.add(button("100%", () -> changePageZoom(1.0)));
        tools.add(button("Done / Preview", this::showPreview));
        return tools;
    }
    private void fitPage() {
        if (pageScroll == null) return;
        Dimension extent = pageScroll.getViewport().getExtentSize();
        changePageZoom(canvas.fitZoom(extent.width, extent.height));
    }
    private void changePageZoom(double requested) {
        // Preserve the current viewport center during zoom, just like a word processor.
        if (pageScroll == null) return;
        JViewport viewport = pageScroll.getViewport();
        Dimension extent = viewport.getExtentSize();
        Point before = viewport.getViewPosition();
        double oldZoom = canvas.zoom();
        double zoom = canvas.setZoom(requested);
        syncingZoom = true;
        pageZoomSlider.setValue((int) Math.round(zoom * 100));
        syncingZoom = false;
        pageZoomLabel.setText(Math.round(zoom * 100) + "%");
        // Defer repositioning until scroll view layout knows its new size.
        SwingUtilities.invokeLater(() -> {
            Dimension size = viewport.getViewSize();
            int x = (int)Math.round((before.x + extent.width / 2.0) * zoom / oldZoom - extent.width / 2.0);
            int y = (int)Math.round((before.y + extent.height / 2.0) * zoom / oldZoom - extent.height / 2.0);
            viewport.setViewPosition(new Point(Math.max(0, Math.min(x, Math.max(0, size.width - extent.width))),
                    Math.max(0, Math.min(y, Math.max(0, size.height - extent.height)))));
        });
    }
    private void autoLayout() {
        int count = controller.autoLayout();
        updatePageLabel(); canvas.repaint();
        status.setText(count == 0 ? "No library materials to import."
                : "Imported " + count + " materials. Undo reverses the layout in one step.");
    }
    private JButton menuButton(String title, JPopupMenu menu) {
        JButton button = new GlassButton(title, 34);
        button.addActionListener(e -> menu.show(button, 0, button.getHeight()));
        return button;
    }
    private static void menuItem(JPopupMenu menu, String title, Runnable action) {
        JMenuItem item = new JMenuItem(title);
        item.setOpaque(true);
        item.setBackground(Color.WHITE);
        item.setForeground(INK);
        item.setBorder(BorderFactory.createEmptyBorder(9, 17, 9, 17));
        item.setFont(Fonts.ui(Font.PLAIN, 13f));
        item.addActionListener(e -> action.run()); menu.add(item);
    }
    private static JPopupMenu styledPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Color.WHITE);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(204, 217, 235), 1, true),
                BorderFactory.createEmptyBorder(6, 5, 6, 5)));
        return menu;
    }
    private JPopupMenu arrangeMenu() {
        JPopupMenu menu = styledPopup();
        menuItem(menu, "Bring to Front", () -> arrange(2));
        menuItem(menu, "Bring Forward", () -> arrange(1));
        menu.addSeparator();
        menuItem(menu, "Send Backward", () -> arrange(-1));
        menuItem(menu, "Send to Back", () -> arrange(-2));
        return menu;
    }
    private void arrange(int direction) {
        if (controller.selected() == null) { status.setText("Select an item before arranging."); return; }
        controller.arrange(direction); canvas.repaint();
    }
    private void addNote() {
        controller.addNote(); canvas.repaint(); editNote();
    }
    private void editNote() {
        PdfDocument.Placement note = controller.selected();
        if (note == null || !note.isNote()) { status.setText("Select a note first."); return; }
        List<PdfDocument.NoteSpan> spans = RichNoteEditor.edit(this, note);
        if (spans != null) { controller.updateRichNote(spans); canvas.repaint(); }
    }
    private void editFormula() {
        PdfDocument.Placement item = controller.selected();
        if (item == null || item.isNote() || item.material().isGraph()) return;
        FormulaEditDialog.Result edited = FormulaEditDialog.show(this, item.material());
        if (edited != null && controller.editSelectedFormula(edited.formula(), edited.result())) {
            canvas.repaint();
            status.setText("Formula updated. Undo restores the previous version.");
        }
    }
    private void splitSelected() {
        if (!controller.splitSelected()) { status.setText("Select a combined formula + result item first."); return; }
        canvas.repaint();
    }
    private void showItemMenu(MouseEvent e) {
        PdfDocument.Placement selected = controller.selected();
        if (selected == null) return;
        JPopupMenu menu = styledPopup();
        if (selected.isNote()) menuItem(menu, "Edit Note...", this::editNote);
        else if (!selected.material().isGraph()) menuItem(menu, "Edit Formula / LaTeX...", this::editFormula);
        else if (selected.material().hasResult() && !selected.material().isGraph())
            menuItem(menu, "Separate Formula and Result", this::splitSelected);
        if (menu.getComponentCount() > 0) menu.addSeparator();
        menuItem(menu, "Bring to Front", () -> arrange(2));
        menuItem(menu, "Bring Forward", () -> arrange(1));
        menuItem(menu, "Send Backward", () -> arrange(-1));
        menuItem(menu, "Send to Back", () -> arrange(-2));
        JMenu move = new JMenu("Move to Page");
        move.setFont(Fonts.ui(Font.PLAIN, 13f));
        move.setForeground(INK);
        move.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 10));
        for (int target=0; target<controller.pageCount(); target++) {
            int index = target;
            if (index != controller.currentPage())
                menuItem(move.getPopupMenu(), "Page " + (index + 1), () -> moveSelectedToPage(index));
        }
        menuItem(move.getPopupMenu(), "New Page", () -> moveSelectedToPage(-1));
        menu.add(move);
        menu.addSeparator();
        menuItem(menu, "Delete Item", () -> { controller.deleteSelected(); canvas.repaint(); });
        menu.show(canvas, e.getX(), e.getY());
    }
    private void moveSelectedToPage(int target) {
        if (controller.moveSelectedToPage(target)) {
            updatePageLabel(); canvas.repaint();
            status.setText("Moved item to Page " + (controller.currentPage() + 1) + ".");
        }
    }
    private JButton button(String title, Runnable action) {
        JButton b = new GlassButton(title, 34);
        b.addActionListener(e -> action.run()); return b;
    }
    private void switchPage(int page) { controller.switchPage(page); updatePageLabel(); canvas.repaint(); }
    private void updatePageLabel() {
        pageLabel.setText("Page " + (controller.currentPage() + 1) + " / " + controller.pageCount());
    }
    private void undo() { if (controller.undo()) { updatePageLabel(); canvas.repaint(); } }
    private void redo() { if (controller.redo()) { updatePageLabel(); canvas.repaint(); } }
    private void showPreview() {
        try {
            previewImages = new ArrayList<>();
            for (PdfPreviewPage page : controller.preview()) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(page.png()));
                if (image == null) throw new IOException("Invalid preview image");
                previewImages.add(image);
            }
            previewPage = 0; buildPreview(); cards.show(center, "preview");
        } catch (IOException | RuntimeException e) { error("Preview failed", e); }
    }
    private void buildPreview() {
        preview.removeAll();
        JPanel bar = new JPanel(new CenteredWrapLayout(9, 8));
        bar.setBackground(BACKDROP);
        bar.add(button("Back to Edit", () -> cards.show(center, "edit")));
        bar.add(button("<", () -> { previewPage = Math.max(0, previewPage - 1); buildPreview(); }));
        bar.add(new JLabel("Preview " + (previewPage + 1) + " / " + previewImages.size()));
        bar.add(button(">", () -> { previewPage = Math.min(previewImages.size()-1, previewPage+1); buildPreview(); }));
        bar.add(button("Export PDF...", this::export));
        preview.add(bar, BorderLayout.NORTH);
        JLabel image = new JLabel(new ImageIcon(previewImages.get(previewPage)));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        preview.add(glassScroll(image), BorderLayout.CENTER);
        preview.revalidate(); preview.repaint();
    }
    private void export() {
        FileDialog dialog = new FileDialog(this, "Save PDF", FileDialog.SAVE);
        dialog.setFile("mathlab-notes.pdf");
        dialog.setFilenameFilter((directory, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"));
        dialog.setVisible(true);
        String name = dialog.getFile();
        if (name == null) return;
        java.io.File output = new java.io.File(dialog.getDirectory(), name);
        if (!output.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
            output = new java.io.File(output.getPath() + ".pdf");
        if (output.exists() && !confirmGlass("Replace existing PDF?",
                "A file with this name already exists. Replace it?")) return;
        try {
            controller.export(output.toPath());
            messageGlass("PDF saved", "Saved to: " + output.getAbsolutePath());
        } catch (IOException | RuntimeException e) { error("Export failed", e); }
    }
    private void error(String title, Exception e) {
        messageGlass(title, e.getMessage() == null ? "An unexpected error occurred." : e.getMessage());
    }
    private void messageGlass(String title, String message) {
        glassDialog(title, message, false);
    }
    private boolean confirmGlass(String title, String message) {
        return glassDialog(title, message, true);
    }
    private boolean glassDialog(String title, String message, boolean confirmation) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        RoundedPanel panel = new RoundedPanel(new BorderLayout(0, 16), Color.WHITE, 24);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
        JLabel heading = new JLabel(title);
        heading.setFont(Fonts.ui(Font.BOLD, 19f));
        heading.setForeground(INK);
        JLabel body = new JLabel("<html><div style='width:360px'>" + escapeHtml(message) + "</div></html>");
        body.setFont(Fonts.ui(Font.PLAIN, 13f));
        body.setForeground(MUTED);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        final boolean[] accepted = {false};
        if (confirmation) {
            JButton cancel = button("Cancel", dialog::dispose);
            actions.add(cancel);
        }
        JButton ok = button(confirmation ? "Replace" : "OK", () -> {
            accepted[0] = true;
            dialog.dispose();
        });
        actions.add(ok);
        panel.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, 175));
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setVisible(true);
        return accepted[0];
    }
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }


    private static final class CenteredWrapLayout implements LayoutManager {
        private final int gap;
        private final int verticalGap;
        CenteredWrapLayout(int gap, int verticalGap) {
            this.gap = gap;
            this.verticalGap = verticalGap;
        }
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int available = parent.getWidth() > 0 ? parent.getWidth() - insets.left - insets.right
                        : 1024;
                available = Math.max(1, available);
                int rowWidth = 0, rowHeight = 0, totalHeight = 0, maxWidth = 0;
                for (Component c : parent.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (rowWidth > 0 && rowWidth + gap + d.width > available) {
                        totalHeight += rowHeight + verticalGap;
                        maxWidth = Math.max(maxWidth, rowWidth);
                        rowWidth = 0; rowHeight = 0;
                    }
                    if (rowWidth > 0) rowWidth += gap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                totalHeight += rowHeight;
                maxWidth = Math.max(maxWidth, rowWidth);
                return new Dimension(maxWidth + insets.left + insets.right,
                        totalHeight + insets.top + insets.bottom + verticalGap * 2);
            }
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
        @Override public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int available = Math.max(1, parent.getWidth() - insets.left - insets.right);
                java.util.List<Component> row = new java.util.ArrayList<>();
                int width = 0, height = 0, y = insets.top + verticalGap;
                for (Component c : parent.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    if (!row.isEmpty() && width + gap + d.width > available) {
                        placeRow(row, parent, insets.left, available, y, width, height);
                        y += height + verticalGap;
                        row.clear(); width = 0; height = 0;
                    }
                    if (!row.isEmpty()) width += gap;
                    row.add(c); width += d.width; height = Math.max(height, d.height);
                }
                if (!row.isEmpty()) placeRow(row, parent, insets.left, available, y, width, height);
            }
        }
        private void placeRow(java.util.List<Component> row, Container parent, int left,
                              int available, int y, int width, int height) {
            int x = left + Math.max(0, (available - width) / 2);
            for (Component c : row) {
                Dimension d = c.getPreferredSize();
                c.setBounds(x, y + (height - d.height) / 2, d.width, d.height);
                x += d.width + gap;
            }
        }
    }
}
