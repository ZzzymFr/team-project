package view.answer;

import entity.Stroke;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

// which strokes are calculated formulas and what their answers are
public class FormulaBook {
    private final Map<Long, Formula> formulas = new LinkedHashMap<>();
    private final AtomicLong ids;  // shared with copies so undo never reuses an id

    public FormulaBook() {
        this(new AtomicLong());
    }

    private FormulaBook(AtomicLong ids) {
        this.ids = ids;
    }

    // new formula; drops any old one sharing strokes with it
    public Formula add(Collection<Stroke> strokes, long revision) {
        Set<Stroke> picked = new LinkedHashSet<>(strokes);
        formulas.values().removeIf(f -> !Collections.disjoint(f.getStrokes(), picked));
        Formula f = new Formula(ids.incrementAndGet(), picked, revision, null);
        formulas.put(f.getId(), f);
        return f;
    }

    public Formula get(long id) {
        return formulas.get(id);
    }

    public List<Formula> all() {
        return Collections.unmodifiableList(new ArrayList<>(formulas.values()));
    }

    public boolean isBound(Stroke s) {
        return holding(s) != null;
    }

    // formula the stroke is part of, null if loose
    public Formula holding(Stroke s) {
        for (Formula f : formulas.values()) {
            if (f.getStrokes().contains(s)) {
                return f;
            }
        }
        return null;
    }

    public void put(Formula f) {
        formulas.put(f.getId(), f);
    }

    public void remove(long id) {
        formulas.remove(id);
    }

    public FormulaBook copy() {
        FormulaBook copy = new FormulaBook(ids);
        copy.formulas.putAll(formulas);
        return copy;
    }

    // ink changed: returns ids of formulas that lost or gained strokes (incl. removed ones)
    public Set<Long> strokesReplaced(Collection<Stroke> removed, Collection<Stroke> added, LongSupplier revisions) {
        Set<Stroke> gone = new LinkedHashSet<>(removed);
        Map<Long, Rectangle> before = new HashMap<>();
        Map<Long, Set<Stroke>> after = new LinkedHashMap<>();
        Set<Long> changed = new LinkedHashSet<>();
        for (Formula f : formulas.values()) {
            before.put(f.getId(), AnswerLayout.bounds(f.getStrokes()));
            Set<Stroke> kept = new LinkedHashSet<>(f.getStrokes());
            if (kept.removeAll(gone)) {
                changed.add(f.getId());
            }
            after.put(f.getId(), kept);
        }
        for (Stroke s : added) {
            Rectangle box = AnswerLayout.bounds(List.of(s));
            if (box == null) {
                continue;
            }
            Formula home = homeOf(box, gone, before);
            if (home != null) {
                after.get(home.getId()).add(s);
                changed.add(home.getId());
            }
        }
        for (long id : changed) {
            Set<Stroke> strokes = after.get(id);
            if (strokes.isEmpty()) {
                formulas.remove(id);
            } else {
                formulas.put(id, formulas.get(id).with(strokes, revisions.getAsLong(), null));
            }
        }
        return changed;
    }

    // formula a stroke lands on: the one it was cut out of, else the closest. null = none
    private Formula homeOf(Rectangle box, Set<Stroke> gone, Map<Long, Rectangle> before) {
        Formula closest = null;
        double closestGap = 0;
        for (Formula f : formulas.values()) {
            // judge against the old box so erased pieces still count
            Rectangle old = before.get(f.getId());
            if (!AnswerLayout.joins(old, f.getCharHeight(), box)) {
                continue;
            }
            if (wasCutFrom(f, gone, box)) {
                return f;
            }
            double gap = AnswerLayout.gap(old, box);
            if (closest == null || gap < closestGap) {
                closest = f;
                closestGap = gap;
            }
        }
        return closest;
    }

    // box sits inside a stroke the formula just lost = leftover of an erase
    private static boolean wasCutFrom(Formula f, Set<Stroke> gone, Rectangle box) {
        for (Stroke s : f.getStrokes()) {
            Rectangle whole = gone.contains(s) ? AnswerLayout.bounds(List.of(s)) : null;
            if (whole != null && whole.getMinX() <= box.getMinX() && whole.getMaxX() >= box.getMaxX()
                    && whole.getMinY() <= box.getMinY() && whole.getMaxY() >= box.getMaxY()) {
                return true;
            }
        }
        return false;
    }

    // whole formula moved/scaled: same content, keep revision
    public void moveWhole(long id, Map<Stroke, Stroke> copies, UnaryOperator<CanvasAnswer> answerChange) {
        Formula f = formulas.get(id);
        if (f == null) {
            return;
        }
        Set<Stroke> moved = new LinkedHashSet<>();
        for (Stroke s : f.getStrokes()) {
            moved.add(copies.getOrDefault(s, s));
        }
        CanvasAnswer answer = f.getAnswer() == null ? null : answerChange.apply(f.getAnswer());
        formulas.put(id, f.with(moved, f.getRevision(), answer));
    }
}
