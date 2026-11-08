package view.ribbon;

import org.junit.jupiter.api.Test;
import view.glass.GlassIcons;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Word-like ribbon: tabs that can be greyed out, and buttons that run only while usable. */
class RibbonTest {
    private final Ribbon ribbon = new Ribbon("Math Lab");
    private final AtomicBoolean mathUsable = new AtomicBoolean();
    private final Ribbon.Tab edit = ribbon.addTab("Edit", () -> true);
    private final Ribbon.Tab math = ribbon.addTab("Math", mathUsable::get);

    @Test
    void theFirstTabStartsChosen() {
        assertSame(edit, ribbon.getSelectedTab());
        assertTrue(edit.isSelected());
        assertFalse(math.isSelected());
    }

    @Test
    void aGreyedOutTabCannotBeChosen() {
        math.click();
        assertSame(edit, ribbon.getSelectedTab());
        mathUsable.set(true);
        math.click();
        assertSame(math, ribbon.getSelectedTab());
    }

    @Test
    void theChosenTabGivesWayWhenItIsGreyedOut() {
        mathUsable.set(true);
        math.click();
        mathUsable.set(false);
        ribbon.refresh();
        assertSame(edit, ribbon.getSelectedTab());
    }

    @Test
    void listenersHearWhenAnotherTabIsChosen() {
        java.util.List<Ribbon.Tab> heard = new java.util.ArrayList<>();
        ribbon.addTabListener(heard::add);
        edit.click();
        assertTrue(heard.isEmpty(), "already chosen");
        mathUsable.set(true);
        math.click();
        mathUsable.set(false);
        ribbon.refresh();  // greyed out: Edit takes over
        assertEquals(java.util.List.of(math, edit), heard);
    }

    @Test
    void aGreyedOutButtonDoesNothing() {
        AtomicBoolean usable = new AtomicBoolean();
        AtomicInteger runs = new AtomicInteger();
        RibbonButton button = edit.addButton("Note", GlassIcons::note, runs::incrementAndGet, usable::get);
        assertFalse(button.isEnabled());
        button.doClick();
        assertEquals(0, runs.get());

        usable.set(true);
        assertTrue(button.isEnabled());
        button.doClick();
        assertEquals(1, runs.get());
    }

    @Test
    void quickActionsSitInTheTitleRow() {
        AtomicInteger saves = new AtomicInteger();
        javax.swing.JButton save = ribbon.addQuickAction("Save", GlassIcons::save, java.awt.Color.BLUE,
                saves::incrementAndGet);
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(save, ribbon.getTitleRow()));
        assertEquals("", save.getText(), "just the icon");
        assertEquals("Save", save.getToolTipText());
        assertFalse(save.isBorderPainted(), "no border, like Word");
        assertEquals(java.awt.Color.BLUE, ((QuickButton) save).getIconColor());
        save.doClick();
        assertEquals(1, saves.get());
    }

    @Test
    void aTabsButtonsShareOneGlassBarShownOnlyWithTheTab() {
        mathUsable.set(true);
        RibbonButton note = edit.addButton("Note", GlassIcons::note, () -> { }, () -> true);
        RibbonButton delete = edit.addButton("Delete", GlassIcons::trash, () -> { }, () -> true);
        RibbonButton graph = math.addButton("Graph", GlassIcons::chart, () -> { }, () -> true);
        assertSame(note.getBar(), delete.getBar());
        assertTrue(note.getBar().isVisible());
        assertFalse(graph.getBar().isVisible());
        math.click();
        assertFalse(note.getBar().isVisible());
        assertTrue(graph.getBar().isVisible());
    }

    @Test
    void aControlSitsInAGapOfTheSameBar() {
        RibbonButton before = edit.addButton("Note", GlassIcons::note, () -> { }, () -> true);
        javax.swing.JLabel control = new javax.swing.JLabel("x");
        edit.addControl(control);
        RibbonButton after = edit.addButton("Delete", GlassIcons::trash, () -> { }, () -> true);
        assertSame(before.getBar(), after.getBar());
        ribbon.setSize(1200, ribbon.preferredHeight());
        ribbon.doLayout();
        java.awt.Rectangle bar = before.getBar().getBounds();
        assertTrue(control.getX() > bar.x + view.glass.GlassBar.MARGIN, "after Note");
        assertTrue(control.getY() > bar.y + view.glass.GlassBar.MARGIN, "inside the glass");
    }

    @Test
    void theBarRunsTheWidthOfTheWindowAndIsTallerThanTheTools() {
        RibbonButton note = edit.addButton("Note", GlassIcons::note, () -> { }, () -> true);
        ribbon.setSize(1200, ribbon.preferredHeight());
        ribbon.doLayout();
        view.glass.GlassBar bar = note.getBar();
        int left = bar.getX() + view.glass.GlassBar.MARGIN;
        int right = bar.getX() + bar.getWidth() - view.glass.GlassBar.MARGIN;
        assertTrue(left < 20 && right > 1200 - 20, "from edge to edge, was " + left + ".." + right);
        assertTrue(bar.capsuleHeight() > view.glass.GlassBar.CAPSULE_HEIGHT);
    }
}
