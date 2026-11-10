package entity.node;

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

    static boolean isMember(Value element, Value set, Node elementNode) {
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
            double number = Values.toNumber(element);
            if (numbers.getKind() == NumberSetValue.Kind.RATIONAL && numbers.getDimension() == 1
                    && !Double.isNaN(number) && !Double.isInfinite(number)
                    && number != Math.floor(number)) {
                if (SignedNumber.is(elementNode)) {
                    return true;
                }
                throw new UncalculableException(UncalculableReason.RATIONAL);
            }
            return numbers.containsNumber(number);
        }
        throw new UncalculableException(UncalculableReason.WRONG_KIND);
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
