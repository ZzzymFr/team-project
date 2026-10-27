package interface_adapter.evaluate;

import use_case.evaluate.EvaluateInputBoundary;
import use_case.evaluate.EvaluateInputData;

public class EvaluateController {
    private final EvaluateInputBoundary interactor;

    public EvaluateController(EvaluateInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void execute(String expression) {
        interactor.execute(new EvaluateInputData(expression));
    }
}
