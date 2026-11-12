package view.board;

// under-damped spring, overshoots a bit
public final class Spring {
    public float x;
    public float v;

    // f = elapsed time in 16 ms frames
    public boolean step(float target, float k, float damping, float f) {
        v = (v + (target - x) * k * f) * (float) Math.pow(damping, f);
        x += v * f;
        if (Math.abs(target - x) < 0.15f && Math.abs(v) < 0.15f) {
            x = target;
            v = 0;
            return true;
        }
        return false;
    }
}
