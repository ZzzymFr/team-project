package view.board.doc;

import history.adapter.HistoryMaterialMapper;
import interface_adapter.history.HistoryItem;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.MaterialRenderer;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.view.FormulaEditDialog;
import pdf_editor.view.PdfPageCanvas;
import view.board.BoardTheme;
import view.board.Icons;
import view.board.PdfWorkspacePort;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Word-style presentation adapter for the real PDF document use cases.
 * This replaces the *placeholder* page from the one-board prototype, not its visual concept.
 * All mutations and export flow through PdfEditorController; no persistence is done here.
 */
public final class DocEditorPanel extends JPanel implements PdfWorkspacePort {
    private static final Color DIVIDER = new Color(0, 0, 0, 24);
    private static final Color MUTED = new Color(110, 112, 122);
    static final Color DESK_COLOR = new Color(0xE9E9EF);
    private static final Color DESK = DESK_COLOR;
    private static final int MAX_ZOOM = 300;
    private static final int MIN_ZOOM = 35;

    private final PdfEditorController controller;
    private final MathLibrary library;
    private final PdfPageCanvas paper;
    private final JScrollPane pageScroll;
    private final JPanel ribbonHolder = new JPanel(new BorderLayout());
    private final JPanel libraryPanel = new JPanel(new BorderLayout());
    private final DefaultListModel<MathLibrary.Material> libraryModel = new DefaultListModel<>();
    private final JList<MathLibrary.Material> libraryList = new JList<>(libraryModel);
    private final JLabel pageCount = new JLabel();
    private final JLabel zoomValue = new JLabel("100%");
    private final JLabel title = new JLabel("Untitled document");
    private final JSlider zoomSlider = new JSlider(MIN_ZOOM, MAX_ZOOM, 100);
    private final JPanel workspace = new JPanel(new BorderLayout());
    private final java.util.List<Tab> tabs = new java.util.ArrayList<>();
    private final Runnable detachLibraryListener;
    private Supplier<BufferedImage> canvasCapture = () -> null;
    private Runnable regionCapture = this::insertCanvasInk;
    private boolean updatingZoom;
    private Runnable toggleFullscreen = () -> { };
    public void setFullscreenAction(Runnable callback) { toggleFullscreen = Objects.requireNonNull(callback); }


