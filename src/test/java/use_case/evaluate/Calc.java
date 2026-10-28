package use_case.evaluate;

import data_access.InMemoryFunctionDataAccessObject;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluatePresenter;

final class Calc {
    private final MathLabViewModel viewModel = new MathLabViewModel();
    private final EvaluateInteractor interactor =
            new EvaluateInteractor(new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel));

    String show(String input) {
        interactor.execute(new EvaluateInputData(input));
        MathLabState state = viewModel.getState();
        if (!state.getErrorText().isEmpty()) {
            return state.getErrorText();
        }
        return state.getResultText();
    }

    // "Uncalculable — why" -> "Uncalculable"
    static String withoutReason(String answer) {
        return answer.startsWith("Uncalculable — ") ? "Uncalculable" : answer;
    }

    static String once(String input) {
        return new Calc().show(input);
    }
}
