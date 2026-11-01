package entity.node;

// one method per node type, new node = compile error till each visitor decides
public interface NodeVisitor<R> {
    R visitBinary(BinaryNode node);

    R visitCall(CallNode node);

    R visitDerivative(DerivativeNode node);

    R visitFunction(FunctionNode node);

    R visitLimit(LimitNode node);

    R visitLiteral(LiteralNode node);

    R visitNumber(NumberNode node);

    R visitPlusMinus(PlusMinusNode node);

    R visitQuantifier(QuantifierNode node);

    R visitRelation(RelationNode node);

    R visitSeries(SeriesNode node);

    R visitSetLiteral(SetLiteralNode node);

    R visitUnary(UnaryNode node);

    R visitVariable(VariableNode node);

    R visitVector(VectorNode node);
}
