package view.ribbon;

import view.glass.Glass;
import view.glass.GlassBackdrop;
import view.glass.GlassBar;
import view.glass.GlassFrost;
import view.glass.GlassIcons;
import view.stack.Fonts;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The band across the top of the window, laid out like Word's: a slim row with the file commands
 * at the left (on macOS it doubles as the title bar), a row of tabs, and the commands of the
 * chosen tab. The band is frosted glass over the canvas; the commands sit on it in the same
 * liquid-glass bars as the rest of the window, the drawing tools among them. A tab can be greyed
 * out; while it is, it cannot be chosen, and if it was the chosen one the first tab that can be used
 * takes over.
 */
public final class Ribbon extends JComponent {
    /** The font of the commands. */
    public static final Font FONT = Fonts.ui(Font.PLAIN, 14f);
    private static final Color TEXT = new Color(28, 28, 30);
    private static final Color MUTED = new Color(99, 99, 102);
    private static final Color EDGE = new Color(0, 0, 0, 26);
    private static final int TITLE_HEIGHT = 34;
    private static final int TAB_HEIGHT = 28;
    private static final int BAR_HEIGHT = 64;        // a little taller than a plain glass bar
    private static final int CONTENT_HEIGHT = BAR_HEIGHT + 12;
    private static final int CONTROL_GAP = 8;        // around a control laid into a bar
    private static final int SIDE = 12;
    private static final int REFRESH_MILLIS = 150;   // how often greyed-out states are asked again

