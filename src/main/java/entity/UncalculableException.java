package entity;

public class UncalculableException extends MathSyntaxException {
    private final UncalculableReason reason;  // null = no reason given
    private final String detail;              // the function, for INVERSE_AMBIGUOUS

    public UncalculableException() {
        this(null, null);
    }

    public UncalculableException(UncalculableReason reason) {
        this(reason, null);
    }

    public UncalculableException(UncalculableReason reason, String detail) {
        super(reason == null ? "Uncalculable" : "Uncalculable: " + reason);
        this.reason = reason;
        this.detail = detail;
    }

    public UncalculableReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
