package entity.value;

// one method per value kind, so callers dont need instanceof chains
public interface ValueVisitor<R> {
    R visitNumber(NumberValue value);

    R visitBoolean(BooleanValue value);

    R visitFiniteSet(FiniteSetValue value);

    R visitVector(VectorValue value);

    R visitNumberSet(NumberSetValue value);
}
