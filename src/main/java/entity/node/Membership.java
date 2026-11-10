package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UndefinedException;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;

// \in and \subset
final class Membership {
    private Membership() {
    }

    static boolean isMember(Value element, Value set, Node elementNode, Context context) {
        if (element instanceof NumberValue && Double.isNaN(((NumberValue) element).getValue())) {
            throw new UndefinedException();
        }
        if (element instanceof NumberValue && set instanceof FiniteSetValue) {
            return ((FiniteSetValue) set).contains(((NumberValue) element).getValue());
        }
        if (set instanceof NumberSetValue) {
            NumberSetValue numbers = (NumberSetValue) set;
            if (element instanceof VectorValue) {
                return numbers.containsVector((VectorValue) element);
            }
            return containsNumber(numbers, Values.toNumber(element), elementNode, context);
        }
        throw new UncalculableException(UncalculableReason.WRONG_KIND);
    }

    private static boolean containsNumber(NumberSetValue numbers, double number, Node node, Context context) {
        if (numbers.getDimension() != 1 || !Double.isFinite(number)) {
            return numbers.containsNumber(number);
        }
        double nearest = Math.rint(number);
        if (isWhole(number, nearest, node, context)) {
            return numbers.containsNumber(nearest, true, true);
        }
        if (SignedNumber.is(node)) {
            return numbers.containsNumber(number, false, true);
        }
        return numbers.containsNumber(number);
    }

    // whole if = would call it equal to the nearest int
    private static boolean isWhole(double number, double nearest, Node node, Context context) {
        if (number == nearest) {
            return true;
        }
        double scale = NoiseScale.isSupported(node) ? NoiseScale.of(node, context) : Double.NaN;
        return NoiseTolerance.within(number, nearest, NoiseTolerance.of(scale, number, nearest));
    }

    static boolean isSubset(Value left, Value right) {
        if (left instanceof FiniteSetValue && right instanceof FiniteSetValue) {
            return ((FiniteSetValue) left).isSubsetOf((FiniteSetValue) right);
        }
        if (left instanceof FiniteSetValue && right instanceof NumberSetValue) {
            for (double element : ((FiniteSetValue) left).getElements()) {
                if (!((NumberSetValue) right).containsNumber(element)) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof NumberSetValue && right instanceof NumberSetValue) {
            return ((NumberSetValue) left).isSubsetOf((NumberSetValue) right);
        }
        if (left instanceof NumberSetValue && right instanceof FiniteSetValue) {
            return false;
        }
        throw new UncalculableException(UncalculableReason.WRONG_KIND);
    }
}
