import view.DrawingCanvas;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class app {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(app::showWindow);
    }

    private static void showWindow() {
        DrawingCanvas canvas = new DrawingCanvas();

        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> canvas.undo());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> canvas.clear());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(undo);
        toolbar.add(clear);

        JFrame frame = new JFrame("Math Lab");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(canvas, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
