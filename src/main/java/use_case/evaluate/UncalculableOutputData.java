package use_case.evaluate;

// reason = enum name, null if none. presenter picks the words
public final class UncalculableOutputData {
    private final String reason;
    private final String detail;

    public UncalculableOutputData(String reason, String detail) {
        this.reason = reason;
        this.detail = detail;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
