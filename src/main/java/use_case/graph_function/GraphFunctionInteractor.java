package use_case.graph_function;

import entity.GraphData;
import entity.LatexFunctionParser;
import entity.LatexParseException;
import entity.MathFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Graphs a LaTeX function: parses it, samples it over the x range, picks a sensible y window,
 * and asks the renderer (QuickChart) for the image.
 */
public class GraphFunctionInteractor implements GraphFunctionInputBoundary {
    static final int SAMPLE_COUNT = 400;

    /** A y range wider than this many times the central 90% of values is treated as blow-up. */
    private static final double OUTLIER_RATIO = 10;

    private final LatexFunctionParser parser;
    private final GraphRendererGateway renderer;
    private final GraphFunctionOutputBoundary presenter;

    public GraphFunctionInteractor(LatexFunctionParser parser,
                                   GraphRendererGateway renderer,
                                   GraphFunctionOutputBoundary presenter) {
        this.parser = parser;
        this.renderer = renderer;
        this.presenter = presenter;
    }

    @Override
    public void execute(GraphFunctionInputData inputData) {
        String latex = inputData.getLatex();
        if (latex == null || latex.trim().isEmpty()) {
            presenter.prepareFailView("No expression to graph.");
            return;
        }
        double xMin = inputData.getXMin();
        double xMax = inputData.getXMax();
        if (!(xMin < xMax)) {
            presenter.prepareFailView("The x range is invalid: x min must be less than x max.");
            return;
        }
        boolean yAutomatic = inputData.isYAutomatic();
        double yMin = inputData.getYMin();
        double yMax = inputData.getYMax();
        if (!yAutomatic && !(yMin < yMax)) {
            presenter.prepareFailView("The y range is invalid: y min must be less than y max.");
            return;
        }

        MathFunction function;
        try {
            function = parser.parse(latex.trim());
        } catch (LatexParseException e) {
            presenter.prepareFailView("Cannot graph \"" + latex + "\": " + e.getMessage());
            return;
        }

        GraphData graphData = yAutomatic ? sample(function, xMin, xMax) : sample(function, xMin, xMax, yMin, yMax);
        if (graphData == null) {
            presenter.prepareFailView("\"" + latex + "\" is undefined for every x in ["
                    + xMin + ", " + xMax + "].");
            return;
        }
        if (!yAutomatic && !hasVisiblePoint(graphData)) {
            presenter.prepareFailView("The graph of \"" + latex + "\" is outside the y range ["
                    + yMin + ", " + yMax + "] for every x in [" + xMin + ", " + xMax + "].");
            return;
        }

        try {
            byte[] image = renderer.render(graphData);
            presenter.prepareSuccessView(new GraphFunctionOutputData(latex, image));
        } catch (GraphRenderException e) {
            presenter.prepareFailView("Could not draw the graph: " + e.getMessage());
        }
    }

    /** Samples with an automatic y range. Returns null if the function has no finite value in the range. */
    GraphData sample(MathFunction function, double xMin, double xMax) {
        return sample(function, xMin, xMax, Double.NaN, Double.NaN);
    }

    /**
     * Samples the function over [xMin, xMax] for the window [yMin, yMax]; NaN bounds mean an
     * automatic y range. Returns null if the function has no finite value in the x range.
     */
    GraphData sample(MathFunction function, double xMin, double xMax, double fixedYMin, double fixedYMax) {
        double[] xs = new double[SAMPLE_COUNT + 1];
        double[] ys = new double[SAMPLE_COUNT + 1];
        List<Double> finite = new ArrayList<>();
        for (int i = 0; i <= SAMPLE_COUNT; i++) {
            xs[i] = xMin + (xMax - xMin) * i / SAMPLE_COUNT;
            ys[i] = function.evaluate(xs[i]);
            if (Double.isFinite(ys[i])) {
                finite.add(ys[i]);
            } else {
                ys[i] = Double.NaN;
            }
        }
        if (finite.isEmpty()) {
            return null;
        }

        boolean automatic = Double.isNaN(fixedYMin) || Double.isNaN(fixedYMax);
        double[] window = automatic ? yWindow(finite) : new double[]{fixedYMin, fixedYMax};
        double yMin = window[0];
        double yMax = window[1];

        // Hide points outside the window and break the line at jumps (asymptotes like tan x or 1/x).
        List<Double> outX = new ArrayList<>();
        List<Double> outY = new ArrayList<>();
        double previous = Double.NaN;
        for (int i = 0; i < xs.length; i++) {
            double y = ys[i];
            if (y < yMin || y > yMax) {
                y = Double.NaN;
            }
            if (!Double.isNaN(y) && !Double.isNaN(previous)
                    && Math.abs(y - previous) > 0.5 * (yMax - yMin)) {
                outX.add((xs[i - 1] + xs[i]) / 2);
                outY.add(Double.NaN);
            }
            outX.add(xs[i]);
            outY.add(y);
            previous = y;
        }
        return new GraphData(function.getLatex(), toArray(outX), toArray(outY), xMin, xMax, yMin, yMax);
    }

    private static boolean hasVisiblePoint(GraphData data) {
        for (double y : data.getYs()) {
            if (!Double.isNaN(y)) {
                return true;
            }
        }
        return false;
    }

    /** Chooses [yMin, yMax]: the full range, or the central range when a few values blow up. */
    private static double[] yWindow(List<Double> finite) {
        double[] sorted = new double[finite.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = finite.get(i);
        }
        Arrays.sort(sorted);
        double lo = sorted[0];
        double hi = sorted[sorted.length - 1];
        double p5 = sorted[(int) Math.floor(0.05 * (sorted.length - 1))];
        double p95 = sorted[(int) Math.ceil(0.95 * (sorted.length - 1))];
        double central = p95 - p5;
        if (central > 0 && hi - lo > OUTLIER_RATIO * central) {
            lo = Math.max(lo, p5 - central);
            hi = Math.min(hi, p95 + central);
        }
        if (hi - lo < 1e-9) {
            lo -= 1;
            hi += 1;
        }
        double pad = 0.05 * (hi - lo);
        return new double[]{lo - pad, hi + pad};
    }

    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
