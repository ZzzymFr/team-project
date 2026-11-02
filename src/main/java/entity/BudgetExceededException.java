package entity;

public class BudgetExceededException extends UncalculableException {
    public BudgetExceededException() {
        super(UncalculableReason.TOO_MUCH_WORK);
    }
}
