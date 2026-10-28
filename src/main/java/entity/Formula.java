package entity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// strokes that were calculated together, plus their answer (null = none yet)
public final class Formula {
    private final long id;
    private final Set<Stroke> strokes;
    private final double charHeight;
    private final long revision;
    private final CanvasAnswer answer;

    Formula(long id, Set<Stroke> strokes, long revision, CanvasAnswer answer) {
        this.id = id;
        this.strokes = Collections.unmodifiableSet(new LinkedHashSet<>(strokes));
        this.charHeight = AnswerLayout.charHeight(strokes);
        this.revision = revision;
        this.answer = answer;
    }

    public long getId() {
        return id;
    }

    public Set<Stroke> getStrokes() {
        return strokes;
    }

    public double getCharHeight() {
        return charHeight;
    }

    // bumps whenever the ink changes, so old results can be spotted
    public long getRevision() {
        return revision;
    }

    public CanvasAnswer getAnswer() {
        return answer;
    }

    public Formula withAnswer(CanvasAnswer answer) {
        return new Formula(id, strokes, revision, answer);
    }

    Formula with(Set<Stroke> newStrokes, long newRevision, CanvasAnswer newAnswer) {
        return new Formula(id, newStrokes, newRevision, newAnswer);
    }
}
