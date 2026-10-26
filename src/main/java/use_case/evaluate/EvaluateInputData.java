package use_case.evaluate;

public class EvaluateInputData {
    private final String expression;

    public EvaluateInputData(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }
}
