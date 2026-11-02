package entity;

// cant decide, dont treat as false
public class UndecidableException extends UncalculableException {
    public UndecidableException() {
        super(UncalculableReason.UNDECIDED);
    }

    public UndecidableException(UncalculableReason reason) {
        super(reason);
    }
}
