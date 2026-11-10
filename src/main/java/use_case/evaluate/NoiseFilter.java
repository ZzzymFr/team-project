package use_case.evaluate;

import entity.Context;
import entity.node.Node;
import entity.node.NoiseScale;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.List;

// float noise -> 0, real tiny values stay
final class NoiseFilter implements ValueVisitor<Value> {
    // noise unknown: anything under this counts as 0
    private static final double UNKNOWN_NOISE_CUTOFF = 1e-12;
    // same band = uses: 64x the noise, never past 1e-9
    private static final double NOISE_MARGIN = 64;
    private static final double MAX_NOISE = 1e-9;

    private final Node node;
    private final Context context;

    NoiseFilter(Node node, Context context) {
        this.node = node;
        this.context = context;
    }

    static boolean isTiny(double number) {
        return Math.abs(number) < UNKNOWN_NOISE_CUTOFF;
    }

    static double zeroIfTiny(double number) {
        return isTiny(number) ? 0 : number;
    }

    @Override
    public Value visitNumber(NumberValue value) {
        double number = value.getValue();
        if (number == 0 || !(Math.abs(number) <= MAX_NOISE)) {
            return value;
        }
        double noise = NoiseScale.noiseOf(node, context);
        if (!Double.isFinite(noise)) {
            return new NumberValue(zeroIfTiny(number));
        }
        return Math.abs(number) <= NOISE_MARGIN * noise ? new NumberValue(0) : value;
    }

    @Override
    public Value visitBoolean(BooleanValue value) {
        return value;
    }

    @Override
    public Value visitFiniteSet(FiniteSetValue value) {
        return new FiniteSetValue(zeroAll(value.getElements()));
    }

    @Override
    public Value visitVector(VectorValue value) {
        return new VectorValue(zeroAll(value.getComponents()));
    }

    @Override
    public Value visitNumberSet(NumberSetValue value) {
        return value;
    }

    private static List<Double> zeroAll(List<Double> numbers) {
        List<Double> out = new ArrayList<>();
        for (double number : numbers) {
            out.add(zeroIfTiny(number));
        }
        return out;
    }
}
