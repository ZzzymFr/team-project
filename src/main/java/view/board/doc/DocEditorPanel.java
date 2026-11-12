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
import pdf_editor.view.RichNoteEditor;
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
    private boolean updatingZoom;

    public DocEditorPanel(PdfEditorController controller) {
        super(new BorderLayout());
        this.controller = Objects.requireNonNull(controller);
        this.library = controller.library();
        setBackground(Color.WHITE);
        paper = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
            @Override public void contextMenu(MouseEvent e) { contextMenu(e); }
            @Override public void editNote() { DocEditorPanel.this.editNote(); }
            @Override public void editFormula() { DocEditorPanel.this.editFormula(); }
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
        workspace.add(new PageRuler(), BorderLayout.NORTH);
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
    public void close() { detachLibraryListener.run(); }
    public PdfPageCanvas pageView() { return paper; }
    public PdfEditorController documentController() { return controller; }
    public boolean isLibraryVisible() { return libraryPanel.isVisible(); }

    private JComponent buildHeader() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        nameRow.setOpaque(false);
        nameRow.setBorder(BorderFactory.createEmptyBorder(BoardTheme.TOP + 4, 8, 0, 60));
        JLabel mark = new JLabel("▤"); mark.setForeground(new Color(0x007AFF));
        mark.setFont(BoardTheme.font(20f));
        title.setFont(BoardTheme.font(15f));
        nameRow.add(mark); nameRow.add(title);
        JLabel docStatus = new JLabel("Export to save as PDF");
        docStatus.setFont(BoardTheme.font(11f)); docStatus.setForeground(MUTED);
        nameRow.add(docStatus);
        top.add(nameRow, BorderLayout.NORTH);

        JPanel tabsRow = new JPanel(new BorderLayout());
        tabsRow.setBackground(Color.WHITE);
        tabsRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));
        JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        names.setOpaque(false);
        tabs.add(new Tab("Home", homeRibbon()));
        tabs.add(new Tab("Insert", insertRibbon()));
        tabs.add(new Tab("Layout", layoutRibbon()));
        tabs.add(new Tab("View", viewRibbon()));
        for (Tab tab : tabs) names.add(tab.button);
        tabsRow.add(names, BorderLayout.CENTER);
        JButton export = flatButton("Export PDF", this::export);
        export.setBackground(new Color(0x007AFF));
        export.setForeground(Color.WHITE);
        export.setOpaque(true);
        export.setFont(BoardTheme.font(12f));
        names.add(export);
        top.add(tabsRow, BorderLayout.CENTER);
        ribbonHolder.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DIVIDER));
        ribbonHolder.setBackground(Color.WHITE);
        top.add(ribbonHolder, BorderLayout.SOUTH);
        return top;
    }

    private JPanel ribbon() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return row;
    }
    private RibbonButton command(JPanel row, RibbonButton b, Runnable action) {
        row.add(b.onClick(action));
        return b;
    }
    private JPanel homeRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.icon(Icons::undo, "Undo"), this::undo);
        command(row, RibbonButton.icon(DocIcons::redo, "Redo"), this::redo);
        command(row, RibbonButton.letter("B", Font.BOLD, false, false, "Bold selected note"), () -> setNoteStyle("bold"));
        command(row, RibbonButton.letter("I", Font.ITALIC, false, false, "Italic selected note"), () -> setNoteStyle("italic"));
        command(row, RibbonButton.letter("U", Font.PLAIN, true, false, "Underline selected note"), () -> setNoteStyle("underline"));
        command(row, RibbonButton.dropdown("Font size", 85, "Set size of selected note"), this::setNoteFontSize);
        command(row, RibbonButton.labeled(DocIcons::formula, "Edit math"), this::editFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Edit note"), this::editNote);
        return row;
    }
    private JPanel insertRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(DocIcons::formula, "Formula"), this::insertFormula);
        command(row, RibbonButton.labeled(DocIcons::comment, "Text box"), this::addNote);
        command(row, RibbonButton.labeled(DocIcons::image, "Canvas ink"), this::insertCanvasInk);
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "New page"), this::addPage);
        command(row, RibbonButton.labeled(Icons::history, "Library"), this::toggleLibrary);
        return row;
    }
    private JPanel layoutRibbon() {
        JPanel row = ribbon();
        JLabel label = new JLabel("A4 · Portrait · Editable page elements");
        label.setFont(BoardTheme.font(12f)); label.setForeground(MUTED);
        row.add(label);
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "Add page"), this::addPage);
        command(row, RibbonButton.labeled(DocIcons::pageBreak, "Delete page"), this::deletePage);
        command(row, RibbonButton.labeled(DocIcons.align(0), "Auto layout"), this::autoLayout);
        return row;
    }
    private JPanel viewRibbon() {
        JPanel row = ribbon();
        command(row, RibbonButton.labeled(Icons::document, "Preview"), this::preview);
        command(row, RibbonButton.labeled(DocIcons.align(0), "Fit page"), this::fitWhenOpened);
        command(row, RibbonButton.labeled(Icons::history, "Library"), this::toggleLibrary);
        command(row, RibbonButton.letter("−", Font.PLAIN, false, false, "Zoom out"), () -> zoom(paper.zoom() / 1.15));
        command(row, RibbonButton.letter("+", Font.PLAIN, false, false, "Zoom in"), () -> zoom(paper.zoom() * 1.15));
        return row;
    }
    private JComponent buildStatus() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
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
        JButton b = new JButton(text);
        b.setFont(BoardTheme.font(12f));
        b.setFocusable(false);
        b.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        b.addActionListener(e -> task.run());
        return b;
    }
    private void setupLibrary() {
        libraryPanel.setBackground(Color.WHITE);
        libraryPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DIVIDER));
        libraryPanel.setPreferredSize(new Dimension(183, 0));
        JPanel header = new JPanel(new BorderLayout());header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 9, 6, 6));
        JLabel name = new JLabel("Library");name.setFont(BoardTheme.font(14f));
        header.add(name, BorderLayout.CENTER);
        header.add(flatButton("×", this::toggleLibrary), BorderLayout.EAST);
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
                card.add(label, BorderLayout.NORTH);
                try { card.add(new JLabel(new ImageIcon(MaterialRenderer.render(material, 160, 42))), BorderLayout.CENTER); }
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
            @Override public void mouseClicked(MouseEvent e) {
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
    private void refreshLibrary() {
        libraryModel.clear();for (MathLibrary.Material material : library.items()) libraryModel.addElement(material);
    }
    private void toggleLibrary() { libraryPanel.setVisible(!libraryPanel.isVisible()); workspace.revalidate();workspace.repaint(); }
    private void updateStatus() {
        pageCount.setText("Page " + (controller.currentPage() + 1) + " of " + controller.pageCount());
        paper.repaint();
    }
    private void changePage(int target) {controller.switchPage(target);updateStatus();}
    private void zoom(double z) {
        double actual = paper.setZoom(z);
        updatingZoom = true;
        try {zoomSlider.setValue((int) Math.round(100 * actual));zoomValue.setText((int) Math.round(100 * actual) + "%");}
        finally {updatingZoom = false;}
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
    private void addPage() {controller.addPage();updateStatus();}
    private void deletePage() {
        if (!controller.deletePage()) JOptionPane.showMessageDialog(this, "The document must contain one page.");
        updateStatus();
    }
    private void undo() {controller.undo();updateStatus();}
    private void redo() {controller.redo();updateStatus();}
    private void autoLayout() {controller.autoLayout();updateStatus();}
    private void addNote() {controller.addNote();updateStatus();editNote();}
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
        PdfDocument.Placement selected = controller.selected();
        if (selected == null || !selected.isNote()) {JOptionPane.showMessageDialog(this, "Select a text box first.");return;}
        List<PdfDocument.NoteSpan> spans = RichNoteEditor.edit(this, selected);
        if (spans != null) {controller.updateRichNote(spans);updateStatus();}
    }
    private void setNoteStyle(String style) {
        pdf_editor.use_case.NoteFormatting.Style requested = switch (style) {
            case "bold" -> pdf_editor.use_case.NoteFormatting.Style.BOLD;
            case "italic" -> pdf_editor.use_case.NoteFormatting.Style.ITALIC;
            default -> pdf_editor.use_case.NoteFormatting.Style.UNDERLINE;
        };
        if (!controller.toggleSelectedNoteStyle(requested))
            JOptionPane.showMessageDialog(this, "Select a text box first.");
        updateStatus();
    }
    private void setNoteFontSize() {
        PdfDocument.Placement selected = controller.selected();
        if (selected == null || !selected.isNote()) {
            JOptionPane.showMessageDialog(this, "Select a text box first.");return;
        }
        String value = JOptionPane.showInputDialog(this, "Font size (8–72)", selected.noteSize());
        if (value == null) return;
        try {
            controller.resizeSelectedNote(Integer.parseInt(value.trim()));
            updateStatus();
        } catch (IllegalArgumentException bad) {
            JOptionPane.showMessageDialog(this, "Enter a font size from 8 to 72.");
        }
    }
    private void contextMenu(MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        PdfDocument.Placement selected = controller.selected();
        if (selected == null) {
            item(menu, "Add text box", this::addNote);
            item(menu, "Insert formula", this::insertFormula);
            item(menu, "Add canvas handwriting", this::insertCanvasInk);
        } else {
            if (selected.isNote()) item(menu, "Edit text / formatting...", this::editNote);
            else if (!selected.material().isGraph()) {
                item(menu, "Edit formula / LaTeX / keyboard math...", this::editFormula);
                if (selected.material().hasResult()) item(menu, "Split formula and answer", () -> {controller.splitSelected();updateStatus();});
            }
            item(menu, "Bring to front", () -> {controller.arrange(2);updateStatus();});
            item(menu, "Send to back", () -> {controller.arrange(-2);updateStatus();});
            item(menu, "Move to next page", () -> {controller.moveSelectedToPage(-1);updateStatus();});
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
            JTabbedPane pages = new JTabbedPane();
            for (int i = 0; i < previews.size(); i++) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(previews.get(i).png()));
                if (image != null) pages.addTab("Page " + (i + 1), new JScrollPane(new JLabel(new ImageIcon(image))));
            }
            JOptionPane.showMessageDialog(this, pages, "PDF Preview", JOptionPane.PLAIN_MESSAGE);
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
    /** Small Word-style ruler; decorative, never owns page or business state. */
    private final class PageRuler extends JComponent {
        @Override public Dimension getPreferredSize() { return new Dimension(10, 20); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(DESK);g2.fillRect(0, 0, getWidth(), getHeight());
            int width = (int) Math.round(PdfDocument.PAGE_WIDTH * paper.zoom());
            int left = (getWidth() - width) / 2;
            g2.setColor(new Color(255, 255, 255, 220));
            g2.fillRect(left, 0, width, 19);
            g2.setColor(new Color(155, 158, 167));
            double cm = width / 21.0;
            for (int i = 0; i <= 42; i++) {
                int x = left + (int) Math.round(i * cm / 2.0);
                g2.drawLine(x, 19, x, i % 2 == 0 ? 13 : 16);
            }
            g2.dispose();
        }
    }
    private final class Tab {
        private final String text;
        private final JPanel ribbon;
        private final JToggleButton button;
        private Tab(String text, JPanel ribbon) {
            this.text = text;this.ribbon = ribbon;
            button = new JToggleButton(text);
            button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            button.setBackground(Color.WHITE);
            button.setFocusable(false);
            button.setFont(BoardTheme.font(12f));
            button.addActionListener(e -> select());
        }
        private void select() {
            for (Tab tab : tabs) tab.button.setSelected(tab == this);
            ribbonHolder.removeAll();ribbonHolder.add(ribbon, BorderLayout.CENTER);
            ribbonHolder.revalidate();ribbonHolder.repaint();
        }
    }
}
