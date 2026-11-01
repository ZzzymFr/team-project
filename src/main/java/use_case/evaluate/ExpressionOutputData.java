package use_case.evaluate;

import java.util.List;

// a written out answer (the derivative): latex + canvas text
public final class ExpressionOutputData {
    private final String latex;
    private final List<TextRunOutputData> runs;

    public ExpressionOutputData(String latex, List<TextRunOutputData> runs) {
        this.latex = latex;
        this.runs = runs;
    }

    public String getLatex() {
        return latex;
    }

    public List<TextRunOutputData> getRuns() {
        return runs;
    }

    public static int plainLength(List<TextRunOutputData> runs) {
        int length = 0;
        for (TextRunOutputData run : runs) {
            length += run.getText().length();
        }
        return length;
    }
}
