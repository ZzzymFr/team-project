package entity;

// value too big for double, cant compare
public class OverflowException extends UncalculableException {
    public OverflowException() {
        super(TOO_BIG);
    }
}