    private final GlassFrost frost;
    private final TitleRow titleRow = new TitleRow();
    private final JPanel quickActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, (TITLE_HEIGHT - 28) / 2));
    private final JLabel title;
    private final JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Consumer<Tab>> tabListeners = new ArrayList<>();
    private final Timer refresher = new Timer(REFRESH_MILLIS, e -> refresh());
    private int leadingInset = SIDE - 4;
    private Tab selected;

    /** A ribbon over a plain light background, e.g. for tests. */
    public Ribbon(String title) {
        this(title, g -> {
            g.setColor(Glass.CANVAS);
            g.fill(g.getClipBounds() == null ? new java.awt.Rectangle(0, 0, 4000, 400) : g.getClipBounds());
        });
    }

    /**
     * @param title shown in the middle of the title row, like a window title
     * @param under what the frosted band lies over, in the ribbon's coordinates (the canvas)
     */
    public Ribbon(String title, GlassBackdrop under) {
        setLayout(null);
        setOpaque(false);
        frost = new GlassFrost(under);
        this.title = new JLabel(title, SwingConstants.CENTER);
        this.title.setFont(Fonts.ui(Font.PLAIN, 12.5f));
        this.title.setForeground(MUTED);
        this.title.setVisible(false);  // the system's title bar shows it, unless useAsTitleBar
        quickActions.setOpaque(false);
        titleRow.add(quickActions);
        titleRow.add(this.title);
        tabRow.setOpaque(false);
        add(titleRow);
        add(tabRow);
        refresher.setRepeats(true);
    }

    /** The height the ribbon needs. */
    public int preferredHeight() {
        return TITLE_HEIGHT + TAB_HEIGHT + CONTENT_HEIGHT;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(800, preferredHeight());
    }

    /**
     * A button at the left of the title row, like Word's Save: just the icon in its colour, with no
     * border; the name is its tooltip.
     */
    public JButton addQuickAction(String name, GlassIcons.Glyph icon, Color color, Runnable run) {
        QuickButton button = new QuickButton(name, icon, color);
        button.addActionListener(e -> run.run());
        quickActions.add(button);
        quickActions.revalidate();
        return button;
    }

    /**
     * Makes the title row stand in for the window's title bar (macOS, where the window's content
     * runs under a transparent one): room for the window buttons at the left, and the title in
     * the middle. Elsewhere the system's own title bar shows the title, so the row does not.
     */
    public void useAsTitleBar(int leadingInset) {
        this.leadingInset = leadingInset;
        title.setVisible(true);
        titleRow.revalidate();
    }

    /** The title row, which the window is dragged by when it stands in for the title bar. */
    public JComponent getTitleRow() {
        return titleRow;
    }

    /** Adds a tab at the end; the first one added starts chosen. */
    public Tab addTab(String name, BooleanSupplier enabled) {
        Tab tab = new Tab(name, enabled);
        tabs.add(tab);
        tabRow.add(tab.button);
        if (selected == null) {
            select(tab);
        }
        tabRow.revalidate();
        return tab;
    }

    public List<Tab> getTabs() {
        return Collections.unmodifiableList(tabs);
    }

    public Tab getSelectedTab() {
        return selected;
    }

    /** Called with the newly chosen tab whenever another tab is chosen, by a click or because one was greyed out. */
    public void addTabListener(Consumer<Tab> listener) {
        tabListeners.add(Objects.requireNonNull(listener));
    }

    /** Shows the tab's commands; a greyed-out tab cannot be chosen. */
    public void select(Tab tab) {
        if (!tabs.contains(tab) || !tab.enabled.getAsBoolean()) {
            return;
        }
        boolean changed = selected != tab;
        selected = tab;
        for (Tab t : tabs) {
            for (JComponent part : t.parts) {
                part.setVisible(t == tab);
                if (part instanceof GlassBar) {
                    ((GlassBar) part).clearHover();
                }
            }
        }
        refresh();
        revalidate();
        repaint();
        if (changed) {
            for (Consumer<Tab> listener : new ArrayList<>(tabListeners)) {
                listener.accept(tab);
            }
        }
    }

    /**
     * Asks every tab and command again whether it can be used; a chosen tab that cannot gives way
     * to the first that can. Also runs on its own a few times a second while the ribbon is showing.
     */
    public void refresh() {
        for (Tab tab : tabs) {
            tab.button.refresh();
        }
        if (selected != null && !selected.enabled.getAsBoolean()) {
            for (Tab tab : tabs) {
                if (tab.enabled.getAsBoolean()) {
                    select(tab);
                    return;
                }
            }
        }
        for (Tab tab : tabs) {
            tab.refreshCommands();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresher.start();
    }

    @Override
    public void removeNotify() {
        refresher.stop();
        super.removeNotify();
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        frost.setSize(w, getHeight());
        titleRow.setBounds(0, 0, w, TITLE_HEIGHT);
        tabRow.setBounds(SIDE, TITLE_HEIGHT, Math.max(0, w - 2 * SIDE), TAB_HEIGHT);
        // each tab's bar runs the width of the window; its controls sit in the gaps left for them
        int capsuleTop = TITLE_HEIGHT + TAB_HEIGHT + (CONTENT_HEIGHT - BAR_HEIGHT) / 2;
        for (Tab tab : tabs) {
            GlassBar bar = tab.bar;
            bar.setCapsuleWidth(Math.max(0, w - 2 * SIDE));
            Dimension d = bar.getPreferredSize();
            bar.setBounds(SIDE - GlassBar.MARGIN, capsuleTop - GlassBar.MARGIN, d.width, d.height);
            for (java.util.Map.Entry<JComponent, GlassBar.Item> gap : tab.gaps.entrySet()) {
                Dimension c = gap.getKey().getPreferredSize();
                gap.getKey().setBounds(bar.getX() + bar.itemX(gap.getValue()) + CONTROL_GAP,
                        capsuleTop + (BAR_HEIGHT - c.height) / 2, c.width, c.height);
            }
        }
    }

    /** The frosted band, with a hairline along its bottom edge. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        frost.paintBackdrop(g2);
        g2.setColor(EDGE);
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        g2.dispose();
    }

    /** The title row: file commands at the left, and the title in the middle when it is the title bar. */
    private final class TitleRow extends JPanel {
        TitleRow() {
            super(null);
            setOpaque(false);
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            Dimension quick = quickActions.getPreferredSize();
            quickActions.setBounds(leadingInset, 0, quick.width, TITLE_HEIGHT);
            // centred in the window, unless the commands at the left leave no room there
            int tw = title.getPreferredSize().width;
            int x = Math.max(leadingInset + quick.width + 8, Math.min((w - tw) / 2, w - tw - 8));
            title.setBounds(x, 0, tw, TITLE_HEIGHT);
        }
    }

    /**
     * One tab: its name in the tab row and its commands below, in one liquid-glass bar across the
     * window; other controls (a text box) are laid into gaps in the bar.
     */
    public final class Tab {
        private final String name;
        private final BooleanSupplier enabled;
        private final TabButton button;
        private final GlassBar bar = new GlassBar(frost, FONT);
        private final List<JComponent> parts = new ArrayList<>();
        private final java.util.Map<JComponent, GlassBar.Item> gaps = new java.util.LinkedHashMap<>();
        private final List<RibbonButton> commands = new ArrayList<>();

        private Tab(String name, BooleanSupplier enabled) {
            this.name = Objects.requireNonNull(name);
            this.enabled = Objects.requireNonNull(enabled);
            button = new TabButton(this);
            bar.setCapsuleHeight(BAR_HEIGHT);
            bar.finish();
            addPart(bar);
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled.getAsBoolean();
        }

        public boolean isSelected() {
            return selected == this;
        }

        /** Chooses this tab, as a click on it would. */
        public void click() {
            select(this);
        }

        /** A button with an icon and a label, greyed out while {@code usable} says no. */
        public RibbonButton addButton(String label, GlassIcons.Glyph icon, Runnable run, BooleanSupplier usable) {
            GlassBar.Item item = bar.action(label, icon, run, usable);
            bar.finish();
            RibbonButton command = new RibbonButton(bar, item, label);
            commands.add(command);
            return command;
        }

        /**
         * The tab's glass bar, for what {@link #addButton} does not cover: tool segments and colour
         * swatches. Call {@link GlassBar#finish} after adding to it.
         */
        public GlassBar getBar() {
            return bar;
        }

        /** A thin line between two groups of buttons. */
        public void addSeparator() {
            bar.separator();
            bar.finish();
        }

        /** Any other control, e.g. a text box, laid into a gap in the bar after what is there. */
        public void addControl(JComponent control) {
            gaps.put(control, bar.spacer(control.getPreferredSize().width + 2f * CONTROL_GAP));
            bar.finish();
            addPart(control);
        }

        private void addPart(JComponent part) {
            parts.add(part);
            part.setVisible(isSelected());
            Ribbon.this.add(part, 0);
            Ribbon.this.revalidate();
        }

        private void refreshCommands() {
            for (RibbonButton command : commands) {
                command.refresh();
            }
        }
    }

    /** A tab's name; the chosen tab is bold and underlined in the accent colour, a greyed-out one is faint. */
    private final class TabButton extends JComponent {
        private final Tab tab;
        private boolean hover;

        TabButton(Tab tab) {
            this.tab = tab;
            setFont(FONT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    tab.click();
                }
            });
        }

        void refresh() {
            boolean now = tab.enabled.getAsBoolean();
            if (now != isEnabled()) {
                setEnabled(now);
                setCursor(Cursor.getPredefinedCursor(now ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                setToolTipText(now ? null : tab.name + " is not available now");
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD));
            return new Dimension(fm.stringWidth(tab.name) + 26, TAB_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (hover && isEnabled() && !tab.isSelected()) {
                g2.setColor(new Color(0, 0, 0, 14));
                g2.fill(new RoundRectangle2D.Double(1, 2, getWidth() - 2, getHeight() - 7, 10, 10));
            }
            g2.setFont(tab.isSelected() ? getFont().deriveFont(Font.BOLD) : getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(tab.name);
            int x = (getWidth() - textWidth) / 2;
            int baseline = (getHeight() - 5 - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(isEnabled() ? TEXT : new Color(0, 0, 0, 70));
            g2.drawString(tab.name, x, baseline);
            if (tab.isSelected()) {
                g2.setColor(Glass.ACCENT);
                g2.fill(new RoundRectangle2D.Double(x - 2, getHeight() - 4, textWidth + 4, 3, 3, 3));
            }
            g2.dispose();
        }
    }
}
