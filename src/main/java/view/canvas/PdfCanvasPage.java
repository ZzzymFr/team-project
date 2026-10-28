package view.canvas;

import pdf_editor.PdfStudioLauncher;
import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.*;
import java.awt.*;

/** US4 entry point in the existing stacked-page Swing application. */
public class PdfCanvasPage extends CanvasPage {
    public PdfCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("PDF Studio is not connected."));
    }

    public PdfCanvasPage(SharedCanvas sharedCanvas, PdfStudioLauncher studio) {
        super(sharedCanvas);
        GlassButton open = new GlassButton("Open PDF Studio", 40);
        open.addActionListener(e -> studio.openEditor());
        JLabel note = new JLabel("Your calculated formulas and graphs are collected automatically.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(open);
        tools.add(note);
        setFunctionArea(tools);
    }
}
