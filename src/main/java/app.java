import app.StackedAppBuilder;
import data_access.VitalMiniFormulaRecognizer;
import model.vital_mini;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public class app {
    // relative to the project root, which is where the app runs from (like the tokeniser path in vital_mini)
    public final vital_mini model = new vital_mini(Path.of("src", "main", "java", "model", "checkpoints", "model_ops.pt"));
    JFrame frame;

    void init() throws Exception {
        model.init();
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to the default look.
            }
            frame = new StackedAppBuilder()
                    .addGraphPage()
                    .addPdfPage()
                    .addAnswerPage()
                    .setFormulaRecognizer(new VitalMiniFormulaRecognizer(model))
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
