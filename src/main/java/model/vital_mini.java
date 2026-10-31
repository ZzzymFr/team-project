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
import ai.djl.ndarray.types.DataType;
import ai.djl.modality.cv.ImageFactory;

/**
 * 已有 CNN 特征 -> 五个 decoder -> final_norm -> head。
 * forward 返回词表 logits；judge 返回前缀判断概率。输入均为已准备好的张量。
 * 单线程使用。先 init()；返回的 NDArray 由调用方 close，最晚在模型 close 时释放。
 */
public final class vital_mini implements AutoCloseable {
    public static final int VOCAB_LENGTH = 200;
    public static final int PAD_ID = 67, BOS_ID = 68, EOS_ID = 69, MAX_LEN = 200;
    private final Path modelPath;
    private ModelRuntime runtime;
    private vital_pixel cnn;
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
            cnn = new vital_pixel(loaded);
            runtime = loaded;
        } catch (TranslateException e) {
            loaded.close();
            throw new IOException("无法通过 head 读取共享词嵌入，请使用导出的 model_ops.pt", e);
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

    private NDArray encodeIn(NDArray features) {
        return features.reshape(features.getShape().get(0), 256, -1).transpose(0, 2, 1);
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
        try (NDManager scope = manager().newSubManager()) {
            NDArray memory = encodeIn(encoded);
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
            NDArray memory = encodeIn(encoded);
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
            } finally {
                if (runtime != null) runtime.close();
                embedding = null;
            }
        }
    }

    public String generate(BufferedImage image) throws TranslateException{
        NDArray image_tensor = ImageFactory.getInstance()
                .fromImage(image)
                .toNDArray(this.manager())
                .toType(DataType.FLOAT32, false)
                .div(255f)           // 像素归一化到 [0, 1]
                .transpose(2, 0, 1)  // [H, W, 3] → [3, H, W]
                .expandDims(0);      // → [1, 3, H, W]
        NDArray encoded = cnn.forward(image_tensor);
        ArrayList<Integer> tokens = new ArrayList<>();
        tokens.add(BOS_ID);
        while(tokens.size() < MAX_LEN){
            long[] ids = tokens.stream()
                    .mapToLong(Integer::longValue)
                    .toArray();
            NDArray tokens_tensor = this.manager().create(ids).reshape(1,ids.length);
            try(
                    NDArray decoded = this.forward(encoded, tokens_tensor);
                    NDArray last = decoded.get("0,-1,:");
                    ){
                float[] scores = last.toFloatArray();
                    Integer next_token = new Integer(-1);
                    float max_score = Float.NEGATIVE_INFINITY;
                    for(int i=0;i < scores.length;++i){
                        if(i == BOS_ID || i == PAD_ID) continue;
                        if (scores[i] >= max_score){
                            max_score = scores[i];
                            next_token = i;
                        }
                    }
                    if (next_token == EOS_ID) break;
                    tokens.add(next_token);
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
