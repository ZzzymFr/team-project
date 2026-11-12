package entity.node;

import java.util.List;

// the element formulas of a typed {..} set, null for anything else
final class WrittenElements implements NodeVisitor<List<Node>> {
    static final WrittenElements INSTANCE = new WrittenElements();

    private WrittenElements() {
    }

    @Override
    public List<Node> visitSetLiteral(SetLiteralNode node) {
        return node.children();
    }

    @Override
    public List<Node> visitBinary(BinaryNode node) {
        return null;
    }

    @Override
    public List<Node> visitCall(CallNode node) {
        return null;
    }

    @Override
    public List<Node> visitDerivative(DerivativeNode node) {
        return null;
    }

    @Override
    public List<Node> visitFunction(FunctionNode node) {
        return null;
    }

    @Override
    public List<Node> visitLimit(LimitNode node) {
        return null;
    }

    @Override
    public List<Node> visitLiteral(LiteralNode node) {
        return null;
    }

    @Override
    public List<Node> visitNumber(NumberNode node) {
        return null;
    }

    @Override
    public List<Node> visitPlusMinus(PlusMinusNode node) {
        return null;
    }

    @Override
    public List<Node> visitQuantifier(QuantifierNode node) {
        return null;
    }

    @Override
    public List<Node> visitRelation(RelationNode node) {
        return null;
    }

    @Override
    public List<Node> visitSeries(SeriesNode node) {
        return null;
    }

    @Override
    public List<Node> visitUnary(UnaryNode node) {
        return null;
    }

    @Override
    public List<Node> visitVariable(VariableNode node) {
        return null;
    }

    @Override
    public List<Node> visitVector(VectorNode node) {
        return null;
    }
}
