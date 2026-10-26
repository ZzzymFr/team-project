package entity.value;

public final class BooleanValue extends Value {
    private static final BooleanValue TRUE = new BooleanValue(true);
    private static final BooleanValue FALSE = new BooleanValue(false);

    private final boolean value;

    private BooleanValue(boolean value) {
        this.value = value;
    }

    public static BooleanValue of(boolean value) {
        if (value) {
            return TRUE;
        }
        return FALSE;
    }

    public boolean getValue() {
        return value;
    }
}
