package model;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.pooling.Pool;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Path;

/** model.py 的 vital_pixel；只调用 CNN 的卷积、归一化和 SiLU，执行池化及二维位置编码。单线程使用。 */
public final class vital_pixel implements AutoCloseable {
    private final ModelRuntime runtime;
    private final boolean ownsRuntime;
    private boolean closed;

    public vital_pixel(Path filename) throws IOException, MalformedModelException {
        this(new ModelRuntime(filename), true);
    }

    /** vital_mini 注入同一份模型；此 CNN 不负责关闭共享运行时。 */
    vital_pixel(ModelRuntime runtime) {
        this(runtime, false);
    }

    private vital_pixel(ModelRuntime runtime, boolean ownsRuntime) {
        this.runtime = runtime;
        this.ownsRuntime = ownsRuntime;
    }

    public NDManager manager() {
        if (closed) throw new IllegalStateException("vital_pixel 已关闭");
        return runtime.manager();
    }

    public Device device() { return manager().getDevice(); }

    /** 已预处理的输入：[B,3,128,W]，W 为 16 的倍数；不重复缩放或反色。 */
    public NDArray forward(NDArray image) throws TranslateException {
        try (NDManager scope = manager().newSubManager()) {
            NDArray output = forwardIn(scope, image);
            output.attach(manager());
            return output;
        }
    }

    NDArray forwardIn(NDManager scope, NDArray image) throws TranslateException {
        manager();
        Shape shape = image.getShape();
        if (image.getDataType() != DataType.FLOAT32 || shape.dimension() != 4
                || shape.get(0) < 1 || shape.get(1) != 3 || shape.get(2) != 128
                || shape.get(3) < 16 || shape.get(3) % 16 != 0) {
            throw new IllegalArgumentException("image 必须为 FLOAT32 [B,3,128,W]，W 是 16 的正整数倍");
        }
        NDArray x = runtime.copyInput(scope, image);
        // 对应 Python cnn.forward，逐步展示各层，不在 Java 中重新定义权重。
        x = runtime.callPixel(scope, "cnn_conv", x);
        x = runtime.callPixel(scope, "cnn_norm", x);
        x = runtime.callPixel(scope, "silu", x);
        x = pool(x);
        x = runtime.callPixel(scope, "cnn_conv2", x);
        x = runtime.callPixel(scope, "cnn_norm2", x);
        x = runtime.callPixel(scope, "silu", x);
        x = pool(x);
        x = runtime.callPixel(scope, "cnn_conv3", x);
        x = runtime.callPixel(scope, "cnn_norm3", x);
        x = runtime.callPixel(scope, "silu", x);
        x = pool(x);
        x = runtime.callPixel(scope, "cnn_con4", x);
        x = runtime.callPixel(scope, "cnn_norm4", x);
        x = runtime.callPixel(scope, "silu", x);
        x = runtime.callPixel(scope, "cnn_con5", x);
        x = runtime.callPixel(scope, "cnn_norm5", x);
        return positionEmbedding(scope, x);
    }

    private static NDArray pool(NDArray x) {
        return Pool.maxPool2d(x, new Shape(2, 2), new Shape(2, 2), new Shape(0, 0), false);
    }

    private static NDArray positionEmbedding(NDManager scope, NDArray x) {
        int h = Math.toIntExact(x.getShape().get(2)), w = Math.toIntExact(x.getShape().get(3));
        float[] positions = new float[256 * h * w];
        for (int c = 0; c < 256; c++) {
            float frequency = frequency(c % 128 / 2, 128);
            for (int y = 0; y < h; y++) {
                for (int col = 0; col < w; col++) {
                    float angle = (c < 128 ? y : col) * frequency;
                    positions[(c * h + y) * w + col] = (float) (c % 2 == 0 ? Math.sin(angle) : Math.cos(angle));
                }
            }
        }
        return x.add(scope.create(positions, new Shape(1, 256, h, w)));
    }

    private static float frequency(int pair, int dimension) {
        // 保持 PyTorch float32 的指数和角度运算顺序。
        return (float) Math.exp((float) (2 * pair * (float) (-Math.log(10000.0) / dimension)));
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (ownsRuntime) runtime.close();
        }
    }
}
