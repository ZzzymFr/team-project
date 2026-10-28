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
    private static final Color BACKDROP = new Color(239, 244, 252);
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
        JPanel left = new JPanel(new BorderLayout(4, 4));
        JLabel shelfTitle = new JLabel("  MATERIAL LIBRARY  ·  Drag onto A4 paper");
        shelfTitle.setFont(Fonts.ui(Font.BOLD, 15f));
        shelfTitle.setForeground(new Color(39, 55, 80));
        left.add(shelfTitle, BorderLayout.NORTH);
        left.setBackground(new Color(250, 252, 255));
        left.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(211, 224, 241)),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        left.add(new JScrollPane(shelf), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(295, 600));
        editor.add(left, BorderLayout.WEST);
        JScrollPane pageScroll = new JScrollPane(canvas);
        pageScroll.getViewport().setBackground(BACKDROP);
        editor.add(pageScroll, BorderLayout.CENTER);
        editor.add(editorToolbar(), BorderLayout.NORTH);
        editor.add(status, BorderLayout.SOUTH);
        center.add(editor, "edit"); center.add(preview, "preview");
        setContentPane(center);
        library.onChange(() -> SwingUtilities.invokeLater(this::refreshShelf));
        refreshShelf(); updatePageLabel();
    }
    private void setupShelf() {
        shelf.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shelf.setFixedCellHeight(85);
        shelf.setCellRenderer((list, item, index, selected, focused) -> {
            JPanel panel = new JPanel(new BorderLayout(3, 3));
            panel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            panel.setBackground(selected ? new Color(214, 229, 249) : Color.WHITE);
            if (item != null) {
                JLabel title = new JLabel(item.title + (item.isGraph() ? "  [image]" : "  [formula + result]"));
                panel.add(title, BorderLayout.NORTH);
                JLabel content = new JLabel(new ImageIcon(MaterialRenderer.render(item, 230, 48)));
                panel.add(content, BorderLayout.CENTER);
            }
            return panel;
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
        preview.add(new JScrollPane(image), BorderLayout.CENTER);
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
            g2.setColor(Color.WHITE); g2.fillRect(MARGIN, MARGIN, (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT);
            g2.setColor(new Color(180, 180, 180)); g2.drawRect(MARGIN, MARGIN, (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT);
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
