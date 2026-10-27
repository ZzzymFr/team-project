package pdf_editor;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class PdfEditorDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MathLibrary library = new MathLibrary();
            library.addCalculation("x^2+2x+1=0", "x=-1");
            library.addCalculation("\\frac{1}{2}+\\frac{1}{3}", "\\frac{5}{6}");
            library.addFormula("\\int_0^1 x^2\\,dx");
            BufferedImage graph = new BufferedImage(600, 360, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = graph.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, 600, 360);
            g.setColor(Color.GRAY); g.drawLine(0, 280, 600, 280); g.drawLine(300, 0, 300, 360);
            g.setColor(new Color(30, 110, 215));
            for (int x = 1; x < 599; x++) {
                int y1 = (int) (280 - .003 * (x - 300) * (x - 300));
                int y2 = (int) (280 - .003 * (x + 1 - 300) * (x + 1 - 300));
                g.drawLine(x, y1, x + 1, y2);
            }
            g.dispose();
            library.addGraph("y=x^2", graph);
            new PdfEditorFrame(library).setVisible(true);
        });
    }
}
