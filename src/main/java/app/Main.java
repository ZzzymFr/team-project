package app;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new AppBuilder()
                    .addDrawingView()
                    .addExpressionView()
                    .addEvaluateUseCase()
                    .build();
            frame.setVisible(true);
        });
    }
}
