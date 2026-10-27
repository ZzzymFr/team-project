package model;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 从 model_ops.pt 直接调用一个完整 decoder，不在 Java 中重新实现网络。
 * 包含因果自注意力、交叉注意力、FFN/SwiGLU、归一化和残差。
 * 默认按 CUDA -> MPS -> CPU 自动选择可用设备。
 * 单线程使用；返回的张量在本实例关闭前有效，也可由调用方提前 close()。
 */
public final class decoder implements AutoCloseable {
    private static final Set<String> METHODS =
            Set.of("decoder1", "decoder2", "decoder3", "judge_decoder");

    private final String methodName;
    private final Device device;
    private final Model model;
    private final NDManager manager;
    private final Predictor<NDList, NDList> predictor;
    private boolean closed;

    public decoder(Path modelFile, String methodName)
            throws IOException, MalformedModelException {
        if (methodName == null || !METHODS.contains(methodName)) {
            throw new IllegalArgumentException("未知 decoder: " + methodName);
        }
        Path path = modelFile.toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            throw new IOException("TorchScript 文件不存在: " + path);
        }
        this.methodName = methodName;
        this.device = InferenceDevice.automatic();
        Model loaded = Model.newInstance("model_ops", device, "PyTorch");
        try {
            loaded.load(path.getParent(), path.getFileName().toString());
            manager = loaded.getNDManager().newSubManager();
            predictor = loaded.newPredictor(new NoopTranslator());
            model = loaded;
        } catch (IOException | MalformedModelException | RuntimeException | Error e) {
            loaded.close();
            throw e;
        }
    }

    /** 用模型所选设备的 manager 创建输入。 */
    public NDManager manager() {
        checkOpen();
        return manager;
    }

    public Device device() {
        return device;
    }

    public NDArray forward(NDArray x, NDArray memory) throws TranslateException {
        return forward(x, memory, null, null);
    }

    /**
     * x: FLOAT32 [B,T,256]，已完成 embedding 和位置编码的 token 特征。
     * memory: FLOAT32 [B,S,256]，图像特征。
     * tokenMask: BOOL [B,T]；memoryMask: BOOL [B,S]；True 表示屏蔽。
     * 两个 mask 可为 null；因果 mask 由导出 decoder 内部自动创建。
     * 外部输入若在其他设备上，将为本次调用复制到模型设备；不改变原输入。
     * 返回模型设备上的 FLOAT32 [B,T,256]，不是经过 head 的词表 logits。
     */
    public NDArray forward(NDArray x, NDArray memory, NDArray tokenMask, NDArray memoryMask)
            throws TranslateException {
        checkOpen();
        validateFeatures(x, "x");
        validateFeatures(memory, "memory");
        if (x.getShape().get(0) != memory.getShape().get(0)) {
            throw new IllegalArgumentException("x 和 memory 的 batch 必须相同");
        }
        validateMask(tokenMask, x, "tokenMask");
        validateMask(memoryMask, memory, "memoryMask");

        // marker 只告诉 DJL 要调用的方法名，不会成为 Python 函数参数。
        try (NDManager inputsManager = manager.newSubManager()) {
            NDArray marker = inputsManager.create(0);
            NDArray emptyMask = inputsManager.create(new boolean[0]);
            marker.setName("module_method:" + methodName);
            NDList inputs = new NDList(marker, onDevice(x, inputsManager), onDevice(memory, inputsManager),
                    tokenMask == null ? emptyMask : onDevice(tokenMask, inputsManager),
                    memoryMask == null ? emptyMask : onDevice(memoryMask, inputsManager));
            NDList outputs = predictor.predict(inputs);
            if (outputs.size() != 1) {
                outputs.close();
                throw new IllegalStateException(methodName + " 应只返回一个 Tensor");
            }
            NDArray result = outputs.singletonOrThrow();
            result.attach(manager);
            return result;
        }
    }

    private NDArray onDevice(NDArray array, NDManager inputsManager) {
        if (array.getDevice().equals(device)
                || (array.getDevice().getDeviceType().equals("mps") && device.getDeviceType().equals("mps"))) {
            return array;
        }
        NDArray copy = array.toDevice(device, true);
        copy.attach(inputsManager);
        return copy;
    }

    private static void validateFeatures(NDArray array, String name) {
        if (array == null || array.getDataType() != DataType.FLOAT32
                || array.getShape().dimension() != 3 || array.getShape().get(0) < 1
                || array.getShape().get(1) < 1 || array.getShape().get(2) != 256) {
            throw new IllegalArgumentException(name + " 必须为 FLOAT32 [B,L,256]，B/L >= 1");
        }
    }

    private static void validateMask(NDArray mask, NDArray features, String name) {
        if (mask == null) return;
        Shape expected = new Shape(features.getShape().get(0), features.getShape().get(1));
        if (mask.getDataType() != DataType.BOOLEAN || !mask.getShape().equals(expected)) {
            throw new IllegalArgumentException(name + " 必须为 BOOL " + expected);
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("decoder 已关闭");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                predictor.close();
            } finally {
                try {
                    manager.close();
                } finally {
                    model.close();
                }
            }
        }
    }
}
