package view.canvas;

import interface_adapter.graph.AxisRangeParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import view.glass.GlassButton;
import view.glass.GlassTextField;
import view.stack.Fonts;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;

/**
 * Graph page: plots the function written on the canvas (US3).
 *
 * <p>The Graph button reads the formula (the lasso selection if there is one, otherwise all the
 * ink) with handwriting recognition, sends the LaTeX to the
 * graph use case through {@link GraphController}, and listens to the {@link GraphViewModel}:
 * a graph is shown as a card beside the formula, an error as a red message over the canvas.
 */
public class GraphCanvasPage extends CanvasPage implements PropertyChangeListener {
    private static final int SUCCESS_MESSAGE_MILLIS = 3000;
    private static final int ERROR_MESSAGE_MILLIS = 7000;

    private final GraphController controller;
    private final GraphViewModel viewModel;
    private final GlassButton graphButton = new GlassButton("Graph", 40);
    private final GlassTextField xRangeField = new GlassTextField(7, "-10, 10");
    private final GlassTextField yRangeField = new GlassTextField(7, "auto");
    private Rectangle graphedFormula;  // where the formula being graphed is (world coordinates)

    /** A Graph page without the graph feature: a placeholder in the function area. */
    public GraphCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.controller = null;
        this.viewModel = null;
        setFunctionArea(placeholder("Graph tools go here: plot the function written on the canvas."));
    }

    public GraphCanvasPage(SharedCanvas sharedCanvas, GraphController controller, GraphViewModel viewModel) {
        super(sharedCanvas);
        this.controller = controller;
        this.viewModel = viewModel;
        viewModel.addPropertyChangeListener(this);

        graphButton.setFont(Fonts.ui(Font.PLAIN, 14f));
        graphButton.addActionListener(e -> graph());
        xRangeField.setToolTipText("x range, e.g. -5, 5. Empty: -10 to 10.");
        yRangeField.setToolTipText("y range, e.g. -2, 8. Empty or auto: chosen from the function.");
        xRangeField.addActionListener(e -> graph());  // Enter graphs too
        yRangeField.addActionListener(e -> graph());
        JLabel note = new JLabel("Several formulas? Circle one with the Lasso first.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(axisLabel("x"));
        tools.add(xRangeField);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(axisLabel("y"));
        tools.add(yRangeField);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(graphButton);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(note);
        setFunctionArea(tools);
    }

    /** Types into the range boxes, as the user would. */
    void setRangeText(String xRange, String yRange) {
        xRangeField.setText(xRange);
        yRangeField.setText(yRange);
    }

    private static JLabel axisLabel(String axis) {
        JLabel label = new JLabel(axis);
        label.setFont(Fonts.ui(Font.ITALIC, 16f));
        label.setForeground(new Color(51, 65, 85));
        return label;
    }

    /**
     * Graphs the formula: the one circled with the lasso if there is a selection, otherwise all the
     * ink. The image and the formula's place are taken now, so the card goes beside this formula
     * even if the selection changes while the graph is being drawn. The result arrives through the
     * view model.
     */
    void graph() {
        double[] xRange;
        double[] yRange;
        try {
            xRange = AxisRangeParser.parse(xRangeField.getText(), "x");
            yRange = AxisRangeParser.parse(yRangeField.getText(), "y");
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage(), true, ERROR_MESSAGE_MILLIS);  // a typo in a range box
            return;
        }
        BufferedImage formulaImage;
        try {
            formulaImage = captureFormula();
        } catch (IllegalStateException e) {
            showMessage(e.getMessage(), true, ERROR_MESSAGE_MILLIS);  // the canvas is empty
            return;
        }
        graphedFormula = getSharedCanvas().getFormulaBounds();
        graphButton.setEnabled(false);
        showMessage("Reading the formula…", false, 0);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String latex = recognizeFormula(formulaImage);  // fails if recognition is not connected
                SwingUtilities.invokeLater(() -> showMessage("Drawing the graph of " + latex + "…", false, 0));
                controller.execute(latex, xRange, yRange);  // calls QuickChart; the presenter updates the view model
                return null;
            }

            @Override
            protected void done() {
                graphButton.setEnabled(true);
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    showMessage(reason, true, ERROR_MESSAGE_MILLIS);
                }
            }
        }.execute();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // The presenter may fire from the worker thread; Swing must be updated on the event thread.
        SwingUtilities.invokeLater(() -> show(viewModel.getState()));
    }

    private void show(GraphState state) {
        if (state.getError() != null) {
            showMessage(state.getError(), true, ERROR_MESSAGE_MILLIS);
        } else if (state.getGraphImage() != null) {
            getSharedCanvas().showGraph(state.getGraphImage(), graphedFormula);
            showMessage("Graph of " + state.getLatex(), false, SUCCESS_MESSAGE_MILLIS);
        }
    }
}
