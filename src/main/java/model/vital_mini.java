package model;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslateException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import java.util.ArrayList;
import ai.djl.modality.cv.ImageFactory;

/**
 * 已有 CNN 特征 -> 两个视觉 encoder -> 五个 decoder -> final_norm -> head。
 * forward 返回词表 logits；judge 返回前缀判断概率。输入均为已准备好的张量。
 * 单线程使用。先 init()；返回的 NDArray 由调用方 close，最晚在模型 close 时释放。
 */
public final class vital_mini implements AutoCloseable {
    public static final int VOCAB_LENGTH = 200;
    public static final int PAD_ID = 67, BOS_ID = 68, EOS_ID = 69, MAX_LEN = 200;
    private final Path modelPath;
    private ModelRuntime runtime;
    private vital_pixel cnn;
    private encoder encoder1, encoder2;
    private float[] embedding;
    private boolean closed;

    public vital_mini() {
        this(Path.of("checkpoints", "model_ops.pt"));
    }

    /** 路径由调用方指定；设备始终自动选择。构造后调用 init() 加载。 */
    public vital_mini(Path modelPath) {
        this.modelPath = modelPath;
    }

    public void init() throws IOException, MalformedModelException {
        if (closed) throw new IllegalStateException("vital_mini 已关闭");
        if (cnn != null) return;
        ModelRuntime loaded = new ModelRuntime(modelPath);
        try (NDManager scope = loaded.manager().newSubManager()) {
            // head.weight 与 token_embedding.weight 绑定，且 head 无 bias。
            // head(I_256) = W^T，转置后便是原 Embedding，不需要新增导出函数。
            NDArray weights = loaded.callMini(scope, "head", scope.eye(256)).transpose();
            if (!weights.getShape().equals(new Shape(VOCAB_LENGTH, 256))) {
                throw new IOException("模型的 head/词表尺寸与当前 " + VOCAB_LENGTH + " 个 token 不匹配");
            }
            embedding = weights.toFloatArray();
            // 提前验证导出文件包含完整视觉编码器，避免运行到识别时才发现旧文件。
            NDArray probe = scope.zeros(new Shape(1, 1, 256));
            NDArray noMask = scope.create(new boolean[0]);
            loaded.callMini(scope, "encoder1", probe, noMask);
            loaded.callMini(scope, "encoder2", probe, noMask);
            cnn = new vital_pixel(loaded);
            encoder1 = new encoder(loaded, "encoder1");
            encoder2 = new encoder(loaded, "encoder2");
            runtime = loaded;
        } catch (TranslateException e) {
            loaded.close();
            throw new IOException("无法读取共享词嵌入或视觉编码器，请重新导出 model_ops.pt", e);
        } catch (IOException | RuntimeException | Error e) {
            loaded.close();
            throw e;
        }
    }

    public NDManager manager() { checkReady(); return runtime.manager(); }
    public Device device() { return manager().getDevice(); }
    private void checkReady() {
        if (closed) throw new IllegalStateException("vital_mini 已关闭");
        if (cnn == null) throw new IllegalStateException("请先调用 init() 加载模型");
    }

    private NDArray encodeIn(NDManager scope, NDArray features, NDArray memoryMask) throws TranslateException {
        Shape shape = features.getShape();
        if (features.getDataType() != DataType.FLOAT32 || shape.dimension() != 4
                || shape.get(0) < 1 || shape.get(1) != 256 || shape.get(2) < 1 || shape.get(3) < 1) {
            throw new IllegalArgumentException("encoded 必须为 CNN 输出 FLOAT32 [B,256,h,w]");
        }
        NDArray memory = runtime.copyInput(scope, features).reshape(shape.get(0), 256, -1).transpose(0, 2, 1);
        memory = encoder1.forwardIn(scope, memory, memoryMask);
        return encoder2.forwardIn(scope, memory, memoryMask);
    }

    public NDArray encodeFeatures(NDArray encoded) throws TranslateException {
        return encodeFeatures(encoded, null);
    }

    /** CNN 特征 -> 两层视觉编码器输出 [B,h*w,256]，可缓存供逐词生成复用。 */
    public NDArray encodeFeatures(NDArray encoded, NDArray memoryMask) throws TranslateException {
        try (NDManager scope = manager().newSubManager()) {
            NDArray memory = encodeIn(scope, encoded, memoryMask);
            memory.attach(manager());
            return memory;
        }
    }

    private static void validateBatch(NDArray tokens, NDArray memory) {
        if (memory.getShape().get(0) != tokens.getShape().get(0)) {
            throw new IllegalArgumentException("encoded 和 tokens 的 batch 必须相同");
        }
    }

