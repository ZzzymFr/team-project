package pdf_editor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class NoteRenderer {
    private NoteRenderer() { }
    public static BufferedImage render(PdfDocument.Placement note, int width, int height) {
        width = Math.max(8, width); height = Math.max(8, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            JTextPane pane = RichNoteEditor.createPane(note);
            pane.setOpaque(false);
            int logicalWidth = Math.max(8, Math.round(note.width));
            int logicalHeight = Math.max(8, Math.round(note.height));
            pane.setSize(logicalWidth, logicalHeight);
            pane.validate();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(width / (double) logicalWidth, height / (double) logicalHeight);
            pane.printAll(g);
        } finally { g.dispose(); }
        return image;
    }
}
