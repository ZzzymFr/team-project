package entity;

public class Interval {
    private final double low;
    private final boolean lowIncluded;
    private final double high;
    private final boolean highIncluded;

    public Interval(double low, boolean lowIncluded, double high, boolean highIncluded) {
        this.low = low;
        this.lowIncluded = lowIncluded;
        this.high = high;
        this.highIncluded = highIncluded;
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
        return high - low < 1e-6;
    }
}
