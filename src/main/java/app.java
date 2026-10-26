import view.DrawingCanvas;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class app {
    vital_mini model;
    app(){
        model = new vital_mini();
    }
    void init(){
        SwingUtilities.invokeLater(app::showWindow);
    }
    void run(){
        boolean should_run = true;
        while(should_run){
           if(should_run){
               break;
           }
        }
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
