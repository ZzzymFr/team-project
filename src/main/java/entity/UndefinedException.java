package entity;

// no value here so condition is false
public class UndefinedException extends UncalculableException {
    public UndefinedException() {
        super(UncalculableReason.UNDEFINED_PART);
    }

    public UndefinedException(UncalculableReason reason) {
        super(reason);
    }
}
