import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import model.vital_mini;
import model.vital_pixel;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** 独立运行，无 JUnit 依赖；参考数据由 Python 原模型生成。 */
public final class ModelOpsParityCheck {
    private static Shape readShape(DataInputStream input) throws Exception {
        long[] dimensions = new long[input.readInt()];
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = input.readInt();
        }
        return new Shape(dimensions);
    }

    private static NDArray readTensor(DataInputStream input, NDManager manager, String type) throws Exception {
        Shape shape = readShape(input);
        int size = Math.toIntExact(shape.size());
        if (type.equals("long")) {
            long[] data = new long[size];
            for (int i = 0; i < size; i++) data[i] = input.readLong();
            return manager.create(data).reshape(shape);
        }
        if (type.equals("bool")) {
            boolean[] data = new boolean[size];
            for (int i = 0; i < size; i++) data[i] = input.readBoolean();
            return manager.create(data).reshape(shape);
        }
        float[] data = new float[size];
        for (int i = 0; i < size; i++) data[i] = input.readFloat();
        return manager.create(data, shape);
    }

    private static void check(String label, NDArray actual, NDArray expected) {
        if (!Arrays.equals(actual.getShape().getShape(), expected.getShape().getShape())) {
            throw new AssertionError(label + " shape: " + actual.getShape() + " != " + expected.getShape());
        }
        float[] values = actual.toFloatArray();
        float[] references = expected.toFloatArray();
        double maxError = 0;
        for (int i = 0; i < values.length; i++) {
            double error = Math.abs(values[i] - references[i]);
            maxError = Math.max(maxError, error);
            if (!Float.isFinite(values[i]) || error > 1e-4 + 1e-4 * Math.abs(references[i])) {
                throw new AssertionError(label + " differs at " + i + ": " + values[i] + " != " + references[i]);
            }
        }
        System.out.println(label + " " + actual.getShape() + " max error=" + maxError);
    }

    public static void main(String[] args) throws Exception {
        Path modelPath = args.length > 0 ? Path.of(args[0]) : Path.of("checkpoints/model_ops.pt");
        Path fixtures = args.length > 1 ? Path.of(args[1]) : Path.of("target/model-ops-reference.bin");
        try (vital_mini model = new vital_mini(modelPath);
             vital_pixel pixel = new vital_pixel(modelPath);
             DataInputStream input = new DataInputStream(Files.newInputStream(fixtures))) {
            model.init();
            int count = input.readInt();
            for (int i = 0; i < count; i++) {
                try (NDManager manager = model.manager().newSubManager()) {
                    NDArray image = readTensor(input, manager, "float");
                    NDArray tokens = readTensor(input, manager, "long");
                    NDArray memoryPadding = readTensor(input, manager, "bool");
                    try (NDArray features = pixel.forward(image);
                         NDArray memory = features.reshape(features.getShape().get(0), 256, -1).transpose(0, 2, 1);
                         NDArray decoded = model.forward(features, tokens);
                         NDArray padding = tokens.eq(vital_mini.PAD_ID);
                         NDArray masked = model.forward(features, tokens, padding, memoryPadding);
                         NDArray judged = model.judge(features, tokens, null, memoryPadding)) {
                        check("encoder", memory, readTensor(input, manager, "float"));
                        check("decode", decoded, readTensor(input, manager, "float"));
                        check("masked decode", masked, readTensor(input, manager, "float"));
                        check("judge", judged, readTensor(input, manager, "float"));
                    }
                }
            }
        }
        System.out.println("Java/Python parity passed.");
    }
}
