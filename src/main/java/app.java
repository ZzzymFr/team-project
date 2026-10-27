import model.vital_mini;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;


public class app {
    public vital_mini model;
    app(){
        model = new vital_mini();
    }
    void init() throws java.io.IOException, ai.djl.MalformedModelException {
        model.init();
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Math Lab");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(1000, 700);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
    void run(){
        boolean should_run = true;
        while(should_run){

           if(!should_run){
               break;
           }
        }
    }
    public static void main(String[] args) {
        try {
            app application = new app();
            try {
                application.init();
                application.run();
            } finally {
                application.model.close();
            }
        } catch (Exception e) {
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
}
