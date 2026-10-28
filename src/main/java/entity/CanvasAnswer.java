package entity;

import java.awt.geom.Rectangle2D;

// an answer written on the canvas, world coords; box = ink box of the text
public final class CanvasAnswer {
    private final String text;
    private final boolean error;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final float fontSize;

    public CanvasAnswer(String text, boolean error, double x, double y, double width, double height, float fontSize) {
        this.text = text;
        this.error = error;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
    }

    public String getText() {
        return text;
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

    public Rectangle2D.Double getBox() {
        return new Rectangle2D.Double(x, y, width, height);
    }

    public CanvasAnswer translated(double dx, double dy) {
        return new CanvasAnswer(text, error, x + dx, y + dy, width, height, fontSize);
    }

    // box and font scale together
    public CanvasAnswer scaled(double anchorX, double anchorY, double factor) {
        return new CanvasAnswer(text, error, anchorX + (x - anchorX) * factor, anchorY + (y - anchorY) * factor,
                width * factor, height * factor, (float) (fontSize * factor));
    }
}
