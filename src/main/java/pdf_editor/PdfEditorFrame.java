package pdf_editor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import view.glass.GlassButton;
import view.stack.Fonts;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

public final class PdfEditorFrame extends JFrame {
    private final MathLibrary library;
    private final DocumentEditor useCase = new DocumentEditor();
    private final DefaultListModel<MathLibrary.Material> listModel = new DefaultListModel<>();
    private final JList<MathLibrary.Material> shelf = new JList<>(listModel);
    private final PageCanvas canvas = new PageCanvas();
    private final JPanel editor = new JPanel(new BorderLayout(12, 12));
    private static final Color BACKDROP = new Color(243, 247, 253);
    private static final Color INK = new Color(35, 52, 77);
    private static final Color MUTED = new Color(113, 131, 157);
    private final JPanel preview = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel pageLabel = new JLabel();
    private final JLabel status = new JLabel("Drag an item from the library onto the page.");
    private int currentPage = 0;
    private int previewPage = 0;
    private java.util.List<BufferedImage> previewImages = new java.util.ArrayList<>();

    public PdfEditorFrame(MathLibrary library) {
        super("Math Lab - PDF Editor");
        this.library = library;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1120, 850); setMinimumSize(new Dimension(830, 650));
        setLocationRelativeTo(null);
        editor.setBackground(BACKDROP);
        editor.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setupShelf();
        RoundedPanel left = new RoundedPanel(new BorderLayout(0, 12), new Color(255, 255, 255, 238), 24);
        left.setBorder(BorderFactory.createEmptyBorder(18, 16, 16, 16));
        JLabel shelfTitle = new JLabel("MATERIAL LIBRARY");
        shelfTitle.setFont(Fonts.ui(Font.BOLD, 16f));
        shelfTitle.setForeground(INK);
        JLabel hint = new JLabel("Drag a card onto the A4 page");
        hint.setFont(Fonts.ui(Font.PLAIN, 12f));
        hint.setForeground(MUTED);
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        heading.add(shelfTitle, BorderLayout.NORTH);
        heading.add(hint, BorderLayout.SOUTH);
        left.add(heading, BorderLayout.NORTH);
        JScrollPane shelfScroll = glassScroll(shelf);
        shelfScroll.getViewport().setOpaque(false);
        left.add(shelfScroll, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(320, 600));
        editor.add(left, BorderLayout.WEST);
        RoundedPanel stage = new RoundedPanel(new BorderLayout(), new Color(233, 240, 250), 24);
        stage.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane pageScroll = glassScroll(canvas);
        pageScroll.getViewport().setBackground(new Color(233, 240, 250));
        stage.add(pageScroll, BorderLayout.CENTER);
        editor.add(stage, BorderLayout.CENTER);
        editor.add(editorToolbar(), BorderLayout.NORTH);
        status.setFont(Fonts.ui(Font.PLAIN, 12f));
        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(2, 5, 0, 0));
        editor.add(status, BorderLayout.SOUTH);
        center.add(editor, "edit"); center.add(preview, "preview");
        setContentPane(center);
        library.onChange(() -> SwingUtilities.invokeLater(this::refreshShelf));
        refreshShelf(); updatePageLabel();
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
                JLabel title = new JLabel(item.title);
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
                return m == null ? null : new StringSelection(m.id);
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
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 5));
        JButton previous = button("< Page", () -> switchPage(currentPage - 1));
        JButton next = button("Page >", () -> switchPage(currentPage + 1));
        tools.add(previous); tools.add(pageLabel); tools.add(next);
        tools.setOpaque(false);
        pageLabel.setFont(Fonts.ui(Font.BOLD, 14f));
        tools.add(button("+ Page", () -> { switchPage(useCase.addPage()); }));
        tools.add(button("Delete Page", () -> {
            if (useCase.pageCount() <= 1) { JOptionPane.showMessageDialog(this, "At least one page is required."); return; }
            useCase.removePage(currentPage); switchPage(Math.min(currentPage, useCase.pageCount() - 1));
        }));
        tools.add(button("Delete Item", () -> {
            if (canvas.selected != null) { useCase.delete(currentPage, canvas.selected); canvas.selected = null; canvas.repaint(); }
        }));
        tools.add(button("Undo", this::undo)); tools.add(button("Redo", this::redo));
        tools.add(button("Done / Preview", this::showPreview));
        return tools;
    }
    private JButton button(String title, Runnable action) {
        JButton b = new GlassButton(title, 34); b.addActionListener(e -> action.run()); return b;
    }
    private void switchPage(int page) {
        if (page < 0 || page >= useCase.pageCount()) return;
        currentPage = page; canvas.selected = null; updatePageLabel(); canvas.repaint();
    }
    private void updatePageLabel() { pageLabel.setText("Page " + (currentPage + 1) + " / " + useCase.pageCount()); }
    private void undo() {
        if (useCase.undo()) switchPage(Math.min(currentPage, useCase.pageCount() - 1));
    }
    private void redo() {
        if (useCase.redo()) switchPage(Math.min(currentPage, useCase.pageCount() - 1));
    }
    private void showPreview() {
        try (PDDocument pdf = new PdfExporter().build(useCase.snapshot())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            previewImages = new java.util.ArrayList<>();
            for (int i = 0; i < pdf.getNumberOfPages(); i++) previewImages.add(renderer.renderImageWithDPI(i, 90));
            previewPage = 0; buildPreview(); cards.show(center, "preview");
        } catch (IOException | RuntimeException e) { error("Preview failed", e); }
    }
    private void buildPreview() {
        preview.removeAll();
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(button("Back to Edit", () -> cards.show(center, "edit")));
        bar.add(button("<", () -> { previewPage = Math.max(0, previewPage - 1); buildPreview(); }));
        bar.add(new JLabel("Preview " + (previewPage + 1) + " / " + previewImages.size()));
        bar.add(button(">", () -> { previewPage = Math.min(previewImages.size() - 1, previewPage + 1); buildPreview(); }));
        bar.add(button("Export PDF...", this::export));
        preview.add(bar, BorderLayout.NORTH);
        JLabel image = new JLabel(new ImageIcon(previewImages.get(previewPage)));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        preview.add(glassScroll(image), BorderLayout.CENTER);
        preview.revalidate(); preview.repaint();
    }
    private void export() {
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("mathlab-notes.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File output = chooser.getSelectedFile();
        if (!output.getName().toLowerCase().endsWith(".pdf")) output = new java.io.File(output.getPath() + ".pdf");
        if (output.exists() && JOptionPane.showConfirmDialog(this, "Overwrite existing file?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            new PdfExporter().export(useCase.snapshot(), output.toPath());
            JOptionPane.showMessageDialog(this, "Saved: " + output.getAbsolutePath());
        } catch (IOException | RuntimeException e) { error("Export failed", e); }
    }
    private void error(String title, Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
    private final class PageCanvas extends JPanel {
        private static final int SCALE = 1; // 1 Swing pixel = 1 PDF point
        private static final int MARGIN = 28;
        private PdfDocument.Placement selected;
        private Point anchor;
        private float oldX, oldY, oldW, oldH;
        private boolean resizing;
        PageCanvas() {
            setPreferredSize(new Dimension((int) PdfDocument.PAGE_WIDTH + MARGIN * 2,
                    (int) PdfDocument.PAGE_HEIGHT + MARGIN * 2));
            setBackground(BACKDROP);
            setTransferHandler(new TransferHandler() {
                @Override public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.stringFlavor);
                }
                @Override public boolean importData(TransferSupport support) {
                    if (!canImport(support)) return false;
                    try {
                        String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        MathLibrary.Material material = null;
                        for (MathLibrary.Material m : library.items()) if (m.id.equals(id)) { material = m; break; }
                        if (material == null) return false;
                        Point pt = support.getDropLocation().getDropPoint();
                        float w = material.isGraph() ? 260 : 300;
                        float h = material.isGraph() ? 180 : 100;
                        PdfDocument.Placement p = useCase.place(currentPage, material,
                                pt.x - MARGIN, pt.y - MARGIN, w, h); selected = p; repaint(); return true;
                    } catch (Exception e) { return false; }
                }
            });
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    selected = null;
                    java.util.List<PdfDocument.Placement> placements = useCase.placements(currentPage);
                    for (int i = placements.size() - 1; i >= 0; i--) {
                        PdfDocument.Placement p = placements.get(i);
                        if (e.getX() >= p.x + MARGIN && e.getX() <= p.x + p.width + MARGIN
                                && e.getY() >= p.y + MARGIN && e.getY() <= p.y + p.height + MARGIN) {
                            selected = p; break;
                        }
                    }
                    if (selected != null) {
                        useCase.beginTransform(currentPage, selected); anchor = e.getPoint();
                        oldX = selected.x; oldY = selected.y; oldW = selected.width; oldH = selected.height;
                        resizing = e.getX() > selected.x + selected.width + MARGIN - 18
                                && e.getY() > selected.y + selected.height + MARGIN - 18;
                    }
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (selected == null || anchor == null) return;
                    int dx = e.getX() - anchor.x, dy = e.getY() - anchor.y;
                    if (resizing) {
                        useCase.transform(currentPage, selected, oldX, oldY, oldW + dx, oldH + dy);
                    } else {
                        useCase.transform(currentPage, selected, oldX + dx, oldY + dy, oldW, oldH);
                    }
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) { anchor = null; }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(117, 142, 173, 28));
            g2.fillRoundRect(MARGIN + 5, MARGIN + 7, (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT, 5, 5);
            g2.setColor(Color.WHITE); g2.fillRect(MARGIN, MARGIN, (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT);
            g2.setColor(new Color(215, 225, 238)); g2.drawRect(MARGIN, MARGIN, (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT);
            for (PdfDocument.Placement p : useCase.placements(currentPage)) {
                int x = Math.round(p.x) + MARGIN, y = Math.round(p.y) + MARGIN;
                int w = Math.round(p.width), h = Math.round(p.height);
                g2.drawImage(MaterialRenderer.render(p.material, w * SCALE, h * SCALE), x, y, w, h, null);
                if (p == selected) {
                    g2.setColor(new Color(33, 110, 220)); g2.setStroke(new BasicStroke(2));
                    g2.drawRect(x, y, w, h); g2.fillRect(x + w - 9, y + h - 9, 9, 9);
                }
            }
            g2.dispose();
        }
        private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    }
}
