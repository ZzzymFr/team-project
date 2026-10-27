package view;

import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ExpressionView extends JPanel implements PropertyChangeListener {
    private final MathLabViewModel viewModel;

    private final JTextField expressionField = new JTextField(30);
    private final JButton calculateButton = new JButton("Calculate");
    private final JLabel resultLabel = new JLabel(" ");
    private final JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT));

    private EvaluateController evaluateController;

    public ExpressionView(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
        viewModel.addPropertyChangeListener(this);

        inputRow.add(new JLabel("Expression:"));
        inputRow.add(expressionField);
        inputRow.add(calculateButton);

        resultLabel.setFont(resultLabel.getFont().deriveFont(18f));
        JPanel resultRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resultRow.add(resultLabel);

        setLayout(new BorderLayout());
        add(inputRow, BorderLayout.NORTH);
        add(resultRow, BorderLayout.CENTER);

        calculateButton.addActionListener(e -> calculate());
        expressionField.addActionListener(e -> calculate());
    }

    public void setEvaluateController(EvaluateController evaluateController) {
        this.evaluateController = evaluateController;
    }

    public String getExpression() {
        return expressionField.getText();
    }

    public void addButton(JButton button) {
        inputRow.add(button);
    }

    private void calculate() {
        if (evaluateController != null) {
            evaluateController.execute(expressionField.getText());
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        MathLabState state = viewModel.getState();

        if (MathLabViewModel.EXPRESSION.equals(evt.getPropertyName())) {
            expressionField.setText(state.getExpression());
        }

        if (MathLabViewModel.RESULT.equals(evt.getPropertyName())) {
            if (state.getErrorText().isEmpty()) {
                resultLabel.setForeground(Color.BLACK);
                resultLabel.setText(state.getResultText());
            } else {
                resultLabel.setForeground(Color.RED);
                resultLabel.setText(state.getErrorText());
            }
        }
    }
}
