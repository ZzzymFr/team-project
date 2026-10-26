import app.AppBuilder;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class app {
    vital_mini model;
    JFrame frame;

    app() {
        model = new vital_mini();
    }

    void init() throws Exception {
        SwingUtilities.invokeAndWait(() -> frame = new AppBuilder()
                .addDrawingView()
                .addExpressionView()
                .addEvaluateUseCase()
                .build());
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
