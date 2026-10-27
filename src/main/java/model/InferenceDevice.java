package model;

import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import java.util.Locale;

/** 包内共享的自动设备选择，不提供对外的手动配置接口。 */
final class InferenceDevice {
    private InferenceDevice() { }

    /** 自动优先 CUDA，再尝试 macOS MPS；两者均不可用时使用 CPU。 */
    static Device automatic() {
        Engine engine = Engine.getEngine("PyTorch");
        if (engine.getGpuCount() > 0 && isUsable(Device.gpu(0))) {
            return Device.gpu(0);
        }
        Device mps = Device.of("mps", 0);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") && isUsable(mps)) {
            return mps;
        }
        return Device.cpu();
    }

    private static boolean isUsable(Device candidate) {
        try {
            probe(candidate);
            return true;
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            if (reason == null) reason = e.getClass().getSimpleName();
            System.err.println("设备不可用，尝试下一设备: " + candidate + " (" + reason.lines().findFirst().orElse(reason) + ")");
            return false;
        }
    }

    private static void probe(Device candidate) {
        // DJL 的 getGpuCount() 只检测 CUDA；MPS 需实际分配并执行一个操作。
        try (NDManager probe = NDManager.newBaseManager(candidate, "PyTorch")) {
            NDArray values = probe.ones(new Shape(2, 2));
            float sum = values.add(1).sum().toFloatArray()[0];
            if (sum != 8) throw new IllegalStateException("设备探测计算结果异常");
        }
    }

}
