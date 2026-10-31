package entity;

// cant decide, dont treat as false
public class UndecidableException extends UncalculableException {
    public UndecidableException() {
        super(UNDECIDED);
    }

    public UndecidableException(String reason) {
        super(reason);
    }
}
