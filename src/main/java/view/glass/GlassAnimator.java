package view.glass;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs an animation step over and over on the event thread, at up to {@value #MAX_FRAMES_PER_SECOND}
 * frames per second, until the step calls {@link #stop}. All animators tick together on one
 * {@link FrameClock}, which uses no CPU while nothing is animating.
 *
 * <p>Steps must move by {@link #elapsedFrames()}, not by a fixed amount, so the animation takes the
 * same time at any frame rate.
 */
final class GlassAnimator {
    static final int MAX_FRAMES_PER_SECOND = 120;
    private static final float REF_MS = 16f;      // the spring and easing constants were tuned for 16 ms frames
    private static final float MAX_FRAMES = 2f;   // after a long stall, carry on instead of jumping to the end

    private final Runnable step;
    private boolean running;
    private boolean queued;
    private long lastNanos;

    GlassAnimator(Runnable step) {
        this.step = step;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        lastNanos = System.nanoTime();
        queueTick();
    }

    void stop() {
        running = false;
    }

    private void queueTick() {
        if (!queued) {
            queued = true;
            FrameClock.request(this);
        }
    }

    private void tick() {
        queued = false;
        if (running) {
            step.run();
        }
        if (running) {
            queueTick();
        }
    }

    /** The time since the last call, in units of the 16 ms reference frame. */
    float elapsedFrames() {
        long now = System.nanoTime();
        float frames = (now - lastNanos) / (REF_MS * 1e6f);
        lastNanos = now;
        return Math.min(MAX_FRAMES, frames);
    }

    /** Per-frame easing factor k (at the 16 ms reference) converted for a frame of f reference frames. */
    static float ease(float k, float f) {
        return 1f - (float) Math.pow(1f - k, f);
    }

    /**
     * A background thread that wakes the waiting animators once per frame. A javax.swing.Timer can't
     * do this: on Windows its wait is rounded up to about 15.6 ms, which gives some 64 frames per
     * second whatever delay it is set to. Thread.sleep is accurate to about a millisecond.
     */
    private static final class FrameClock {
        private static final long FRAME_NANOS = 1_000_000_000L / MAX_FRAMES_PER_SECOND;
        private static final List<GlassAnimator> WAITING = new ArrayList<>();
        private static boolean started;

        private FrameClock() {
        }

        static void request(GlassAnimator animator) {
            synchronized (WAITING) {
                WAITING.add(animator);
                if (!started) {
                    started = true;
                    Thread thread = new Thread(FrameClock::run, "glass-frame-clock");
                    thread.setDaemon(true);
                    thread.start();
                }
                WAITING.notifyAll();
            }
        }

        private static void run() {
            long next = System.nanoTime();
            try {
                while (true) {
                    synchronized (WAITING) {
                        while (WAITING.isEmpty()) {
                            WAITING.wait();
                        }
                    }
                    // Next frame on a fixed grid, so a late wake-up is made up for; after a pause, start again now.
                    next = Math.max(next, System.nanoTime() - FRAME_NANOS);
                    sleepUntil(next);
                    next += FRAME_NANOS;
                    List<GlassAnimator> due;
                    synchronized (WAITING) {
                        due = new ArrayList<>(WAITING);
                        WAITING.clear();
                    }
                    EventQueue.invokeLater(() -> due.forEach(GlassAnimator::tick));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static void sleepUntil(long nanos) throws InterruptedException {
            long left = nanos - System.nanoTime();
            while (left > 0) {
                Thread.sleep(left / 1_000_000 + 1);
                left = nanos - System.nanoTime();
            }
        }
    }
}
