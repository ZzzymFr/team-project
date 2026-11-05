package view.glass;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassAnimatorTest {
    private static final int A_FEW_FRAMES_MILLIS = 60;

    /** Lets any ticks still on their way arrive and run. */
    private static void settle() throws Exception {
        Thread.sleep(A_FEW_FRAMES_MILLIS);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void itRunsOnTheEventThreadUntilStopped() throws Exception {
        AtomicInteger steps = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        GlassAnimator[] animator = new GlassAnimator[1];
        animator[0] = new GlassAnimator(() -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            if (steps.incrementAndGet() == 5) {
                animator[0].stop();
                done.countDown();
            }
        });
        SwingUtilities.invokeAndWait(animator[0]::start);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        settle();
        assertEquals(5, steps.get(), "no step after stop");
    }

    @Test
    void startingWhileRunningDoesNotDoubleTheSpeed() throws Exception {
        AtomicInteger steps = new AtomicInteger();
        GlassAnimator[] animator = new GlassAnimator[1];
        animator[0] = new GlassAnimator(() -> {
            steps.incrementAndGet();
            animator[0].stop();
        });
        SwingUtilities.invokeAndWait(() -> {
            animator[0].start();
            animator[0].start();
            animator[0].stop();
            animator[0].start();  // one tick is still queued: it must be reused
        });
        settle();
        assertEquals(1, steps.get());
    }

    @Test
    void itCanStartAgainAfterStopping() throws Exception {
        AtomicInteger steps = new AtomicInteger();
        GlassAnimator[] animator = new GlassAnimator[1];
        animator[0] = new GlassAnimator(() -> {
            steps.incrementAndGet();
            animator[0].stop();
        });
        SwingUtilities.invokeAndWait(animator[0]::start);
        settle();
        SwingUtilities.invokeAndWait(animator[0]::start);
        settle();
        assertEquals(2, steps.get());
    }

    @Test
    void itNeverGoesFasterThanTheCap() throws Exception {
        int frames = 60;
        AtomicInteger steps = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        long[] first = new long[1];
        long[] last = new long[1];
        GlassAnimator[] animator = new GlassAnimator[1];
        animator[0] = new GlassAnimator(() -> {
            int step = steps.incrementAndGet();
            if (step == 1) {
                first[0] = System.nanoTime();
            }
            if (step == frames) {
                last[0] = System.nanoTime();
                animator[0].stop();
                done.countDown();
            }
        });
        SwingUtilities.invokeAndWait(animator[0]::start);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        double seconds = (last[0] - first[0]) / 1e9;
        double rate = (frames - 1) / seconds;
        // a small margin: the clock makes up for late wake-ups, so a few frames can come close together
        assertTrue(rate <= GlassAnimator.MAX_FRAMES_PER_SECOND * 1.1, "ran at " + rate + " frames per second");
    }

    @Test
    void animatorsShareTheClockWithoutSlowingEachOther() throws Exception {
        int frames = 30;
        CountDownLatch done = new CountDownLatch(3);
        long start = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            AtomicInteger steps = new AtomicInteger();
            GlassAnimator[] animator = new GlassAnimator[1];
            animator[0] = new GlassAnimator(() -> {
                if (steps.incrementAndGet() == frames) {
                    animator[0].stop();
                    done.countDown();
                }
            });
            SwingUtilities.invokeAndWait(animator[0]::start);
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        double millis = (System.nanoTime() - start) / 1e6;
        assertTrue(millis < 3 * frames * 1000.0 / GlassAnimator.MAX_FRAMES_PER_SECOND,
                "three animators of " + frames + " frames took " + millis + " ms: they should run side by side");
    }

    @Test
    void elapsedFramesFollowsTheClockAndIsCappedAfterAStall() throws Exception {
        GlassAnimator animator = new GlassAnimator(() -> { });
        animator.elapsedFrames();
        Thread.sleep(8);
        float frames = animator.elapsedFrames();
        assertTrue(frames > 0.4f, "8 ms is half a 16 ms reference frame, was " + frames);

        Thread.sleep(100);
        assertEquals(2f, animator.elapsedFrames(), 0.001f);
    }

    @Test
    void easingAddsUpTheSameHoweverTheTimeIsCut() {
        float whole = GlassAnimator.ease(0.25f, 1f);
        float remaining = 1f;
        for (int i = 0; i < 100; i++) {
            remaining *= 1f - GlassAnimator.ease(0.25f, 0.01f);
        }
        assertEquals(whole, 1f - remaining, 0.001f);
    }
}
