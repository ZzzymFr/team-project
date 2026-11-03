package entity.value;

public abstract class Value {
    public abstract <R> R accept(ValueVisitor<R> visitor);
}
