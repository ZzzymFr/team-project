package pdf_editor;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class NoteRenderer {
    private NoteRenderer() { }
    public static BufferedImage render(PdfDocument.Placement note, int width, int height) {
        width = Math.max(8, width); height = Math.max(8, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(31, 48, 72));
            float scale = width / Math.max(1f, note.width);
            int size = Math.max(7, Math.round(note.noteSize * scale));
            g.setFont(new Font("SansSerif", note.noteStyle, size));
            FontMetrics fm = g.getFontMetrics();
            int y = fm.getAscent() + 5;
            String content = note.noteText == null ? "" : note.noteText;
            for (String line : content.split("\\n", -1)) {
                StringBuilder current = new StringBuilder();
                for (String word : line.split(" ", -1)) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (!current.isEmpty() && fm.stringWidth(candidate) > width - 12) {
                        if (y <= height) g.drawString(current.toString(), 6, y);
                        y += fm.getHeight(); current = new StringBuilder(word);
                    } else current = new StringBuilder(candidate);
                }
                if (y <= height) g.drawString(current.toString(), 6, y);
                y += fm.getHeight();
            }
        } finally { g.dispose(); }
        return image;
    }
}
