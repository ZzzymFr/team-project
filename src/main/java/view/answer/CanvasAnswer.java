package view.answer;

import interface_adapter.TextRun;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.List;

// an answer written on the canvas, world coords; box = ink box of the text
public final class CanvasAnswer {
    private final String text;
    private final boolean error;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final float fontSize;
    private final List<TextRun> runs;  // null = plain text
    private final Color color;

    public CanvasAnswer(String text, boolean error, double x, double y, double width, double height, float fontSize) {
        this(text, null, error, x, y, width, height, fontSize, Color.BLACK);
    }

    // text w/ raised exponent runs, e.g. 3x²
    public CanvasAnswer(List<TextRun> runs, boolean error, double x, double y, double width, double height,
                        float fontSize) {
        this(TextRun.plain(runs), List.copyOf(runs), error, x, y, width, height, fontSize, Color.BLACK);
    }

    private CanvasAnswer(String text, List<TextRun> runs, boolean error, double x, double y, double width,
                         double height, float fontSize, Color color) {
        this.text = text;
        this.runs = runs;
        this.error = error;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public List<TextRun> getRuns() {
        return runs;
    }

    public boolean isError() {
        return error;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public float getFontSize() {
        return fontSize;
    }

    public Color getColor() {
        return color;
    }

    public Rectangle2D.Double getBox() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public CanvasAnswer withColor(Color newColor) {
        return new CanvasAnswer(text, runs, error, x, y, width, height, fontSize, newColor);
    }

    // same spot, new box size
    public CanvasAnswer sized(double newWidth, double newHeight) {
        return new CanvasAnswer(text, runs, error, x, y, newWidth, newHeight, fontSize, color);
    }

    public CanvasAnswer translated(double dx, double dy) {
        return new CanvasAnswer(text, runs, error, x + dx, y + dy, width, height, fontSize, color);
    }

    // box and font scale together
    public CanvasAnswer scaled(double anchorX, double anchorY, double factor) {
        return new CanvasAnswer(text, runs, error, anchorX + (x - anchorX) * factor, anchorY + (y - anchorY) * factor,
                width * factor, height * factor, (float) (fontSize * factor), color);
    }
}
