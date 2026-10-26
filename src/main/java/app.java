

public class app {
    vital_mini model;
    app(){
        model = new vital_mini();
    }
    void init(){

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
    
}
