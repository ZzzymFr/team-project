package view.canvas;

import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;

// answer page: recognize ink -> evaluate -> show answer
public class AnswerCanvasPage extends CanvasPage implements PropertyChangeListener {
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final Color ANSWER_TEXT = new Color(15, 23, 42);
    private static final Color ERROR_TEXT = new Color(185, 28, 28);

    private final EvaluateController controller;
    private final MathLabViewModel viewModel;
    private final GlassButton calculateButton = new GlassButton("Calculate", 40);
    private final JLabel answerLabel = new JLabel(" ");

    // no evaluate feature, just a placeholder
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.controller = null;
        this.viewModel = null;
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel) {
        super(sharedCanvas);
        this.controller = controller;
        this.viewModel = viewModel;
        viewModel.addPropertyChangeListener(this);

        calculateButton.setFont(Fonts.ui(Font.PLAIN, 14f));
        calculateButton.addActionListener(e -> calculate());
        answerLabel.setFont(Fonts.ui(Font.PLAIN, 18f));
        answerLabel.setForeground(ANSWER_TEXT);
        JLabel note = new JLabel("Several formulas? Circle one with the Lasso first.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(calculateButton);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(answerLabel);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(note);
        setFunctionArea(tools);
    }

    // current answer, blank if none
    String currentAnswer() {
        return answerLabel.getText().trim();
    }

    boolean isAnswerError() {
        return ERROR_TEXT.equals(answerLabel.getForeground());
    }

    // calc the lassoed formula or all ink
    void calculate() {
        BufferedImage formulaImage;
        try {
            formulaImage = captureFormula();
        } catch (IllegalStateException e) {
            showMessage(e.getMessage(), true, ERROR_MESSAGE_MILLIS);  // the canvas is empty
            return;
        }
        calculateButton.setEnabled(false);
        showMessage("Reading the formula…", false, 0);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                String latex = recognizeFormula(formulaImage);  // fails if recognition is not connected
                SwingUtilities.invokeLater(() -> showMessage("Calculating " + latex + "…", false, 0));
                controller.execute(latex);  // presenter updates vm
                return null;
            }

            @Override
            protected void done() {
                calculateButton.setEnabled(true);
                try {
                    get();
                    hideMessage();
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
        if (!MathLabViewModel.RESULT.equals(evt.getPropertyName())) {
            return;
        }
        // presenter is on worker thread, hop to EDT
        MathLabState state = viewModel.getState();
        String result = state.getResultText();
        String error = state.getErrorText();
        SwingUtilities.invokeLater(() -> show(result, error));
    }

    private void show(String result, String error) {
        if (error.isEmpty()) {
            answerLabel.setForeground(ANSWER_TEXT);
            answerLabel.setText(result);
        } else {
            answerLabel.setForeground(ERROR_TEXT);
            answerLabel.setText(error);
        }
    }
}
