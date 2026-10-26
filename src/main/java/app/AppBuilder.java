package app;

import data_access.InMemoryFunctionDataAccessObject;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import use_case.evaluate.EvaluateInputBoundary;
import use_case.evaluate.EvaluateInteractor;
import use_case.evaluate.EvaluateOutputBoundary;
import use_case.evaluate.FunctionDataAccessInterface;
import view.DrawingCanvas;
import view.ExpressionView;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class AppBuilder {
    private final MathLabViewModel mathLabViewModel = new MathLabViewModel();
    private final FunctionDataAccessInterface functionDataAccess = new InMemoryFunctionDataAccessObject();

    private JPanel drawingPanel;
    private DrawingCanvas drawingCanvas;
    private ExpressionView expressionView;

    public AppBuilder addDrawingView() {
        drawingCanvas = new DrawingCanvas();

        JButton undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> drawingCanvas.undo());
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> drawingCanvas.clear());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(undoButton);
        toolbar.add(clearButton);

        drawingPanel = new JPanel(new BorderLayout());
        drawingPanel.add(toolbar, BorderLayout.NORTH);
        drawingPanel.add(drawingCanvas, BorderLayout.CENTER);
        return this;
    }

    public AppBuilder addExpressionView() {
        expressionView = new ExpressionView(mathLabViewModel);
        return this;
    }

    public AppBuilder addEvaluateUseCase() {
        EvaluateOutputBoundary presenter = new EvaluatePresenter(mathLabViewModel);
        EvaluateInputBoundary interactor = new EvaluateInteractor(functionDataAccess, presenter);
        EvaluateController controller = new EvaluateController(interactor);
        expressionView.setEvaluateController(controller);
        return this;
    }

    /** The expression panel built by addExpressionView, e.g. to place it in another window. */
    public ExpressionView getExpressionView() {
        return expressionView;
    }

    public JFrame build() {
        JFrame frame = new JFrame("Math Lab");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (drawingPanel != null) {
            frame.add(drawingPanel, BorderLayout.CENTER);
        }
        if (expressionView != null) {
            frame.add(expressionView, BorderLayout.SOUTH);
        }
        frame.pack();
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
