package view.board.doc;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.JViewport;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Rectangle;

/** Keeps the A4 sheet centered like a Word document while still allowing scrolling at high zoom. */
final class CenteredDesk extends JPanel implements Scrollable {
    private final JComponent page;
    CenteredDesk(JComponent page) {
        super(new GridBagLayout());
        this.page = java.util.Objects.requireNonNull(page);
        setBackground(DocEditorPanel.DESK_COLOR);
        add(page);
    }
    @Override public Dimension getPreferredSize() {
        Dimension size = page.getPreferredSize();
        return new Dimension(size.width + 20, size.height + 24);
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle viewport, int direction, int sign) { return 20; }
    @Override public int getScrollableBlockIncrement(Rectangle viewport, int direction, int sign) {
        return Math.max(20, viewport.height - 32);
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport parent && parent.getWidth() >= getPreferredSize().width;
    }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