    private NDArray mask(NDManager scope, NDArray mask, Shape expected, String name) {
        if (mask == null) return scope.create(new boolean[0]);
        if (mask.getDataType() != DataType.BOOLEAN || !mask.getShape().equals(expected)) {
            throw new IllegalArgumentException(name + " 必须为 BOOL " + expected);
        }
        return runtime.copyInput(scope, mask);
    }

    private static float tokenFrequency(int pair) {
        return (float) Math.exp((float) (2 * pair * (float) (-Math.log(10000.0) / 256)));
    }

    private NDArray tokenEmbedding(NDManager scope, NDArray tokens) {
        Shape s = tokens.getShape();
        if (tokens.getDataType() != DataType.INT64 || s.dimension() != 2 || s.get(0) < 1
                || s.get(1) < 1 || s.get(1) > MAX_LEN) {
            throw new IllegalArgumentException("tokens 必须为 INT64 [B,T]，1 <= T <= 300");
        }
        long[] ids = tokens.toLongArray();
        float[] values = new float[Math.multiplyExact(ids.length, 256)];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] < 0 || ids[i] >= VOCAB_LENGTH) throw new IllegalArgumentException("无效 token: " + ids[i]);
            int position = i % (int) s.get(1);
            for (int c = 0; c < 256; c++) {
                float angle = position * tokenFrequency(c / 2);
                float pe = (float) (c % 2 == 0 ? Math.sin(angle) : Math.cos(angle));
                values[i * 256 + c] = embedding[(int) ids[i] * 256 + c] * 16 + pe;
            }
        }
        // eval 模式下 embed_dropout 为恒等操作。
        return scope.create(values, new Shape(s.get(0), s.get(1), 256));
    }

    /**
     * encoded 为 CNN 输出 FLOAT32 [B,256,h,w]；tokens INT64 [B,T]
     * -> FLOAT32 [B,T,200]，返回未做 softmax 的词表 logits。
     */
    public NDArray forward(NDArray encoded, NDArray tokens) throws TranslateException {
        return forward(encoded, tokens, null, null);
    }

    /** tokenMask: BOOL [B,T]；memoryMask: BOOL [B,h*w]；true 表示屏蔽。 */
    public NDArray forward(NDArray encoded, NDArray tokens, NDArray tokenMask, NDArray memoryMask)
            throws TranslateException {
        try (NDArray memory = encodeFeatures(encoded, memoryMask)) {
            return decode(memory, tokens, tokenMask, memoryMask);
        }
    }

    public NDArray decode(NDArray memory, NDArray tokens) throws TranslateException {
        return decode(memory, tokens, null, null);
    }

    /** 已经过两个 encoder 的 memory [B,S,256] -> logits；不重复执行视觉编码。 */
    public NDArray decode(NDArray memory, NDArray tokens, NDArray tokenMask, NDArray memoryMask)
            throws TranslateException {
        try (NDManager scope = manager().newSubManager()) {
            Shape shape = memory.getShape();
            if (memory.getDataType() != DataType.FLOAT32 || shape.dimension() != 3
                    || shape.get(0) < 1 || shape.get(1) < 1 || shape.get(2) != 256) {
                throw new IllegalArgumentException("memory 必须为 FLOAT32 [B,S,256]");
            }
            memory = runtime.copyInput(scope, memory);
            NDArray x = tokenEmbedding(scope, tokens);
            validateBatch(tokens, memory);
            NDArray tm = mask(scope, tokenMask, tokens.getShape(), "tokenMask");
            NDArray mm = mask(scope, memoryMask,
                    new Shape(memory.getShape().get(0), memory.getShape().get(1)), "memoryMask");
            x = runtime.callMini(scope, "decoder1", x, memory, tm, mm);
            x = runtime.callMini(scope, "decoder2", x, memory, tm, mm);
            x = runtime.callMini(scope, "decoder3", x, memory, tm, mm);
            x = runtime.callMini(scope, "decoder4", x, memory, tm, mm);
            x = runtime.callMini(scope, "decoder5", x, memory, tm, mm);
            x = runtime.callMini(scope, "final_norm", x);
            NDArray result = runtime.callMini(scope, "head", x);
            result.attach(manager());
            return result;
        }
    }

    /** 对应 Python judge_prefix：返回 FLOAT32 [B,T,2] 概率；默认屏蔽 PAD token。 */
    public NDArray judge(NDArray encoded, NDArray tokens) throws TranslateException {
        return judge(encoded, tokens, null, null, false);
    }

    public NDArray judge(NDArray encoded, NDArray tokens, NDArray tokenMask, NDArray memoryMask)
            throws TranslateException {
        return judge(encoded, tokens, tokenMask, memoryMask, false);
    }

    /** returnLogits=true 时返回判断分支的原始 logits，false 时返回 softmax 概率。 */
    public NDArray judge(NDArray encoded, NDArray tokens, NDArray tokenMask,
                         NDArray memoryMask, boolean returnLogits) throws TranslateException {
        try (NDManager scope = manager().newSubManager()) {
            NDArray memory = encodeIn(scope, encoded, memoryMask);
            NDArray x = tokenEmbedding(scope, tokens);
            validateBatch(tokens, memory);
            NDArray tm;
            if (tokenMask == null) {
                long[] ids = tokens.toLongArray();
                boolean[] padded = new boolean[ids.length];
                for (int i = 0; i < ids.length; i++) padded[i] = ids[i] == PAD_ID;
                tm = scope.create(padded).reshape(tokens.getShape());
            } else {
                tm = mask(scope, tokenMask, tokens.getShape(), "tokenMask");
            }
            NDArray mm = mask(scope, memoryMask,
                    new Shape(memory.getShape().get(0), memory.getShape().get(1)), "memoryMask");
            x = runtime.callMini(scope, "judge_decoder", x, memory, tm, mm);
            x = runtime.callMini(scope, "judge_norm", x);
            x = runtime.callMini(scope, "process", x);
            x = runtime.callMini(scope, "silu", x);
            NDArray logits = runtime.callMini(scope, "test", x);
            NDArray result = returnLogits ? logits : logits.softmax(-1);
            result.attach(manager());
            return result;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                if (cnn != null) cnn.close();
                if (encoder1 != null) encoder1.close();
                if (encoder2 != null) encoder2.close();
            } finally {
                if (runtime != null) runtime.close();
                embedding = null;
            }
        }
    }

    public String generate(BufferedImage image) throws TranslateException{
        ArrayList<Integer> tokens = new ArrayList<>();
        tokens.add(BOS_ID);
        try (NDManager scope = manager().newSubManager()) {
            NDArray image_tensor = ImageFactory.getInstance()
                    .fromImage(image)
                    .toNDArray(scope)
                    .toType(DataType.FLOAT32, false)
                    .div(255f)           // 像素归一化到 [0, 1]
                    .transpose(2, 0, 1)  // [H, W, 3] → [3, H, W]
                    .expandDims(0);      // → [1, 3, H, W]
            // 图像编码只计算一次；所有 token 步骤复用 memory。
            try (NDArray encoded = cnn.forward(image_tensor);
                 NDArray memory = encodeFeatures(encoded)) {
                while (tokens.size() < MAX_LEN) {
                    long[] ids = tokens.stream().mapToLong(Integer::longValue).toArray();
                    try (NDArray tokens_tensor = scope.create(ids).reshape(1, ids.length);
                         NDArray decoded = decode(memory, tokens_tensor);
                         NDArray last = decoded.get("0,-1,:")) {
                        float[] scores = last.toFloatArray();
                        int next_token = -1;
                        float max_score = Float.NEGATIVE_INFINITY;
                        for (int i = 0; i < scores.length; ++i) {
                            if (i == BOS_ID || i == PAD_ID) continue;
                            if (scores[i] >= max_score) {
                                max_score = scores[i];
                                next_token = i;
                            }
                        }
                        if (next_token == EOS_ID) break;
                        tokens.add(next_token);
                    }
                }
            }
        }
        String result;
        try (var reader = java.nio.file.Files.newBufferedReader(
                Path.of("src/main/java/model/checkpoints/tokeniser.json"))) {
            var vocab = com.google.gson.JsonParser.parseReader(reader)
                    .getAsJsonObject().getAsJsonObject("base").getAsJsonObject("vocab");
            String[] table = new String[VOCAB_LENGTH];
            for (var entry : vocab.entrySet()) {
                table[entry.getValue().getAsInt()] = entry.getKey();
            }

            StringBuilder text = new StringBuilder();
            String previous = "";
            for (int id : tokens) {
                if (id == EOS_ID) break;
                if (id == BOS_ID || id == PAD_ID) continue;
                if (id < 0 || id >= table.length || table[id] == null) {
                    throw new TranslateException("词表中不存在 token ID: " + id);
                }
                String token = table[id];
                // 命令后紧跟英文字母时补空格，例如 \alpha + x -> \alpha x。
                if (previous.matches("\\\\[A-Za-z]+") && !token.isEmpty()
                        && ((token.charAt(0) >= 'a' && token.charAt(0) <= 'z')
                        || (token.charAt(0) >= 'A' && token.charAt(0) <= 'Z'))) {
                    text.append(' ');
                }
                text.append(token);
                previous = token;
            }
            result = text.toString().strip();
        } catch (IOException | RuntimeException e) {
            throw new TranslateException("读取或解析 tokeniser.json 失败", e);
        }
        return result;


    }
}
