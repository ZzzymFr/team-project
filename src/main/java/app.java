import app.AppBuilder;
import app.StackedAppBuilder;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class app {
    vital_mini model;
    JFrame frame;

    app() {
        model = new vital_mini();
    }

    void init() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());  // native look on Windows
            } catch (Exception ignored) {
                // fall back to the default look
            }
            // US2: the expression evaluator, shown on the Answer page
            AppBuilder evaluator = new AppBuilder()
                    .addExpressionView()
                    .addEvaluateUseCase();
            // Graph, PDF and Answer pages stacked in one window, sharing one handwriting canvas
            frame = new StackedAppBuilder()
                    .addGraphPage()
                    .addPdfPage()
                    .addAnswerPage(evaluator.getExpressionView())
                    .build();
        });
    }

    void run() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public static void main(String[] args) {
        try {
            app application = new app();
            application.init();
            application.run();
        } catch (Exception e) {
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
