package view.board.doc;

import pdf_editor.use_case.PreviewZoomModel;
import view.board.BoardTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** Read-only preview with fit-to-window, incremental zoom, drag-to-pan and fast scrolling. */
public final class WordPreviewDialog {
    private WordPreviewDialog() { }

    public static void show(Component parent, List<BufferedImage> images) {
        Objects.requireNonNull(images);
        if (images.isEmpty()) return;
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "PDF preview", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(245, 247, 250));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        heading.setOpaque(false);
        heading.add(new JLabel(DocumentGlyphs.icon(20)));
        JLabel title = new JLabel("PDF preview"); title.setFont(BoardTheme.font(17f));
        heading.add(title); header.add(heading, BorderLayout.WEST);
        JLabel counter = new JLabel(); counter.setFont(BoardTheme.font(12f));
        header.add(counter, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        PreviewPage view = new PreviewPage();
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 226, 235)));
        scroll.getViewport().setBackground(new Color(233, 238, 245));
        scroll.getVerticalScrollBar().setUnitIncrement(96);
        scroll.getHorizontalScrollBar().setUnitIncrement(96);
        scroll.getVerticalScrollBar().setBlockIncrement(400);
        scroll.getHorizontalScrollBar().setBlockIncrement(400);
        root.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(12, 0)); controls.setOpaque(false);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); navigation.setOpaque(false);
        JButton back = pill("‹"); back.setToolTipText("Previous page");
        JButton next = pill("›"); next.setToolTipText("Next page");
        JLabel current = new JLabel(); current.setFont(BoardTheme.font(12f));
        navigation.add(back);navigation.add(current);navigation.add(next);
        controls.add(navigation, BorderLayout.WEST);

        JPanel zoomControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        zoomControls.setOpaque(false);
        JButton fit = pill("Fit");
        JButton minus = pill("−");
        JButton plus = pill("+");
        JLabel zoomLabel = new JLabel("100%"); zoomLabel.setFont(BoardTheme.font(12f));
        JSlider slider = new JSlider(10, 300, 100);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(100, 26));
        zoomControls.add(fit); zoomControls.add(minus); zoomControls.add(slider);
        zoomControls.add(zoomLabel);zoomControls.add(plus);
        controls.add(zoomControls, BorderLayout.EAST);
        root.add(controls, BorderLayout.SOUTH);

        final int[] index = {0};
        final boolean[] updating = {false};
        java.util.function.DoubleConsumer setZoom = amount -> {
            view.setZoom(amount);
            updating[0] = true;
            slider.setValue((int) Math.round(view.zoom() * 100));
            zoomLabel.setText(Math.round(view.zoom() * 100) + "%");
            updating[0] = false;
        };
        Runnable fitPage = () -> setZoom.accept(PreviewZoomModel.fit(
                images.get(index[0]).getWidth(), images.get(index[0]).getHeight(),
                scroll.getViewport().getWidth(), scroll.getViewport().getHeight()));
        Runnable render = () -> {
            view.setPage(images.get(index[0]));
            current.setText("Page " + (index[0] + 1) + " / " + images.size());
            counter.setText(images.size() == 1 ? "1 page" : images.size() + " pages");
            back.setEnabled(index[0] > 0); next.setEnabled(index[0] < images.size() - 1);
            fitPage.run();
            scroll.getViewport().setViewPosition(new Point(0, 0));
        };
        back.addActionListener(e -> { if (index[0] > 0) { index[0]--; render.run(); } });
        next.addActionListener(e -> { if (index[0] < images.size() - 1) { index[0]++; render.run(); } });
        minus.addActionListener(e -> setZoom.accept(view.zoom() / 1.2));
        plus.addActionListener(e -> setZoom.accept(view.zoom() * 1.2));
        fit.addActionListener(e -> fitPage.run());
        slider.addChangeListener(e -> { if (!updating[0]) setZoom.accept(slider.getValue() / 100.0); });
        scroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                setZoom.accept(view.zoom() * Math.pow(1.15, -e.getPreciseWheelRotation()));
                e.consume();
            }
        });

        // A read-only preview can pan with a drag, and its scrollbar wheel jumps by
        // meaningful distances rather than 22 pixels per notch.
        MouseAdapter drag = new MouseAdapter() {
            private Point from;
            @Override public void mousePressed(MouseEvent e) { from = e.getPoint(); }
            @Override public void mouseReleased(MouseEvent e) { from = null; }
            @Override public void mouseDragged(MouseEvent e) {
                if (from == null) return;
                JViewport viewport = scroll.getViewport();
                Point pos = viewport.getViewPosition();
                pos.translate(from.x - e.getX(), from.y - e.getY());
                pos.x = Math.max(0, Math.min(pos.x, Math.max(0, view.getWidth() - viewport.getWidth())));
                pos.y = Math.max(0, Math.min(pos.y, Math.max(0, view.getHeight() - viewport.getHeight())));
                viewport.setViewPosition(pos);
            }
        };
        view.addMouseListener(drag);
        view.addMouseMotionListener(drag);
        view.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        dialog.setContentPane(root);
        dialog.setSize(900, 800);
        dialog.setLocationRelativeTo(parent);
        // The viewport has no valid dimensions until display layout completes.
        SwingUtilities.invokeLater(render);
        dialog.setVisible(true);
    }

    private static JButton pill(String text) {
        JButton button = new JButton(text);
        button.setFont(BoardTheme.font(12f));
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(202, 213, 227), 1, true),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        return button;
    }

    /** Draw the source directly at current scale: avoids repeated ImageIcon resampling. */
    static final class PreviewPage extends JComponent implements Scrollable {
        private BufferedImage page;
        private double zoom = 1;
        public double zoom() { return zoom; }
        public void setPage(BufferedImage page) {
            this.page = Objects.requireNonNull(page);
            revalidate(); repaint();
        }
        public void setZoom(double zoom) {
            this.zoom = PreviewZoomModel.clamp(zoom);
            revalidate(); repaint();
        }
        @Override public Dimension getPreferredSize() {
            return page == null ? new Dimension(500, 600) : new Dimension(
                    Math.max(1, (int) Math.round(page.getWidth() * zoom)) + 44,
                    Math.max(1, (int) Math.round(page.getHeight() * zoom)) + 44);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (page == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int w = Math.max(1, (int) Math.round(page.getWidth() * zoom));
                int h = Math.max(1, (int) Math.round(page.getHeight() * zoom));
                int x = Math.max(22, (getWidth() - w) / 2), y = 22;
                g.setColor(new Color(0, 0, 0, 28));g.fillRect(x + 3, y + 4, w, h);
                g.setColor(Color.WHITE);g.fillRect(x, y, w, h);
                g.drawImage(page, x, y, w, h, null);
            } finally { g.dispose(); }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 650); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int axis, int direction) { return 96; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int axis, int direction) { return 400; }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && getPreferredSize().width <= viewport.getWidth();
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && getPreferredSize().height <= viewport.getHeight();
        }
    }
}
