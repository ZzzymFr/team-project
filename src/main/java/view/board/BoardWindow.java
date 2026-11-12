package view.board;

import view.DrawingCanvas;
import interface_adapter.history.HistoryItem;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The one board: a full-window canvas with History (left) and PDF (right) panels that slide in
 * next to it behind a draggable divider, glass capsules on top, and the floating toolbar.
 */
public class BoardWindow extends JPanel implements HistoryPanel.DragHost {
    private final JComponent page;
    private final DrawingCanvas canvas;
    private final HistoryPanel history;
    private final JComponent pdf;
    private final SplitDivider historyDivider;
    private final SplitDivider pdfDivider;
    private final GlassBar historyBar;
    private final GlassBar.Item historyItem;
    private final GlassBar historyClose;
    private final GlassBar pdfBar;
    private final GlassBar pdfClose;
    private final Ghost ghost = new Ghost();
    private final JComponent titleStrip;
    private boolean historyOpen;
    private boolean pdfOpen;
    private double historyRatio = 0.24;  // kept while the app runs
    private double pdfRatio = 0.56;
    private boolean pdfOverlay;
    private int lastPageWidth = -1;
    private final Function<String, Boolean> deleteHistory;

    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history, JComponent pdf) {
        this(page, canvas, history, pdf, id -> false);
    }

    /** The composition root supplies the persistent-history delete command. */
    public BoardWindow(JComponent page, DrawingCanvas canvas,
                       java.util.function.Function<HistoryPanel.DragHost, HistoryPanel> history,
                       JComponent pdf, Function<String, Boolean> deleteHistory) {
        super(null);
        this.page = page;
        this.canvas = canvas;
        this.history = history.apply(this);
        this.pdf = pdf;
        this.deleteHistory = deleteHistory;
        setBackground(BoardTheme.CANVAS);

        historyDivider = new SplitDivider(x -> {
            historyRatio = SplitMath.ratioFor(x, getWidth());
            relayout();
        }, () -> {
            historyRatio = 0.24;
            relayout();
        });
        pdfDivider = new SplitDivider(x -> {
            pdfRatio = SplitMath.ratioFor(getWidth() - x, getWidth());
            relayout();
        }, () -> {
            pdfRatio = 0.56;
            relayout();
        });

        historyBar = bar(15f);
        historyItem = historyBar.segment("History", Icons::history, 0, () -> {
            setHistoryOpen(!historyOpen);
            return historyOpen;
        });
        historyBar.finish();
        historyClose = bar(15f);
        historyClose.action(null, Icons::close, () -> setHistoryOpen(false), () -> true);
        historyClose.finish();
        pdfBar = bar(15f);
        pdfBar.action("PDF", Icons::document, this::showPdfChoices, () -> true);
        pdfBar.finish();
        pdfClose = bar(15f);
        pdfClose.action(null, Icons::close, () -> setPdfOpen(false), () -> true);
        pdfClose.finish();
        titleStrip = BoardTheme.MAC ? new TitleStrip() : null;

        // first added = drawn on top
        add(ghost);
        add(historyBar);
        add(historyClose);
        add(pdfBar);
        add(pdfClose);
        if (titleStrip != null) {
            add(titleStrip);
        }
        add(historyDivider);
        add(pdfDivider);
        add(this.history);
        add(pdf);
        add(page);
        relayout();
    }

    private GlassBar bar(float size) {
        return new GlassBar(BoardTheme.font(size), this, this::paintBackdrop, this::backdropVersion);
    }

    public boolean isHistoryOpen() {
        return historyOpen;
    }

    public boolean isPdfOpen() {
        return pdfOpen;
    }

    // only one panel at a time, the window is too narrow for both
    public void setHistoryOpen(boolean open) {
        historyOpen = open;
        if (open) {
            historyBar.select(historyItem);
        } else {
            historyBar.deselect(0);
        }
        relayout();
    }

    public void setPdfOpen(boolean open) {
        boolean firstOpen = open && !pdfOpen;
        pdfOpen = open;
        relayout();
        if (firstOpen && pdf instanceof PdfWorkspacePort panel) {
            SwingUtilities.invokeLater(panel::fitWhenOpened);
        }
    }

    public boolean isPdfOverlay() { return pdfOverlay; }
    /** Overlay keeps the canvas at full width behind the PDF pane; split reserves its own width. */
    public void setPdfOverlay(boolean overlay) { pdfOverlay = overlay; relayout(); }

    private void showPdfChoices() {
        JPopupMenu choices = new JPopupMenu();
        JMenuItem split = new JMenuItem("PDF · Split view (side by side)");
        split.addActionListener(e -> { pdfOverlay = false; setPdfOpen(true); });
        JMenuItem overlay = new JMenuItem("PDF · Overlay on canvas");
        overlay.addActionListener(e -> { pdfOverlay = true; setPdfOpen(true); });
        JMenuItem close = new JMenuItem("Close PDF");
        close.addActionListener(e -> setPdfOpen(false));
        choices.add(split); choices.add(overlay); choices.addSeparator(); choices.add(close);
        choices.show(pdfBar, 0, pdfBar.getHeight());
    }

    double getHistoryRatio() {
        return historyRatio;
    }

    double getPdfRatio() {
        return pdfRatio;
    }

    Rectangle pageBounds() {
        return page.getBounds();
    }

    private void relayout() {
        revalidate();
        repaint();
    }

    // windows with child overlap must repaint the capsules when the canvas repaints
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 640);
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        int half = SplitDivider.HIT / 2;
        history.setVisible(historyOpen);
        historyDivider.setVisible(historyOpen);
        pdf.setVisible(pdfOpen);
        pdfDivider.setVisible(pdfOpen);
        // In split mode the Canvas really owns a smaller viewport: panels never overlap it.
        // Ratio constraints are based on the remaining width after History.
        int hw = historyOpen ? Math.min(SplitMath.panelWidth(historyRatio, w), Math.max(0, w - 340)) : 0;
        int remaining = Math.max(0, w - hw);
        int pw = pdfOpen ? Math.max(0, Math.min(SplitMath.panelWidth(pdfRatio, w),
                Math.max(0, remaining - (pdfOverlay ? 0 : 250)))) : 0;
        if (historyOpen) {
            history.setBounds(0, 0, hw, h);
            historyDivider.setBounds(hw - half, 0, SplitDivider.HIT, h);
        }
        if (pdfOpen) {
            pdf.setBounds(w - pw, 0, pw, h);
            pdfDivider.setBounds(w - pw - half, 0, SplitDivider.HIT, h);
        }
        int canvasWidth = Math.max(0, w - hw - (pdfOpen && !pdfOverlay ? pw : 0));
        page.setBounds(hw, 0, canvasWidth, h);
        page.doLayout();
        // Keep the same ink in the centre of the visible viewport when splitting / resizing.
        if (lastPageWidth > 0 && lastPageWidth != canvasWidth && page.isShowing()) {
            canvas.panBy((canvasWidth - lastPageWidth) / 2.0, 0);
        }
        lastPageWidth = canvasWidth;

        int m = GlassBar.MARGIN;
        Dimension hb = historyBar.getPreferredSize();
        historyBar.setBounds(BoardTheme.HISTORY_LEFT - m, BoardTheme.TOP - m, hb.width, hb.height);
        Dimension hc = historyClose.getPreferredSize();
        historyClose.setVisible(historyOpen);
        historyClose.setBounds(BoardTheme.HISTORY_LEFT + historyBar.capsuleWidth() + 8 - m, BoardTheme.TOP - m,
                hc.width, hc.height);
        Dimension pb = pdfBar.getPreferredSize();
        pdfBar.setVisible(true);
        pdfBar.setBounds(w - BoardTheme.SIDE - pdfBar.capsuleWidth() - m, BoardTheme.TOP - m, pb.width, pb.height);
        Dimension pc = pdfClose.getPreferredSize();
        pdfClose.setVisible(pdfOpen);
        pdfClose.setBounds(w - BoardTheme.SIDE - pdfBar.capsuleWidth() - pdfClose.capsuleWidth() - 13 - m, BoardTheme.TOP - m,
                pc.width, pc.height);
        if (titleStrip != null) {
            titleStrip.setBounds(0, 0, w, BoardTheme.TITLEBAR_H);
        }
        ghost.setBounds(0, 0, w, h);
    }

    // what the top capsules see: panels as flat colour, the canvas content where it is
    private void paintBackdrop(Graphics2D g) {
        if (history.isVisible()) {
            g.setColor(BoardTheme.PANEL);
            g.fill(history.getBounds());
        }
        if (pdf.isVisible()) {
            g.setColor(Color.WHITE);
            g.fill(pdf.getBounds());
        }
        if (canvas.getWidth() <= 0) {
            return;
        }
        Point c = SwingUtilities.convertPoint(canvas, 0, 0, this);
        Graphics2D cg = (Graphics2D) g.create();
        cg.clip(page.getBounds());
        cg.clipRect(c.x, c.y, canvas.getWidth(), canvas.getHeight());
        cg.translate(c.x, c.y);
        canvas.paintContent(cg);
        cg.dispose();
    }

    private long backdropVersion() {
        long v = canvas.contentVersion();
        v = v * 31 + (historyOpen ? 1 : 0) + (pdfOpen ? 2 : 0);
        return v * 31 + page.getX() * 131L + page.getWidth();
    }

    @Override public void importIntoPdf(HistoryItem item) {
        if (pdf instanceof PdfWorkspacePort panel) {
            setPdfOpen(true);
            panel.addHistoryItem(item);
        }
    }
    @Override public void deleteHistory(HistoryItem item) { deleteHistory.apply(item.getId()); }

    @Override public boolean dropped(HistoryItem item, BufferedImage picture, Point screenTopLeft) {
        ghost.clear();
        if (pdf instanceof PdfWorkspacePort panel && pdfOpen && pdf.isShowing()) {
            Point onPdf = new Point(screenTopLeft);
            SwingUtilities.convertPointFromScreen(onPdf, pdf);
            if (onPdf.x >= 0 && onPdf.x < pdf.getWidth() && onPdf.y >= 0 && onPdf.y < pdf.getHeight()) {
                return panel.dropHistoryItem(item, screenTopLeft);
            }
        }
        return dropped(picture, screenTopLeft);
    }

    // ---- dragging a history row onto the canvas

    @Override
    public void dragging(BufferedImage picture, Point screenTopLeft) {
        Point p = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(p, this);
        ghost.showPicture(picture, p);
    }

    @Override
    public boolean dropped(BufferedImage picture, Point screenTopLeft) {
        ghost.clear();
        Point onCanvas = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(onCanvas, canvas);
        Point onPage = new Point(screenTopLeft);
        SwingUtilities.convertPointFromScreen(onPage, page);
        if (!canvas.isShowing() || onPage.x < 0 || onPage.x >= page.getWidth()
                || onPage.y < 0 || onPage.y >= page.getHeight()) {
            return false;  // let go over the panel: nothing happens
        }
        canvas.addPicture(picture, onCanvas);
        return true;
    }

    // picture following the mouse while a history row is dragged
    private static final class Ghost extends JComponent {
        private BufferedImage picture;
        private Point at;

        void showPicture(BufferedImage picture, Point at) {
            this.picture = picture;
            this.at = at;
            repaint();
        }

        void clear() {
            picture = null;
            repaint();
        }

        @Override
        public boolean contains(int x, int y) {
            return false;  // never takes the mouse
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (picture == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.8f));
            g2.drawImage(picture, at.x, at.y, picture.getWidth() / 2, picture.getHeight() / 2, null);
            g2.dispose();
        }
    }

    // mac: the canvas runs under a clear title bar, this strip moves the window
    private static final class TitleStrip extends JComponent {
        private Point from;
        private Point windowFrom;

        TitleStrip() {
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                    if (!SwingUtilities.isLeftMouseButton(e) || w == null) {
                        return;
                    }
                    if (e.getClickCount() == 2 && w instanceof Frame) {
                        Frame f = (Frame) w;
                        f.setExtendedState(f.getExtendedState() ^ Frame.MAXIMIZED_BOTH);
                        from = null;
                        return;
                    }
                    from = e.getLocationOnScreen();
                    windowFrom = w.getLocation();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Window w = SwingUtilities.getWindowAncestor(TitleStrip.this);
                    if (from == null || w == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    w.setLocation(windowFrom.x + now.x - from.x, windowFrom.y + now.y - from.y);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    from = null;
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }
    }

    // for tests
    Component pageComponent() {
        return page;
    }
}
