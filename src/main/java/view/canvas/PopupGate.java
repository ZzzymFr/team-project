package view.canvas;

import view.answer.EqualsSpot;
import view.answer.Formula;

import java.util.HashSet;
import java.util.Set;

// popup waits till the "=" is written, lasso picks don't have to
final class PopupGate {
    private final Set<Long> picked = new HashSet<>();

    void picked(long formulaId) {
        picked.add(formulaId);
    }

    // latex null = not read / couldn't read
    boolean allows(Formula f, String latex) {
        if (f == null) {
            return false;
        }
        return picked.contains(f.getId()) || EqualsSpot.written(f.getStrokes(), f.getCharHeight(), latex);
    }
}
