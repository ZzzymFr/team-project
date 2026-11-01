package interface_adapter.evaluate;

import entity.symbolic.Expr;
import use_case.evaluate.ExpressionWriter;
import use_case.evaluate.TextRunOutputData;

import java.util.List;

// hooks ExprWriter up to the use case
public final class ExprOutputWriter implements ExpressionWriter {
    @Override
    public List<TextRunOutputData> runs(Expr expression) {
        return ExprWriter.runs(expression);
    }

    @Override
    public String latex(Expr expression) {
        return ExprWriter.latex(expression);
    }
}
