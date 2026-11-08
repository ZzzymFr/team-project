package view.ribbon;

import view.glass.GlassBar;

import java.util.Objects;

/** One command on a tab of the ribbon: a button in one of its glass bars. */
public final class RibbonButton {
    private final GlassBar bar;
    private final GlassBar.Item item;
    private final String label;
    private boolean enabled;

    RibbonButton(GlassBar bar, GlassBar.Item item, String label) {
        this.bar = Objects.requireNonNull(bar);
        this.item = Objects.requireNonNull(item);
        this.label = Objects.requireNonNull(label);
        enabled = bar.isEnabled(item);
    }

    public String getLabel() {
        return label;
    }

    /** Whether the command can be used now. */
    public boolean isEnabled() {
        return bar.isEnabled(item);
    }

    /** Runs the command, as a click would; nothing while it is greyed out. */
    public void doClick() {
        bar.activate(item);
    }

    /** The bar it is in. */
    public GlassBar getBar() {
        return bar;
    }

    /** Repaints the bar when the command has just become usable or greyed out. */
    void refresh() {
        boolean now = bar.isEnabled(item);
        if (now != enabled) {
            enabled = now;
            bar.repaint();
        }
    }
}
