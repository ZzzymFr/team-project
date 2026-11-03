package use_case.evaluate;

// one solution interval, plain numbers for the presenter
public final class IntervalOutputData {
    private final double low;
    private final boolean lowIncluded;
    private final double high;
    private final boolean highIncluded;
    private final boolean point;

    public IntervalOutputData(double low, boolean lowIncluded, double high, boolean highIncluded, boolean point) {
        this.low = low;
        this.lowIncluded = lowIncluded;
        this.high = high;
        this.highIncluded = highIncluded;
        this.point = point;
    }

    public double getLow() {
        return low;
    }

    public boolean isLowIncluded() {
        return lowIncluded;
    }

    public double getHigh() {
        return high;
    }

    public boolean isHighIncluded() {
        return highIncluded;
    }

    public boolean isPoint() {
        return point;
    }
}
