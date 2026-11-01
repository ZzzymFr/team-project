package use_case.evaluate;

import entity.Interval;

// one solution interval, plain numbers for the presenter
public final class IntervalOutputData {
    private final double low;
    private final boolean lowIncluded;
    private final double high;
    private final boolean highIncluded;
    private final boolean point;

    private IntervalOutputData(Interval interval) {
        this.low = interval.getLow();
        this.lowIncluded = interval.isLowIncluded();
        this.high = interval.getHigh();
        this.highIncluded = interval.isHighIncluded();
        this.point = interval.isPoint();
    }

    public static IntervalOutputData of(Interval interval) {
        return new IntervalOutputData(interval);
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
