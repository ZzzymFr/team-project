package use_case.evaluate;

import java.util.ArrayList;
import java.util.List;

// roots + interval ends too small to trust go out as 0
final class NoiseFreeOutput implements EvaluateOutputBoundary {
    private final EvaluateOutputBoundary next;

    NoiseFreeOutput(EvaluateOutputBoundary next) {
        this.next = next;
    }

    @Override
    public void prepareFormulaShape(FormulaShape shape) {
        next.prepareFormulaShape(shape);
    }

    @Override
    public void prepareValuesView(List<ValueOutputData> values) {
        next.prepareValuesView(values);
    }

    @Override
    public void prepareSolutionsView(String variable, List<Double> solutions) {
        next.prepareSolutionsView(variable, zeroed(solutions));
    }

    @Override
    public void prepareIntervalsView(String variable, List<IntervalOutputData> intervals) {
        List<Double> ends = new ArrayList<>();
        for (IntervalOutputData interval : intervals) {
            ends.add(interval.getLow());
            ends.add(interval.getHigh());
        }
        List<Double> clean = zeroed(ends);
        List<IntervalOutputData> out = new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            IntervalOutputData interval = intervals.get(i);
            out.add(new IntervalOutputData(clean.get(2 * i), interval.isLowIncluded(), clean.get(2 * i + 1),
                    interval.isHighIncluded(), interval.isPoint()));
        }
        next.prepareIntervalsView(variable, out);
    }

    @Override
    public void prepareExcludedView(String variable, List<Double> excluded) {
        next.prepareExcludedView(variable, zeroed(excluded));
    }

    @Override
    public void prepareAlwaysTrueView(String variable) {
        next.prepareAlwaysTrueView(variable);
    }

    @Override
    public void prepareFunctionDefinedView(String definition) {
        next.prepareFunctionDefinedView(definition);
    }

    @Override
    public void prepareExpressionView(ExprOutputData expression) {
        next.prepareExpressionView(expression);
    }

    @Override
    public void prepareUncalculableView(UncalculableOutputData why) {
        next.prepareUncalculableView(why);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        next.prepareFailView(errorMessage);
    }

    // 2 different tiny ones side by side are real, not noise
    private static List<Double> zeroed(List<Double> numbers) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            boolean apart = tinyAndApart(numbers, i, i - 1) || tinyAndApart(numbers, i, i + 1);
            out.add(apart ? numbers.get(i) : NoiseFilter.zeroIfTiny(numbers.get(i)));
        }
        return out;
    }

    private static boolean tinyAndApart(List<Double> numbers, int i, int other) {
        return other >= 0 && other < numbers.size() && NoiseFilter.isTiny(numbers.get(other))
                && !numbers.get(other).equals(numbers.get(i));
    }
}
