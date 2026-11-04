package view.canvas;

import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.*;
import java.awt.*;

public class PdfCanvasPage extends CanvasPage {
    public PdfCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("PDF Studio is not connected."));
    }

    public PdfCanvasPage(SharedCanvas sharedCanvas, Runnable openStudio) {
        super(sharedCanvas);
        GlassButton open = new GlassButton("Open PDF Studio", 40);
        open.addActionListener(e -> openStudio.run());
        JLabel note = new JLabel("Your calculated formulas and graphs are collected automatically.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(open);
        tools.add(note);
        setFunctionArea(tools);
    }
}
