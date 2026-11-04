package pdf_editor.view;

import pdf_editor.adapter.PdfEditorController;
import pdf_editor.adapter.PageZoomModel;
import pdf_editor.adapter.PageCoordinateMapper;
import pdf_editor.data_access.MaterialRenderer;
import pdf_editor.data_access.NoteRenderer;
import pdf_editor.domain.PdfDocument;
import pdf_editor.MathLibrary;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

/** Swing page interaction adapter: hit-testing and rendering; all edits go through controller. */
public final class PdfPageCanvas extends JPanel {
    public interface Listener {
        void contextMenu(MouseEvent event);
        void editNote();
        void editFormula();
    }
    private static final int MARGIN = 28;
    private final PdfEditorController controller;
    private final Listener listener;
    private final PageZoomModel zoomModel = new PageZoomModel();
    public double zoom() { return zoomModel.zoom(); }
    public double setZoom(double zoom) {
        zoomModel.set(zoom);
        updateSize(); revalidate(); repaint();
        return zoomModel.zoom();
    }
    public double adjustZoom(double rotation) { return setZoom(zoomModel.adjustWheel(rotation)); }
    public double fitZoom(int viewportWidth, int viewportHeight) {
        return setZoom(zoomModel.fit(viewportWidth - MARGIN * 2, viewportHeight - MARGIN * 2,
                (int) PdfDocument.PAGE_WIDTH, (int) PdfDocument.PAGE_HEIGHT));
    }
    private void updateSize() {
        double z = zoom();
        setPreferredSize(new Dimension((int)Math.ceil(PdfDocument.PAGE_WIDTH * z) + MARGIN * 2,
                (int)Math.ceil(PdfDocument.PAGE_HEIGHT * z) + MARGIN * 2));
    }
    private float toPageX(int screenX) { return PageCoordinateMapper.screenToPage(screenX, MARGIN, zoom()); }
    private float toPageY(int screenY) { return PageCoordinateMapper.screenToPage(screenY, MARGIN, zoom()); }
    private Point anchor;
    private float oldX, oldY, oldW, oldH;
    private boolean resizing;
    private boolean dragStarted;

    public PdfPageCanvas(PdfEditorController controller, Listener listener) {
        this.controller = Objects.requireNonNull(controller);
        this.listener = Objects.requireNonNull(listener);
        updateSize();
        setBackground(new Color(243, 247, 253));
        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    MathLibrary.Material material = controller.library().find(id);
                    if (material == null) return false;
                    Point location = support.getDropLocation().getDropPoint();
                    controller.place(material, toPageX(location.x), toPageY(location.y),
                            material.isGraph() ? 260 : 300, material.isGraph() ? 180 : 100);
                    repaint(); return true;
                } catch (Exception ignored) { return false; }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                controller.selectAt(toPageX(e.getX()), toPageY(e.getY()));
                PdfDocument.Placement p = controller.selected();
                anchor = null; dragStarted = false;
                if (p != null) {
                    oldX = p.x(); oldY = p.y(); oldW = p.width(); oldH = p.height();
                    resizing = toPageX(e.getX()) > p.x() + p.width() - 18 / zoom()
                            && toPageY(e.getY()) > p.y() + p.height() - 18 / zoom();
                    if (SwingUtilities.isLeftMouseButton(e)) anchor = e.getPoint();
                }
                if (e.isPopupTrigger()) listener.contextMenu(e);
                repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (controller.selected() == null || anchor == null) return;
                float dx = PageCoordinateMapper.deltaToPage(e.getX() - anchor.x, zoom());
                float dy = PageCoordinateMapper.deltaToPage(e.getY() - anchor.y, zoom());
                if (!dragStarted) {
                    if (dx == 0 && dy == 0) return;
                    controller.beginTransform(); dragStarted = true;
                }
                if (resizing) controller.transform(oldX, oldY, oldW + dx, oldH + dy);
                else controller.transform(oldX + dx, oldY + dy, oldW, oldH);
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                anchor = null; dragStarted = false;
                if (e.isPopupTrigger()) listener.contextMenu(e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && controller.selected() != null) {
                    if (controller.selected().isNote()) listener.editNote();
                    else if (!controller.selected().material().isGraph()) listener.editFormula();
                }
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Page and placements share the same physical view transform.
            g2.translate(MARGIN, MARGIN);
            g2.scale(zoom(), zoom());
            g2.translate(-MARGIN, -MARGIN);
            g2.setColor(new Color(117, 142, 173, 28));
            g2.fillRoundRect(MARGIN + 5, MARGIN + 7, (int)PdfDocument.PAGE_WIDTH,
                    (int)PdfDocument.PAGE_HEIGHT, 5, 5);
            g2.setColor(Color.WHITE);
            g2.fillRect(MARGIN, MARGIN, (int)PdfDocument.PAGE_WIDTH, (int)PdfDocument.PAGE_HEIGHT);
            g2.setColor(new Color(215, 225, 238));
            g2.drawRect(MARGIN, MARGIN, (int)PdfDocument.PAGE_WIDTH, (int)PdfDocument.PAGE_HEIGHT);
            PdfDocument.Placement selected = controller.selected();
            List<PdfDocument.Placement> placements = controller.placements();
            for (PdfDocument.Placement p : placements) {
                int x = Math.round(p.x()) + MARGIN, y = Math.round(p.y()) + MARGIN;
                int width = Math.round(p.width()), height = Math.round(p.height());
                // Render at device-visible resolution on every paint/zoom: a single
                // low-res bitmap must never be stretched when the user zooms in.
                double deviceScale = Math.max(1.0, zoom() * Math.max(1.0,
                        g2.getDeviceConfiguration().getDefaultTransform().getScaleX()));
                int rasterWidth = Math.min(3200, Math.max(8, (int) Math.ceil(width * deviceScale)));
                int rasterHeight = Math.min(3200, Math.max(8, (int) Math.ceil(height * deviceScale)));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(p.isNote() ? NoteRenderer.render(p, rasterWidth, rasterHeight)
                        : MaterialRenderer.render(p.material(), rasterWidth, rasterHeight),
                        x, y, width, height, null);
                if (selected != null && selected.id().equals(p.id())) {
                    g2.setColor(new Color(33, 110, 220)); g2.setStroke(new BasicStroke(2));
                    g2.drawRect(x, y, width, height);
                    g2.fillRect(x + width - 9, y + height - 9, 9, 9);
                }
            }
        } finally { g2.dispose(); }
    }
}
