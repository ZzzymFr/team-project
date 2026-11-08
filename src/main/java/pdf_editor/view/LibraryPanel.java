package pdf_editor.view;

import pdf_editor.MathLibrary;
import pdf_editor.data_access.MaterialRenderer;
import view.glass.Glass;
import view.glass.GlassButton;
import view.glass.SlimScrollBarUI;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The material library floating at the left of the window: every calculation and graph made so
 * far, as cards. Drag a card onto the paper, or double-click it to put it in the middle of the view.
 */
public final class LibraryPanel extends JPanel {
    private static final Color INK = new Color(28, 28, 30);
    private static final Color TYPE = new Color(0, 122, 255);
    private static final int ARC = 22;
    // cards are drawn on every repaint of the list; materials never change, so their pictures can be kept
    private static final java.util.Map<MathLibrary.Material, ImageIcon> THUMBNAILS = new java.util.WeakHashMap<>();

    private final MathLibrary library;
    private final DefaultListModel<MathLibrary.Material> model = new DefaultListModel<>();
    private final JList<MathLibrary.Material> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final Runnable stopListening;

    /**
     * @param insert puts a material on the paper (double-click)
     * @param close  hides the panel
     */
    public LibraryPanel(MathLibrary library, Consumer<MathLibrary.Material> insert, Runnable close) {
        super(new BorderLayout(0, 10));
        this.library = Objects.requireNonNull(library);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 14, 14, 10));

        JLabel title = new JLabel("Library");
        title.setFont(Fonts.ui(Font.BOLD, 16f));
        title.setForeground(INK);
        JLabel hint = new JLabel("Drag onto the paper, or double-click");
        hint.setFont(Fonts.ui(Font.PLAIN, 12f));
        hint.setForeground(Glass.TEXT_MUTED);
        JPanel titles = new JPanel(new BorderLayout(0, 3));
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(hint, BorderLayout.SOUTH);
        JButton hide = new GlassButton("Close", 28);
        hide.addActionListener(e -> close.run());
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        heading.add(titles, BorderLayout.CENTER);
        heading.add(hide, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        setUpList(insert);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        JLabel empty = new JLabel("<html><div style='text-align:center'>Calculate or graph a formula<br>"
                + "and it shows up here.</div></html>", SwingConstants.CENTER);
        empty.setFont(Fonts.ui(Font.PLAIN, 13f));
        empty.setForeground(Glass.TEXT_MUTED);
        body.setOpaque(false);
        body.add(scroll, "list");
        body.add(empty, "empty");
        add(body, BorderLayout.CENTER);

        stopListening = library.onChange(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    private void setUpList(Consumer<MathLibrary.Material> insert) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(112);
        list.setOpaque(false);
        list.setCellRenderer((owner, item, index, selected, focused) -> card(item, selected));
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            list.setDragEnabled(true);  // throws without a screen
        }
        list.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                MathLibrary.Material m = list.getSelectedValue();
                return m == null ? null : new StringSelection(m.id());
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (e.getClickCount() == 2 && index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    insert.accept(model.get(index));
                }
            }
        });
    }

    private static JComponent card(MathLibrary.Material item, boolean selected) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 9, 4));
        Rounded card = new Rounded(new BorderLayout(0, 7),
                selected ? new Color(0, 122, 255, 30) : new Color(255, 255, 255, 235), 16);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 9, 12));
        if (item != null) {
            JLabel name = new JLabel(item.title());
            name.setFont(Fonts.ui(Font.BOLD, 13f));
            name.setForeground(INK);
            JLabel type = new JLabel(item.isGraph() ? "GRAPH" : item.hasResult() ? "FORMULA + RESULT" : "FORMULA");
            type.setFont(Fonts.ui(Font.BOLD, 10f));
            type.setForeground(TYPE);
            JPanel heading = new JPanel(new BorderLayout(5, 0));
            heading.setOpaque(false);
            heading.add(name, BorderLayout.CENTER);
            heading.add(type, BorderLayout.EAST);
            card.add(heading, BorderLayout.NORTH);
            JLabel content = new JLabel(THUMBNAILS.computeIfAbsent(item,
                    m -> new ImageIcon(MaterialRenderer.render(m, 240, 62))));
            content.setHorizontalAlignment(SwingConstants.LEFT);
            card.add(content, BorderLayout.CENTER);
        }
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void refresh() {
        model.clear();
        for (MathLibrary.Material material : library.items()) {
            model.addElement(material);
        }
        cards.show(body, model.isEmpty() ? "empty" : "list");
    }

    int itemCount() {
        return model.size();
    }

    /** Stops following the library, e.g. when the window closes. */
    public void dispose() {
        stopListening.run();
    }

    /** A frosted card: soft shadow, nearly white body, a hairline edge. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 6;
        int h = getHeight() - 6;
        for (int i = 1; i <= 5; i++) {
            g2.setColor(new Color(0, 0, 0, 7));
            g2.fill(new RoundRectangle2D.Double(3 - i * 0.5, 3 + i * 0.7, w + i, h + i * 0.4, ARC + i, ARC + i));
        }
        RoundRectangle2D shape = new RoundRectangle2D.Double(3, 3, w, h, ARC, ARC);
        g2.setColor(new Color(248, 248, 250, 240));
        g2.fill(shape);
        g2.setColor(new Color(0, 0, 0, 24));
        g2.draw(shape);
        g2.dispose();
        super.paintComponent(g);
    }

    private static final class Rounded extends JPanel {
        private final Color fill;
        private final int arc;

        Rounded(LayoutManager layout, Color fill, int arc) {
            super(layout);
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(new Color(0, 0, 0, 22));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
