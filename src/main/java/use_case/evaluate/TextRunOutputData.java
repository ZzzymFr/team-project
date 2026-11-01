package use_case.evaluate;

import java.util.Objects;

// bit of answer text, raised = goes up like an exponent
public final class TextRunOutputData {
    private final String text;
    private final boolean raised;

    public TextRunOutputData(String text, boolean raised) {
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
        return o instanceof TextRunOutputData && ((TextRunOutputData) o).raised == raised
                && ((TextRunOutputData) o).text.equals(text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, raised);
    }
}
