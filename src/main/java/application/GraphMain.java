package application;

import view.GraphView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Runs the graph feature on its own until it is added to the main app window.
 */
public class GraphMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GraphView graphView = GraphFeatureFactory.createGraphView();
            // Once handwriting recognition exists: graphView.setLatexProvider(recogniser::recognise);
            JFrame frame = new JFrame("Math Lab - Graph");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(graphView);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
