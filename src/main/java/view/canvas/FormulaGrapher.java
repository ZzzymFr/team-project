package view.canvas;

import interface_adapter.graph.AxisRangeParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import view.glass.GlassTextField;
import view.stack.Fonts;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Font;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;

/**
 * Plots a recognised formula (US3): sends the LaTeX and the x and y ranges to the graph use case
 * through {@link GraphController} and listens to the {@link GraphViewModel}: a graph is shown as a
 * card beside the formula, an error as a red message over the canvas. Holds the two range boxes;
 * pressing Enter in one graphs the last formula again with the new range.
 */
public class FormulaGrapher implements PropertyChangeListener {
    private static final int SUCCESS_MESSAGE_MILLIS = 3000;
    private static final int ERROR_MESSAGE_MILLIS = 7000;

    /** Where the grapher reports what it is doing. */
    interface Messages {
        void show(String text, boolean error, int hideAfterMillis);
    }

    private final SharedCanvas sharedCanvas;
    private final GraphController controller;
    private final GraphViewModel viewModel;
    private final GlassTextField xRangeField = new GlassTextField(7, "-10, 10");
    private final GlassTextField yRangeField = new GlassTextField(7, "auto");
    private Messages messages = (text, error, hideAfterMillis) -> { };
    private String graphedLatex;
    private Rectangle graphedFormula;  // where the formula being graphed is (world coordinates)

    public FormulaGrapher(SharedCanvas sharedCanvas, GraphController controller, GraphViewModel viewModel) {
        this.sharedCanvas = sharedCanvas;
        this.controller = controller;
        this.viewModel = viewModel;
        viewModel.addPropertyChangeListener(this);
        xRangeField.setToolTipText("x range, e.g. -5, 5. Empty: -10 to 10.");
        yRangeField.setToolTipText("y range, e.g. -2, 8. Empty or auto: chosen from the function.");
        xRangeField.addActionListener(e -> graphAgain());
        yRangeField.addActionListener(e -> graphAgain());
    }

    void setMessages(Messages messages) {
        this.messages = messages;
    }

    /** The range boxes and their labels, for the Math tab of the ribbon. */
    JComponent[] controls() {
        return new JComponent[]{axisLabel("x"), xRangeField, axisLabel("y"), yRangeField};
    }

    private static JLabel axisLabel(String axis) {
        JLabel label = new JLabel(axis);
        label.setFont(Fonts.ui(Font.ITALIC, 16f));
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 0));
        return label;
    }

    /** Types into the range boxes, as the user would. */
    void setRangeText(String xRange, String yRange) {
        xRangeField.setText(xRange);
        yRangeField.setText(yRange);
    }

    private void graphAgain() {
        if (graphedLatex != null) {
            graph(graphedLatex, graphedFormula);
        }
    }

    /**
     * Graphs the formula; the card goes beside {@code formula} (world coordinates). The result
     * arrives through the view model.
     *
     * @return false if a range box has a typo: that is reported and nothing is graphed
     */
    boolean graph(String latex, Rectangle formula) {
        double[] xRange;
        double[] yRange;
        try {
            xRange = AxisRangeParser.parse(xRangeField.getText(), "x");
            yRange = AxisRangeParser.parse(yRangeField.getText(), "y");
        } catch (IllegalArgumentException e) {
            messages.show(e.getMessage(), true, ERROR_MESSAGE_MILLIS);
            return false;
        }
        graphedLatex = latex;
        graphedFormula = formula;
        messages.show("Drawing the graph of " + latex + "…", false, 0);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                controller.execute(latex, xRange, yRange);  // calls QuickChart; the presenter updates the view model
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    messages.show(reason, true, ERROR_MESSAGE_MILLIS);
                }
            }
        }.execute();
        return true;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // The presenter may fire from the worker thread; Swing must be updated on the event thread.
        SwingUtilities.invokeLater(() -> show(viewModel.getState()));
    }

    private void show(GraphState state) {
        if (state.getError() != null) {
            messages.show(state.getError(), true, ERROR_MESSAGE_MILLIS);
        } else if (state.getGraphImage() != null) {
            sharedCanvas.showGraph(state.getGraphImage(), graphedFormula);
            messages.show("Graph of " + state.getLatex(), false, SUCCESS_MESSAGE_MILLIS);
        }
    }
}
