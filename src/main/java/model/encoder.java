package model;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslateException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** 完整视觉编码器的 TorchScript 调用器；复用运行时，不在 Java 中重写注意力和 FFN。 */
public final class encoder implements AutoCloseable {
    private static final Set<String> METHODS = Set.of("encoder1", "encoder2");
    private final ModelRuntime runtime;
    private final String methodName;
    private final boolean ownsRuntime;
    private boolean closed;

    public encoder(Path filename, String methodName) throws IOException, MalformedModelException {
        validateName(methodName);
        this.runtime = new ModelRuntime(filename);
        this.methodName = methodName;
        this.ownsRuntime = true;
    }

    encoder(ModelRuntime runtime, String methodName) {
        validateName(methodName);
        this.runtime = runtime;
        this.methodName = methodName;
        this.ownsRuntime = false;
    }

    private static void validateName(String name) {
        if (name == null || !METHODS.contains(name)) throw new IllegalArgumentException("未知视觉编码器: " + name);
    }

    public NDManager manager() {
        if (closed) throw new IllegalStateException("encoder 已关闭");
        return runtime.manager();
    }

    public Device device() { return manager().getDevice(); }

    public NDArray forward(NDArray features) throws TranslateException { return forward(features, null); }

    /** features: FLOAT32 [B,S,256]；memoryMask: BOOL [B,S] 或 null；true 屏蔽补边，无因果遮罩。 */
    public NDArray forward(NDArray features, NDArray memoryMask) throws TranslateException {
        try (NDManager scope = manager().newSubManager()) {
            NDArray result = forwardIn(scope, features, memoryMask);
            result.attach(manager());
            return result;
        }
    }

    NDArray forwardIn(NDManager scope, NDArray features, NDArray memoryMask) throws TranslateException {
        manager();
        Shape shape = features.getShape();
        if (features.getDataType() != DataType.FLOAT32 || shape.dimension() != 3
                || shape.get(0) < 1 || shape.get(1) < 1 || shape.get(2) != 256) {
            throw new IllegalArgumentException("features 必须为 FLOAT32 [B,S,256]");
        }
        if (memoryMask != null && (memoryMask.getDataType() != DataType.BOOLEAN
                || !memoryMask.getShape().equals(new Shape(shape.get(0), shape.get(1))))) {
            throw new IllegalArgumentException("memoryMask 必须为 BOOL [B,S]");
        }
        NDArray mask = memoryMask == null ? scope.create(new boolean[0]) : runtime.copyInput(scope, memoryMask);
        return runtime.callMini(scope, methodName, runtime.copyInput(scope, features), mask);
    }

    @Override public void close() {
        if (!closed) {
            closed = true;
            if (ownsRuntime) runtime.close();
        }
    }
}
