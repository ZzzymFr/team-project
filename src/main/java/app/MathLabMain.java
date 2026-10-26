package app;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Starts Math Lab with the stacked-pages window.
 */
public class MathLabMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());  // native buttons on Windows
            } catch (Exception ignored) {
                // fall back to the default look
            }
            JFrame frame = new StackedAppBuilder()
                    .addGraphPage()
                    .addPdfPage()
                    .addAnswerPage()
                    .build();
            frame.setVisible(true);
        });
    }
}
