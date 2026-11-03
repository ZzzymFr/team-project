package interface_adapter;

import use_case.evaluate.FormulaShape;

// where an answer goes on the canvas
public enum AnswerKind {
    AFTER_EQUALS, BELOW, RIGHT;

    public static AnswerKind of(FormulaShape shape) {
        switch (shape) {
            case ENDS_WITH_EQUALS:
                return AFTER_EQUALS;
            case RELATION:
                return BELOW;
            default:
                return RIGHT;
        }
    }

    public boolean isBelow() {
        return this == BELOW;
    }
}