    public DocEditorPanel(PdfEditorController controller) {
        super(new BorderLayout());
        this.controller = Objects.requireNonNull(controller);
        this.library = controller.library();
        setBackground(DESK);
        paper = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
            @Override public void contextMenu(MouseEvent e) { contextMenu(e); }
            @Override public void editNote() { DocEditorPanel.this.editNote(); }
            @Override public void editFormula() { DocEditorPanel.this.editFormula(); }
            @Override public void typeInDocument() { DocEditorPanel.this.typeOnPage(); }
        });
        paper.setBackground(DESK);
        pageScroll = new JScrollPane(new CenteredDesk(paper));
        pageScroll.setBorder(null);
        pageScroll.getViewport().setBackground(DESK);
        pageScroll.getVerticalScrollBar().setUnitIncrement(22);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(22);
        pageScroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) { zoom(paper.adjustZoom(e.getPreciseWheelRotation())); e.consume(); }
        });
        workspace.setBackground(DESK);
        workspace.add(pageScroll, BorderLayout.CENTER);
        // A decorative ruler misrepresented measurements and did not scroll with the page.
        // Keep the desk clean until a real, scroll-synchronised editing ruler is added.
        setupLibrary();
        workspace.add(libraryPanel, BorderLayout.WEST);
        libraryPanel.setVisible(false);
        add(workspace, BorderLayout.CENTER);
        add(buildHeader(), BorderLayout.NORTH);
        add(buildStatus(), BorderLayout.SOUTH);
        detachLibraryListener = library.onChange(() -> SwingUtilities.invokeLater(this::refreshLibrary));
        refreshLibrary();
        updateStatus();
        tabs.get(0).select();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                ribbonHolder.revalidate();
            }
        });
    }

    /** Canvas ink is captured only on demand, never from an outdated opening screenshot. */
    public void setCanvasCapture(Supplier<BufferedImage> capture) {
        canvasCapture = Objects.requireNonNull(capture);
    }
    /** Request a temporary screenshot selection on the visible Canvas. */
    public void setRegionCapture(Runnable command) { regionCapture = Objects.requireNonNull(command); }
    public void showLibrary() { libraryPanel.setVisible(true); workspace.revalidate(); workspace.repaint(); }
    public void close() { detachLibraryListener.run(); }
    public PdfPageCanvas pageView() { return paper; }
    public PdfEditorController documentController() { return controller; }
    public boolean isLibraryVisible() { return libraryPanel.isVisible(); }

    /** Floating Apple-like controls: rounded capsules rather than full-width white strips. */
    private static final class Capsule extends JPanel {
        Capsule(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0,0,0,12));
            g2.fillRoundRect(1, 3, getWidth()-2, getHeight()-3, 28, 28);
            g2.setColor(new Color(255,255,255,252));
            g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-3, 28, 28);
            g2.setColor(new Color(210,216,227));
            g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-3, 28, 28);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private JComponent buildHeader() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(DESK);
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 9));
        nameRow.setOpaque(false);
        nameRow.setBorder(BorderFactory.createEmptyBorder(BoardTheme.TOP + 2, 10, 1, 0));
        Capsule document = new Capsule(new FlowLayout(FlowLayout.LEFT, 8, 2));
        title.setFont(BoardTheme.font(14f));
        document.add(DocumentGlyphs.component(23, Icons::document));
        document.add(title);
        nameRow.add(document);
        top.add(nameRow, BorderLayout.NORTH);

        JPanel tabsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 7, 3));
        tabsRow.setOpaque(false);
        JPanel names = new Capsule(new WrapLayout(FlowLayout.CENTER, 2, 1));
        tabs.add(new Tab("Home", homeRibbon()));
        tabs.add(new Tab("Insert", insertRibbon()));
        tabs.add(new Tab("Layout", layoutRibbon()));
        tabs.add(new Tab("View", viewRibbon()));
        for (Tab tab : tabs) names.add(tab.button);
        tabsRow.add(names);
        JButton export = new RoundedActionButton("Export PDF", this::export, true);
        export.setToolTipText("Save your document as a PDF file");
        tabsRow.add(export);
        top.add(tabsRow, BorderLayout.CENTER);
        ribbonHolder.setOpaque(false);
        ribbonHolder.setBorder(BorderFactory.createEmptyBorder(3, 9, 5, 9));
        top.add(ribbonHolder, BorderLayout.SOUTH);
        return top;
    }

    private JPanel ribbon() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return row;
    }
    private RibbonButton command(JPanel row, RibbonButton b, Runnable action) {
        row.add(b.onClick(action));
        return b;
    }
    private JPanel homeRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::comment, "Type"), this::typeOnPage);
        command(row, RibbonButton.icon(Icons::undo, "Undo"), this::undo);
        command(row, RibbonButton.icon(DocIcons::redo, "Redo"), this::redo);
        command(row, RibbonButton.letter("B", Font.BOLD, false, false, "Bold selected note"), () -> setNoteStyle("bold"));
        command(row, RibbonButton.letter("I", Font.ITALIC, false, false, "Italic selected note"), () -> setNoteStyle("italic"));
        command(row, RibbonButton.letter("U", Font.PLAIN, true, false, "Underline selected note"), () -> setNoteStyle("underline"));
        JComboBox<Integer> sizes = new JComboBox<>(new Integer[] {8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 60, 72});
        sizes.setSelectedItem(16);
        sizes.setToolTipText("Font size — applies to highlighted text or the selected text box");
        sizes.setFont(BoardTheme.font(12f));
        sizes.setFocusable(false);
        sizes.addActionListener(e -> {
            Integer size = (Integer) sizes.getSelectedItem();
            if (size != null) applyNoteFontSize(size);
        });
        row.add(sizes);
        JComboBox<String> families = new JComboBox<>(new String[]{"Times New Roman", "Arial", "Calibri", "Georgia", "Courier New", "Segoe UI"});
        families.setToolTipText("Font family for document text");
        families.setFont(BoardTheme.font(12f));
        families.setFocusable(false);
        families.addActionListener(e -> {
            String chosen = (String) families.getSelectedItem();
            if (chosen == null) return;
            if (paper.isEditingNote()) paper.setInlineFontFamily(chosen);
            else controller.changeSelectedNoteFont(chosen);
        });
        row.add(families);
        command(row, RibbonButton.labeled(DocIcons::formula, "Edit math"), this::editFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Edit note"), this::editNote);
        return row;
    }
    private JPanel insertRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::formula, "Formula"), this::insertFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Text box"), this::addNote);
        command(row, RibbonButton.labeled(DocIcons::image, "Capture area"), () -> regionCapture.run());
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "New page"), this::addPage);
        command(row, RibbonButton.labeled(Icons::library, "Library"), this::toggleLibrary);
        return row;
    }
    private JPanel layoutRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "Add page"), this::addPage);
        command(row, RibbonButton.labeled(DocIcons::deletePage, "Delete page"), this::deletePage);
        command(row, RibbonButton.labeled(DocIcons.align(0), "Auto layout"), this::autoLayout);
        command(row, RibbonButton.labeled(DocIcons::movePage, "Move page"), this::moveToPageMenu);
        command(row, RibbonButton.letter("↑", Font.PLAIN, false, false, "Bring selected object forward one layer"), () -> arrange(1));
        command(row, RibbonButton.letter("↓", Font.PLAIN, false, false, "Send selected object backward one layer"), () -> arrange(-1));
        command(row, RibbonButton.labeled(DocIcons::layers, "Arrange…"), this::arrangeMenu);
        return row;
    }
    private JPanel viewRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(Icons::preview, "Preview"), this::preview);
        command(row, RibbonButton.labeled(Icons::fullScreen, "Full screen"), () -> toggleFullscreen.run());
        command(row, RibbonButton.labeled(DocIcons::fitPage, "Fit page"), this::fitWhenOpened);
        command(row, RibbonButton.letter("−", Font.PLAIN, false, false, "Zoom out"), () -> zoom(paper.zoom() / 1.15));
        command(row, RibbonButton.letter("+", Font.PLAIN, false, false, "Zoom in"), () -> zoom(paper.zoom() * 1.15));
        return row;
    }
    private JComponent buildStatus() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(DESK);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        pageCount.setFont(BoardTheme.font(12f)); pageCount.setForeground(MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); left.setOpaque(false);
        left.add(flatButton("‹", () -> changePage(controller.currentPage() - 1)));
        left.add(pageCount);
        left.add(flatButton("›", () -> changePage(controller.currentPage() + 1)));
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));right.setOpaque(false);
        right.add(flatButton("−", () -> zoom(paper.zoom() / 1.15)));
        zoomSlider.setPreferredSize(new Dimension(85, 24));
        zoomSlider.setOpaque(false);
        zoomSlider.addChangeListener(e -> { if (!updatingZoom) zoom(zoomSlider.getValue() / 100.0); });
        right.add(zoomSlider);
        zoomValue.setForeground(MUTED);zoomValue.setFont(BoardTheme.font(12f));right.add(zoomValue);
        right.add(flatButton("+", () -> zoom(paper.zoom() * 1.15)));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }
    private JButton flatButton(String text, Runnable task) {
        return new RoundedActionButton(text, task, false);
    }
    /** Readable rounded buttons with a real opaque fill; unlike platform LAF translucent defaults. */
    private static final class RoundedActionButton extends JButton {
        private final boolean primary;
        private boolean over;
        RoundedActionButton(String text, Runnable action, boolean primary) {
            super(text);this.primary = primary;
            setFont(BoardTheme.font(12f));
            setFocusable(false);setContentAreaFilled(false);setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            setForeground(primary ? Color.WHITE : new Color(42, 57, 79));
            addActionListener(e -> action.run());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {over = true;repaint();}
                @Override public void mouseExited(MouseEvent e) {over = false;repaint();}
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color top = primary ? (over ? new Color(41, 143, 255) : new Color(0, 122, 255))
                    : (over ? new Color(228, 237, 249, 252) : new Color(247, 249, 253, 242));
            Color end = primary ? new Color(0, 93, 216) : new Color(239, 243, 249);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, end));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 17, 17);
            g2.setColor(primary ? new Color(255, 255, 255, 95) : new Color(189, 198, 213, 145));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 17, 17);
            g2.dispose();super.paintComponent(g);
        }
    }
    private void setupLibrary() {
        libraryPanel.setBackground(Color.WHITE);
        libraryPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DIVIDER));
        libraryPanel.setPreferredSize(new Dimension(188, 0));
        JPanel header = new JPanel(new BorderLayout());header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 9, 6, 6));
        JLabel name = new JLabel("Library");name.setFont(BoardTheme.font(14f));
        header.add(name, BorderLayout.CENTER);
        JPanel libraryActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        libraryActions.setOpaque(false);
        // Plain text (no platform bullet/leading icon) keeps the compact Library header clean.
        JButton undoDelete = new JButton("Undo");
        undoDelete.setFont(BoardTheme.font(11f));
        undoDelete.setOpaque(false);
        undoDelete.setContentAreaFilled(false);
        undoDelete.setFocusPainted(false);
        undoDelete.setBorderPainted(false);
        undoDelete.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        undoDelete.addActionListener(e -> library.undoDelete());
        libraryActions.add(undoDelete);
        JButton close = new LibraryHeaderCloseButton(this::toggleLibrary);
        libraryActions.add(close);
        header.add(libraryActions, BorderLayout.EAST);
        libraryPanel.add(header, BorderLayout.NORTH);
        libraryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libraryList.setFixedCellHeight(76);
        libraryList.setFont(BoardTheme.font(12f));
        libraryList.setCellRenderer((list, material, index, selected, focus) -> {
            JPanel card = new JPanel(new BorderLayout(0, 3));
            card.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 6));
            card.setBackground(selected ? new Color(0xDCEAFE) : Color.WHITE);
            if (material != null) {
                JLabel label = new JLabel(material.isGraph() ? "Graph" : material.title());
                label.setFont(BoardTheme.font(11f));
                JPanel cardTitle = new JPanel(new BorderLayout());
                cardTitle.setOpaque(false);
                cardTitle.add(label, BorderLayout.CENTER);
                JLabel delete = new JLabel("×", SwingConstants.CENTER);
                delete.setForeground(new Color(110, 118, 132));
                delete.setFont(BoardTheme.font(16f));
                delete.setPreferredSize(new Dimension(22, 20));
                delete.setToolTipText("Remove this item");
                cardTitle.add(delete, BorderLayout.EAST);
                card.add(cardTitle, BorderLayout.NORTH);
                try { card.add(new JLabel(new ImageIcon(MaterialRenderer.render(material, 145, 42))), BorderLayout.CENTER); }
                catch (RuntimeException bad) { card.add(new JLabel(material.formula()), BorderLayout.CENTER); }
            }
            return card;
        });
        libraryList.setDragEnabled(!GraphicsEnvironment.isHeadless());
        libraryList.setTransferHandler(new TransferHandler() {
            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent component) {
                MathLibrary.Material chosen = libraryList.getSelectedValue();
                return chosen == null ? null : new StringSelection(chosen.id());
            }
            @Override public int getSourceActions(JComponent component) { return COPY; }
        });
        libraryList.addMouseListener(new java.awt.event.MouseAdapter() {
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                libraryList.setSelectedIndex(libraryList.locationToIndex(e.getPoint()));
                JPopupMenu menu = new JPopupMenu();
                item(menu, "Add to PDF", () -> {
                    MathLibrary.Material selected = libraryList.getSelectedValue();
                    if (selected != null) addMaterial(selected, 85, 110);
                });
                item(menu, "Delete from Library", DocEditorPanel.this::deleteLibraryItem);
                item(menu, "Undo Library delete", library::undoDelete);
                menu.show(libraryList, e.getX(), e.getY());
            }
            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && libraryDeleteHit(e.getPoint())) {
                    deleteLibraryItem();
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    MathLibrary.Material chosen = libraryList.getSelectedValue();
                    if (chosen != null) addMaterial(chosen, 85, 110);
                }
            }
        });
        JScrollPane scroll = new JScrollPane(libraryList);scroll.setBorder(null);
        libraryPanel.add(scroll, BorderLayout.CENTER);
        JLabel help = new JLabel("Drag to page or double-click");
        help.setFont(BoardTheme.font(10f)); help.setForeground(MUTED);
        libraryPanel.add(help, BorderLayout.SOUTH);
    }
    /** The renderer is not a live component: route card × clicks through the JList. */
    boolean libraryDeleteHit(Point at) {
        int index = libraryList.locationToIndex(at);
        if (index < 0) return false;
        Rectangle bounds = libraryList.getCellBounds(index, index);
        return LibraryCardHitTarget.deleteClicked(bounds, at);
    }
    private void refreshLibrary() {
        libraryModel.clear();for (MathLibrary.Material material : library.items()) libraryModel.addElement(material);
    }
    private void deleteLibraryItem() {
        MathLibrary.Material selected = libraryList.getSelectedValue();
        if (selected != null) library.delete(selected.id());
    }
    private void toggleLibrary() { libraryPanel.setVisible(!libraryPanel.isVisible()); workspace.revalidate();workspace.repaint(); }
    private void updateStatus() {
        pageCount.setText("Page " + (controller.currentPage() + 1) + " of " + controller.pageCount());
        paper.repaint();
    }
    private void changePage(int target) {paper.finishInlineEdit();controller.switchPage(target);updateStatus();}
    private void zoom(double z) {
        double actual = paper.setZoom(z);
        updatingZoom = true;
        try {zoomSlider.setValue((int) Math.round(100 * actual));zoomValue.setText((int) Math.round(100 * actual) + "%");}
        finally {updatingZoom = false;}
        // CenteredDesk computes its preferred size from the zoomed page; invalidate
        // the viewport view too, otherwise its GridBag positions the new page at the
        // old (100%) dimensions and it looks left aligned at Fit Page.
        JComponent desk = (JComponent) pageScroll.getViewport().getView();
        desk.revalidate();
        Dimension desired = desk.getPreferredSize();
        desk.setSize(Math.max(desired.width, pageScroll.getViewport().getWidth()),
                Math.max(desired.height, pageScroll.getViewport().getHeight()));
        desk.doLayout();
        pageScroll.getViewport().revalidate();
        pageScroll.revalidate();
        workspace.repaint();
    }
    @Override public void fitWhenOpened() {
        int w = pageScroll.getViewport().getWidth();
        int h = pageScroll.getViewport().getHeight();
        if (w > 0 && h > 0) zoom(paper.fitZoom(w, h));
    }
    private void addMaterial(PdfMaterial material, float x, float y) {
        float w = material.isGraph() ? 280 : 320;
        float h = material.isGraph() ? Math.max(65, w * material.imageHeight() / Math.max(1f, material.imageWidth())) : 90;
        if (h > 600) {w *= 600f / h;h = 600;}
        controller.place(material, x, y, w, h);
        updateStatus();
    }
    @Override public void addHistoryItem(HistoryItem item) {
        Objects.requireNonNull(item);
        addMaterial(HistoryMaterialMapper.toPdfMaterial(item.entry()), 75, 108);
    }
    @Override public boolean dropHistoryItem(HistoryItem item, Point screenTopLeft) {
        if (!paper.isShowing()) {addHistoryItem(item);return true;}
        Point spot = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(spot, paper);
        addMaterial(HistoryMaterialMapper.toPdfMaterial(item.entry()),
                Math.max(0, paper.pageX(spot.x)), Math.max(0, paper.pageY(spot.y)));
        return true;
    }
    private void addPage() {paper.finishInlineEdit();controller.addPage();updateStatus();}
    private void deletePage() {
        paper.finishInlineEdit();
        if (!controller.deletePage()) JOptionPane.showMessageDialog(this, "The document must contain one page.");
        updateStatus();
    }
    private void undo() {paper.finishInlineEdit();controller.undo();updateStatus();}
    private void redo() {paper.finishInlineEdit();controller.redo();updateStatus();}
    private void autoLayout() {controller.autoLayout();updateStatus();}
    /** Normal document body starts at 1-inch margins. Text boxes stay a separate Insert tool. */
    public void typeOnPage() {
        paper.finishInlineEdit();
        PdfDocument.Placement body = controller.placements().stream()
                .filter(p -> p.isNote() && Math.abs(p.x()-72)<.5 && Math.abs(p.y()-72)<.5
                        && p.width() >= 440).findFirst().orElse(null);
        if (body == null) {
            body = controller.addNoteAt(72, 72);
            controller.beginTransform();
            controller.transform(72, 72, 451, 690);
        } else controller.select(body.id());
        updateStatus();
        paper.editNoteInline();
    }
    private void addNote() {paper.finishInlineEdit();controller.addNote();updateStatus();editNote();}
    private void insertFormula() {
        String latex = JOptionPane.showInputDialog(this, "LaTeX formula", "Insert formula", JOptionPane.PLAIN_MESSAGE);
        if (latex == null || latex.isBlank()) return;
        PdfMaterial material = new PdfMaterial("Typed formula", latex, "", null, 0, 0) {};
        addMaterial(material, 90, 120);
    }
    private void insertCanvasInk() {
        BufferedImage image = canvasCapture.get();
        if (image == null) {JOptionPane.showMessageDialog(this, "Select some handwriting on the Canvas first.");return;}
        addMaterial(MathLibrary.region("Handwriting", image), 85, 110);
    }
    private void editFormula() {
        PdfDocument.Placement selected = controller.selected();
        if (selected == null || selected.isNote() || selected.material().isGraph()) {
            JOptionPane.showMessageDialog(this, "Select a formula on the page first.");return;
        }
        FormulaEditDialog.Result change = FormulaEditDialog.show(SwingUtilities.getWindowAncestor(this), selected.material());
        if (change != null) {controller.editSelectedFormula(change.formula(), change.result()); updateStatus();}
    }
    private void editNote() {
        if (!paper.editNoteInline()) {
            JOptionPane.showMessageDialog(this, "Select a text box first, or double-click an empty page to type.");
        }
    }
    private void setNoteStyle(String style) {
        pdf_editor.use_case.NoteFormatting.Style requested = switch (style) {
            case "bold" -> pdf_editor.use_case.NoteFormatting.Style.BOLD;
            case "italic" -> pdf_editor.use_case.NoteFormatting.Style.ITALIC;
            default -> pdf_editor.use_case.NoteFormatting.Style.UNDERLINE;
        };
        if (paper.isEditingNote()) {
            paper.toggleInlineStyle(switch (requested) {
                case BOLD -> javax.swing.text.StyleConstants.Bold;
                case ITALIC -> javax.swing.text.StyleConstants.Italic;
                case UNDERLINE -> javax.swing.text.StyleConstants.Underline;
            });
        } else if (!controller.toggleSelectedNoteStyle(requested)) {
            JOptionPane.showMessageDialog(this, "Select or edit a text box first.");
        }
        updateStatus();
    }
    private void applyNoteFontSize(int size) {
        if (paper.isEditingNote()) paper.setInlineFontSize(size);
        else controller.resizeSelectedNote(size);
        updateStatus();
    }
    private void arrange(int direction) {
        paper.finishInlineEdit();
        controller.arrange(direction);
        updateStatus();
    }
    private void arrangeMenu() {
        if (controller.selected() == null) {JOptionPane.showMessageDialog(this, "Select a page object first.");return;}
        JPopupMenu menu = new JPopupMenu();
        item(menu, "Bring forward", () -> arrange(1));
        item(menu, "Send backward", () -> arrange(-1));
        item(menu, "Bring to front", () -> arrange(2));
        item(menu, "Send to back", () -> arrange(-2));
        item(menu, "Move to page…", this::moveToPageMenu);
        menu.show(this, Math.max(12, getWidth() / 3), 100);
    }
    /** Build destinations once for the ribbon and the document context menu. */
    private JMenu pageDestinations() {
        JMenu destinations = new JMenu("Move to page");
        for (int i = 0; i < controller.pageCount(); i++) {
            final int destination = i;
            if (i != controller.currentPage()) {
                JMenuItem target = new JMenuItem("Page " + (i + 1));
                target.addActionListener(e -> {
                    paper.finishInlineEdit(); controller.moveSelectedToPage(destination); updateStatus();
                });
                destinations.add(target);
            }
        }
        JMenuItem newPage = new JMenuItem("New page");
        newPage.addActionListener(e -> {
            paper.finishInlineEdit(); controller.moveSelectedToPage(-1); updateStatus();
        });
        destinations.add(newPage);
        return destinations;
    }
    private void moveToPageMenu() {
        if (controller.selected() == null) {JOptionPane.showMessageDialog(this, "Select a page object first.");return;}
        JPopupMenu menu = new JPopupMenu();
        JMenu choices = pageDestinations();
        while (choices.getItemCount() > 0) {
            JMenuItem destination = choices.getItem(0);
            choices.remove(0);
            menu.add(destination);
        }
        menu.show(this, Math.max(12, getWidth() / 3), 106);
    }
    private void contextMenu(MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        PdfDocument.Placement selected = controller.selected();
        if (selected == null) {
            item(menu, "Type here", () -> {
                controller.addNoteAt(paper.pageX(event.getX()), paper.pageY(event.getY()));
                updateStatus();editNote();
            });
            item(menu, "Insert formula", this::insertFormula);
            item(menu, "Capture Canvas area", () -> regionCapture.run());
        } else {
            if (selected.isNote()) item(menu, "Type / edit text in place", this::editNote);
            else if (!selected.material().isGraph()) {
                item(menu, "Edit formula / LaTeX / keyboard math...", this::editFormula);
                if (selected.material().hasResult()) item(menu, "Split formula and answer", () -> {controller.splitSelected();updateStatus();});
            }
            item(menu, "Bring forward one layer", () -> arrange(1));
            item(menu, "Send backward one layer", () -> arrange(-1));
            item(menu, "Bring to front", () -> arrange(2));
            item(menu, "Send to back", () -> arrange(-2));
            menu.add(pageDestinations());
            item(menu, "Delete selected item", () -> {controller.deleteSelected();updateStatus();});
        }
        menu.show(paper, event.getX(), event.getY());
    }
    private static void item(JPopupMenu menu, String title, Runnable action) {
        JMenuItem entry = new JMenuItem(title);entry.addActionListener(e -> action.run());menu.add(entry);
    }
    private void preview() {
        try {
            List<pdf_editor.domain.PdfPreviewPage> previews = controller.preview();
            if (previews.isEmpty()) return;
            java.util.List<BufferedImage> pages = new java.util.ArrayList<>();
            for (var preview : previews) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(preview.png()));
                if (image != null) pages.add(image);
            }
            if (!pages.isEmpty()) WordPreviewDialog.show(this, pages);
        } catch (IOException bad) {error("Preview failed", bad);}
    }
    private void export() {
        JFileChooser chooser = new JFileChooser();chooser.setDialogTitle("Export PDF");
        chooser.setSelectedFile(new java.io.File("math-lab.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (!target.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
            target = Path.of(target.toString() + ".pdf");
        if (java.nio.file.Files.exists(target) && JOptionPane.showConfirmDialog(this,
                "Replace existing file?", "Confirm export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {controller.export(target);title.setText(target.getFileName().toString());}
        catch (IOException bad) {error("Export failed", bad);}
    }
    private void error(String message, Exception e) {JOptionPane.showMessageDialog(this, message + ": " + e.getMessage());}
    private final class Tab {
        private final String text;
        private final JPanel ribbon;
        private final JToggleButton button;
        private Tab(String text, JPanel ribbon) {
            this.text = text;this.ribbon = ribbon;
            button = new JToggleButton(text) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isSelected() || getModel().isRollover()) {
                        g2.setColor(isSelected() ? new Color(225, 237, 254) : new Color(244, 246, 250));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 13, 13);
                    }
                    g2.dispose(); super.paintComponent(g);
                }
            };
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder(8, 13, 8, 13));
            button.setFocusable(false);
            button.setRolloverEnabled(true);
            button.setFont(BoardTheme.font(12f));
            button.addActionListener(e -> select());
        }
        private void select() {
            for (Tab tab : tabs) tab.button.setSelected(tab == this);
            ribbonHolder.removeAll();
            Capsule capsule = new Capsule(new BorderLayout());
            capsule.add(ribbon, BorderLayout.CENTER);
            ribbonHolder.add(capsule, BorderLayout.CENTER);
            ribbonHolder.revalidate();ribbonHolder.repaint();
        }
    }
}
