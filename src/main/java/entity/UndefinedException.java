package entity;

// no value here so condition is false
public class UndefinedException extends UncalculableException {
    public UndefinedException() {
        super(UNDEFINED_PART);
    }

    public UndefinedException(String reason) {
        super(reason);
    }
}
