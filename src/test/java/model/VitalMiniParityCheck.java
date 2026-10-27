package model;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 独立对照 Python 原模型，包含 CPU 输入到自动选择设备的复制和输入生命周期检查。 */
public final class VitalMiniParityCheck {
    private static int checked;
    private static NDArray read(DataInputStream input, NDManager manager, String kind) throws Exception {
        long[] dimensions = new long[input.readInt()];
        for (int i = 0; i < dimensions.length; i++) dimensions[i] = input.readInt();
        Shape shape = new Shape(dimensions);
        int size = Math.toIntExact(shape.size());
        if (kind.equals("long")) {
            long[] data = new long[size];
            for (int i = 0; i < size; i++) data[i] = input.readLong();
            return manager.create(data).reshape(shape);
        }
        if (kind.equals("bool")) {
            boolean[] data = new boolean[size];
            for (int i = 0; i < size; i++) data[i] = input.readBoolean();
            return manager.create(data).reshape(shape);
        }
        float[] data = new float[size];
        for (int i = 0; i < size; i++) data[i] = input.readFloat();
        return manager.create(data, shape);
    }

    private static void check(String label, NDArray actual, NDArray expected, Device device) {
        try (actual; expected) {
            if (!actual.getShape().equals(expected.getShape())) throw new AssertionError(label + " shape");
            if (!actual.getDevice().equals(device)) throw new AssertionError(label + " device: " + actual.getDevice());
            float[] values = actual.toFloatArray(), reference = expected.toFloatArray();
            double maximum = 0;
            for (int i = 0; i < values.length; i++) {
                double error = Math.abs(values[i] - reference[i]);
                if (!Float.isFinite(values[i]) || !Float.isFinite(reference[i])
                        || error > 1e-4 + 1e-4 * Math.abs(reference[i])) {
                    throw new AssertionError(label + " at " + i + ": " + values[i] + " != " + reference[i]);
                }
                maximum = Math.max(maximum, error);
            }
            checked++;
            System.out.println(label + " " + actual.getShape() + " max error=" + maximum);
        }
    }

    private static void checkRuntimeOwnership() throws Exception {
        try (ModelRuntime runtime = new ModelRuntime(Path.of("checkpoints/model_ops.pt"));
             NDManager scope = runtime.manager().newSubManager()) {
            NDArray input = scope.zeros(new Shape(1, 256));
            try {
                runtime.callPixel(scope, "head", input);
                throw new AssertionError("CNN accepted head");
            } catch (IllegalArgumentException expected) { }
            try {
                runtime.callMini(scope, "cnn_conv", input);
                throw new AssertionError("vital_mini accepted CNN layer");
            } catch (IllegalArgumentException expected) { }
            vital_pixel shared = new vital_pixel(runtime);
            shared.close();
            shared.close();
            // 关闭借用者不能关闭 vital_mini 持有的共享 .pt。
            try (NDArray output = runtime.callMini(scope, "head", input)) {
                if (!output.getShape().equals(new Shape(1, 186))) throw new AssertionError("Shared runtime closed");
                output.toFloatArray();
            }
            try {
                shared.manager();
                throw new AssertionError("Closed CNN is still accessible");
            } catch (IllegalStateException expected) { }
        }
        System.out.println("Layer boundaries and shared runtime ownership passed");
    }

    public static void main(String[] args) throws Exception {
        checkRuntimeOwnership();
        Path fixture = args.length > 0 ? Path.of(args[0]) : Path.of("target/vital-reference.bin");
        try (vital_mini model = new vital_mini();
             DataInputStream input = new DataInputStream(Files.newInputStream(fixture))) {
            model.init();
            model.init(); // 重复初始化不应重新加载或泄露模型。
            System.out.println("Automatic device: " + model.device());
            try (vital_pixel pixel = new vital_pixel(Path.of("checkpoints/model_ops.pt"));
                 NDManager cpu = NDManager.newBaseManager(Device.cpu(), "PyTorch")) {
                int count = input.readInt();
                for (int sample = 0; sample < count; sample++) {
                    try (NDManager scope = cpu.newSubManager()) {
                        NDArray image = read(input, scope, "float");
                        NDArray tokens = read(input, scope, "long");
                        NDArray memoryMask = read(input, scope, "bool");
                        NDArray tokenMask = tokens.eq(vital_mini.PAD_ID);
                        try (NDArray features = pixel.forward(image)) {
                            check("CNN + 2D position", features.reshape(features.getShape().get(0), 256, -1).transpose(0, 2, 1),
                                    read(input, scope, "float"), pixel.device());
                            check("forward", model.forward(features, tokens), read(input, scope, "float"), model.device());
                            check("forward both masks", model.forward(features, tokens, tokenMask, memoryMask),
                                    read(input, scope, "float"), model.device());
                            check("judge memory mask", model.judge(features, tokens, null, memoryMask),
                                    read(input, scope, "float"), model.device());
                            check("judge default", model.judge(features, tokens), read(input, scope, "float"), model.device());
                            check("judge logits", model.judge(features, tokens, null, memoryMask, true),
                                    read(input, scope, "float"), model.device());
                            check("forward token mask", model.forward(features, tokens, tokenMask, null),
                                    read(input, scope, "float"), model.device());
                            check("forward memory mask", model.forward(features, tokens, null, memoryMask),
                                    read(input, scope, "float"), model.device());
                            NDArray noPadding = scope.create(new boolean[Math.toIntExact(tokens.size())]).reshape(tokens.getShape());
                            check("judge explicit mask", model.judge(features, tokens, noPadding, null),
                                    read(input, scope, "float"), model.device());
                        }
                        if (!image.getDevice().equals(Device.cpu()) || !tokens.getDevice().equals(Device.cpu())) {
                            throw new AssertionError("Caller inputs were moved");
                        }
                        image.toFloatArray(); tokens.toLongArray(); memoryMask.toBooleanArray();
                    }
                }
                if (input.read() != -1) throw new AssertionError("Unread fixture data");
            }
        }
        System.out.println("Java/Python vital_mini parity passed: " + checked + " comparisons");
    }
}
