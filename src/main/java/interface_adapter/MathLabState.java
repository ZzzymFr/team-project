package interface_adapter;

import entity.symbolic.TextRun;

import java.util.List;

public class MathLabState {
    private String expression = "";

    private String resultText = "";

    private String errorText = "";

    private List<TextRun> resultRuns;  // canvas text w/ raised exponents, null for plain answers

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
        this.resultRuns = null;
    }

    public List<TextRun> getResultRuns() {
        return resultRuns;
    }

    public void setResultRuns(List<TextRun> resultRuns) {
        this.resultRuns = resultRuns;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }
}
