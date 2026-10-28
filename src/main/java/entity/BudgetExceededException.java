package entity;

public class BudgetExceededException extends UncalculableException {
    public BudgetExceededException() {
        super(TOO_MUCH_WORK);
    }
}
