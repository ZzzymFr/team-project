package entity.value;

import entity.UncalculableException;

import java.util.List;

public final class Values {
    private Values() {
    }

    public static double toNumber(Value value) {
        if (!(value instanceof NumberValue)) {
            throw new UncalculableException();
        }
        return ((NumberValue) value).getValue();
    }

    public static boolean toBoolean(Value value) {
        if (!(value instanceof BooleanValue)) {
            throw new UncalculableException();
        }
        return ((BooleanValue) value).getValue();
    }

    public static boolean areAlmostEqual(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return a == b;
        }
        double size = Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= 1e-9 * size;
    }

    public static boolean areEqual(Value a, Value b) {
        if (a instanceof NumberValue && b instanceof NumberValue) {
            return areAlmostEqual(((NumberValue) a).getValue(), ((NumberValue) b).getValue());
        }
        if (a instanceof BooleanValue && b instanceof BooleanValue) {
            return ((BooleanValue) a).getValue() == ((BooleanValue) b).getValue();
        }
        if (a instanceof FiniteSetValue && b instanceof FiniteSetValue) {
            return areAllAlmostEqual(((FiniteSetValue) a).getElements(), ((FiniteSetValue) b).getElements());
        }
        if (a instanceof VectorValue && b instanceof VectorValue) {
            return areAllAlmostEqual(((VectorValue) a).getComponents(), ((VectorValue) b).getComponents());
        }
        if (a instanceof NumberSetValue && b instanceof NumberSetValue) {
            NumberSetValue first = (NumberSetValue) a;
            NumberSetValue second = (NumberSetValue) b;
            return first.getKind() == second.getKind() && first.getDimension() == second.getDimension();
        }
        return false;
    }

    private static boolean areAllAlmostEqual(List<Double> first, List<Double> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!areAlmostEqual(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }
}
