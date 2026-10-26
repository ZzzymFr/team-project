package view;

import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import interface_adapter.graph.LatexProvider;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;

/**
 * Graph panel: a LaTeX field, a Graph button, and the canvas the QuickChart image is drawn on.
 *
 * <p>Pressing Graph asks the {@link LatexProvider} for LaTeX (the handwriting recogniser once it is
 * plugged in with {@link #setLatexProvider}; the text field until then) and sends it to the controller.
 */
public class GraphView extends JPanel implements PropertyChangeListener {
    private final GraphViewModel viewModel;
    private final GraphController controller;

    private final JTextField latexField = new JTextField("y=\\sin(x-\\frac{\\pi}{2})+1", 30);
    private final JButton graphButton = new JButton("Graph");
    private final GraphCanvas canvas = new GraphCanvas();
    private final JLabel statusLabel = new JLabel(" ");

    private LatexProvider latexProvider = latexField::getText;

    public GraphView(GraphViewModel viewModel, GraphController controller) {
        super(new BorderLayout(8, 8));
        this.viewModel = viewModel;
        this.controller = controller;
        viewModel.addPropertyChangeListener(this);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.add(new JLabel("LaTeX:"), BorderLayout.WEST);
        inputRow.add(latexField, BorderLayout.CENTER);
        inputRow.add(graphButton, BorderLayout.EAST);

        canvas.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(inputRow, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        graphButton.addActionListener(e -> graph());
        latexField.addActionListener(e -> graph());
    }

    /** Plugs in the handwriting recogniser so the Graph button graphs what was drawn. */
    public void setLatexProvider(LatexProvider latexProvider) {
        this.latexProvider = latexProvider;
    }

    /** Graphs the given LaTeX directly, e.g. when another feature already has the recognised string. */
    public void graph(String latex) {
        latexField.setText(latex);
        runInBackground(() -> latex);
    }

    private void graph() {
        runInBackground(latexProvider);
    }

    private void runInBackground(LatexProvider provider) {
        graphButton.setEnabled(false);
        statusLabel.setText("Drawing graph...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String latex = provider.provideLatex();
                SwingUtilities.invokeLater(() -> latexField.setText(latex));
                controller.execute(latex);  // network call to QuickChart; result arrives via the view model
                return latex;
            }

            @Override
            protected void done() {
                graphButton.setEnabled(true);
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("Could not read the expression: " + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // The presenter may fire from the worker thread; Swing must be updated on the EDT.
        SwingUtilities.invokeLater(() -> render(viewModel.getState()));
    }

    private void render(GraphState state) {
        if (state.getError() != null) {
            showError(state.getError());
        } else if (state.getGraphImage() != null) {
            canvas.showImage(state.getGraphImage());
            statusLabel.setForeground(Color.DARK_GRAY);
            statusLabel.setText("Graph of " + state.getLatex());
        }
    }

    private void showError(String message) {
        canvas.showMessage(message);
        statusLabel.setForeground(new Color(0xB91C1C));
        statusLabel.setText(message);
    }
}
