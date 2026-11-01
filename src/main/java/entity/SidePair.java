package entity;

import entity.node.Node;
import entity.value.Values;

// compare two sides or one part each
final class SidePair {
    private final Node left;
    private final Node right;
    final int leftPart;
    final int rightPart;

    // comparison for a factor of a product
    final SidePair whole;

    double lastLeft = Double.NaN;
    double lastRight = Double.NaN;
    private double lastError;

    SidePair(Node left, Node right, int leftPart, int rightPart) {
        this(left, right, leftPart, rightPart, null);
    }

    SidePair(Node left, Node right, int leftPart, int rightPart, SidePair whole) {
        this.left = left;
        this.right = right;
        this.leftPart = leftPart;
        this.rightPart = rightPart;
        this.whole = whole;
    }

    double leftAt(Context at) {
        return EquationSolver.partOf(left.evaluate(at), leftPart);
    }

    double rightAt(Context at) {
        return EquationSolver.partOf(right.evaluate(at), rightPart);
    }

    double differenceAt(Context at) {
        lastLeft = Double.NaN;
        lastRight = Double.NaN;
        at.setApproximation(0);
        lastLeft = leftAt(at);
        lastRight = rightAt(at);
        lastError = at.getApproximation();
        return lastLeft - lastRight;
    }

    // approx sides equal within error
    boolean isNoise() {
        return (left.isApproximate() || right.isApproximate()) && lastLeft != lastRight
                && Values.areApproximatelyEqual(lastLeft, lastRight, lastError);
    }
}
