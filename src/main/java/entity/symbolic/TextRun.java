package entity.symbolic;

import java.util.Objects;

// bit of answer text, raised = drawn small and up like an exponent
public final class TextRun {
    private final String text;
    private final boolean raised;

    public TextRun(String text, boolean raised) {
        this.text = text;
        this.raised = raised;
    }

    public String getText() {
        return text;
    }

    public boolean isRaised() {
        return raised;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextRun && ((TextRun) o).raised == raised && ((TextRun) o).text.equals(text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, raised);
    }

    @Override
    public String toString() {
        return raised ? "^" + text : text;
    }
}
