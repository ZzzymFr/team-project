import app.StackedAppBuilder;
import model.vital_mini;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class app {
    public final vital_mini model = new vital_mini();
    JFrame frame;

    void init() throws Exception {
        model.init();
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default look.
            }
            AppBuilder evaluator = new AppBuilder()
                    .addExpressionView()
                    .addEvaluateUseCase();
            frame = new StackedAppBuilder()
                    .addGraphPage()
                    .addPdfPage()
                    .addAnswerPage()
                    .build();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    model.close();
                }

                @Override
                public void windowClosed(WindowEvent event) {
                    model.close();
                }
            });
        });
    }

    void run() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public static void main(String[] args) {
        app application = new app();
        try {
            application.init();
            application.run();
        } catch (Exception e) {
            application.model.close();
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
