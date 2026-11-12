package entity.node;

// a typed number, maybe w/ a minus in front
final class SignedNumber {
    private SignedNumber() {
    }

    static boolean is(Node node) {
        if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NEGATE) {
            return is(((UnaryNode) node).getOperand());
        }
        return node instanceof NumberNode;
    }

    // decimals in literal, incl -3.14
    static int decimalsOf(Node literal) {
        if (literal instanceof UnaryNode) {
            return decimalsOf(((UnaryNode) literal).getOperand());
        }
        return ((NumberNode) literal).getDecimals();
    }
}
