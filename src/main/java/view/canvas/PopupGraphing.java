package view.canvas;

import interface_adapter.graph.AxisRangeParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import view.FormulaCapture;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Graph from the popup: uses the latex already read, card goes beside that formula
final class PopupGraphing {
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final int SUCCESS_MESSAGE_MILLIS = 3000;
    private static final int RANGE_GAP = 6;

    // the page's message strip
    interface Messages {
        void show(String text, boolean error, int hideAfterMillis);
    }

    private final SharedCanvas canvas;
    private final GraphController controller;
    private final GraphViewModel viewModel;
    private final Messages messages;
    private final JComponent area;
    private final java.util.function.BiConsumer<FormulaCapture, String> graphSaved;
    private FormulaCapture pendingCapture;
    private final GraphRangeBar ranges;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "popup-graph");
        t.setDaemon(true);
        return t;
    });
    private String lastLatex;
    private Rectangle lastFormula;   // world coords
    private Rectangle pending;       // where the card goes when the graph comes back

    PopupGraphing(SharedCanvas canvas, GraphController controller, GraphViewModel viewModel,
                  Messages messages, JComponent area,
                  java.util.function.BiConsumer<FormulaCapture, String> graphSaved) {
        this.canvas = canvas;
        this.controller = controller;
        this.viewModel = viewModel;
        this.messages = messages;
        this.area = area;
        this.graphSaved = java.util.Objects.requireNonNull(graphSaved);
        this.ranges = new GraphRangeBar(this::regraph);
        viewModel.addPropertyChangeListener(this::changed);
        canvas.addViewListener(this::placeRanges);
        canvas.addUserActionListener(() -> SwingUtilities.invokeLater(this::placeRanges));
        canvas.getDrawingCanvas().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                placeRanges();  // the card's x may have closed it
            }
        });
    }

    JComponent rangeBar() {
        return ranges;
    }

    void setRangeText(String x, String y) {
        ranges.setRangeText(x, y);
    }

    void graph(String latex, Rectangle formulaWorld, FormulaCapture capture) {
        pendingCapture = capture;
        lastLatex = latex;
        lastFormula = formulaWorld;
        run(latex, formulaWorld);
    }

    // Enter in a range box: same formula, new range
    private void regraph() {
        if (lastLatex != null) {
            run(lastLatex, lastFormula);
        }
    }

    private void run(String latex, Rectangle formulaWorld) {
        double[] x;
        double[] y;
        try {
            x = AxisRangeParser.parse(ranges.xText(), "x");
            y = AxisRangeParser.parse(ranges.yText(), "y");
        } catch (IllegalArgumentException e) {
            messages.show(e.getMessage(), true, ERROR_MESSAGE_MILLIS);
            return;
        }
        pending = formulaWorld;
        messages.show("Drawing the graph of " + latex + "…", false, 0);
        executor.execute(() -> {
            try {
                controller.execute(latex, x, y);  // answer comes through the view model
            } catch (RuntimeException e) {
                String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                SwingUtilities.invokeLater(() -> messages.show(reason, true, ERROR_MESSAGE_MILLIS));
            }
        });
    }

    private void changed(PropertyChangeEvent event) {
        if (GraphViewModel.STATE_PROPERTY.equals(event.getPropertyName())) {
            SwingUtilities.invokeLater(() -> show(viewModel.getState()));
        }
    }

    private void show(GraphState state) {
        if (state.getError() != null) {
            messages.show(state.getError(), true, ERROR_MESSAGE_MILLIS);
        } else if (state.getGraphImage() != null) {
            canvas.showGraph(state.getGraphImage(), pending);
            // Explicit, successful Graph is the publication boundary for formula-only material.
            // Regraphing a range does not create duplicates: the source revision is tracked by the page.
            if (pendingCapture != null && state.getLatex() != null
                    && state.getLatex().equals(lastLatex)) {
                graphSaved.accept(pendingCapture, state.getLatex());
            }
            messages.show("Graph of " + state.getLatex(), false, SUCCESS_MESSAGE_MILLIS);
            placeRanges();
        }
    }

    // under the card, moves with pan and zoom
    void placeRanges() {
        Rectangle2D card = canvas.getDrawingCanvas().getGraphCardOnScreen();
        if (card == null) {
            if (ranges.isVisible()) {
                ranges.setVisible(false);
                area.repaint();
            }
            return;
        }
        Dimension d = ranges.getPreferredSize();
        Rectangle at = SwingUtilities.convertRectangle(canvas.getDrawingCanvas(), new Rectangle(
                (int) Math.round(card.getX()), (int) Math.round(card.getMaxY()) + RANGE_GAP, d.width, d.height), area);
        ranges.setBounds(at);
        ranges.setVisible(true);
        ranges.revalidate();
        area.repaint();
    }
}
