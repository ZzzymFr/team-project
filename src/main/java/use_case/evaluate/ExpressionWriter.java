package use_case.evaluate;

import entity.symbolic.Expr;

import java.util.List;

// how an Expr gets written is an outer layer thing
public interface ExpressionWriter {
    List<TextRunOutputData> runs(Expr expression);

    String latex(Expr expression);
}
