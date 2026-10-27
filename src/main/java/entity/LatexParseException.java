package entity;

/**
 * Thrown when a LaTeX string cannot be understood as a function of x.
 */
public class LatexParseException extends Exception {
    public LatexParseException(String message) {
        super(message);
    }
}
