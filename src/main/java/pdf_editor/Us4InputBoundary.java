package pdf_editor;

import java.awt.image.BufferedImage;

public interface Us4InputBoundary {
    void addCalculation(String latex, String result);
    void addGraph(String title, BufferedImage png);
    void openEditor();
}
