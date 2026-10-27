package model;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 独立数值对照测试，不依赖未完成的应用类或 JUnit。 */
public final class DecoderParityCheck {
    private static NDArray readTensor(DataInputStream input, NDManager manager, boolean bool) throws Exception {
        long[] dimensions = new long[input.readInt()];
        for (int i = 0; i < dimensions.length; i++) dimensions[i] = input.readInt();
        Shape shape = new Shape(dimensions);
        int size = Math.toIntExact(shape.size());
        if (bool) {
            boolean[] data = new boolean[size];
            for (int i = 0; i < size; i++) data[i] = input.readBoolean();
            return manager.create(data).reshape(shape);
        }
        float[] data = new float[size];
        for (int i = 0; i < size; i++) data[i] = input.readFloat();
        return manager.create(data, shape);
    }

    private static double compare(NDArray actual, NDArray expected) {
        if (!actual.getShape().equals(expected.getShape())) {
            throw new AssertionError("Shape mismatch: " + actual.getShape() + " != " + expected.getShape());
        }
        float[] values = actual.toFloatArray();
        float[] reference = expected.toFloatArray();
        double maximum = 0;
        for (int i = 0; i < values.length; i++) {
            double error = Math.abs(values[i] - reference[i]);
            if (!Float.isFinite(values[i]) || !Float.isFinite(reference[i])
                    || error > 1e-4 + 1e-4 * Math.abs(reference[i])) {
                throw new AssertionError("Value mismatch at " + i + ": " + values[i] + " != " + reference[i]);
            }
            maximum = Math.max(maximum, error);
        }
        return maximum;
    }

    public static void main(String[] args) throws Exception {
        Path modelFile = Path.of("checkpoints", "model_ops.pt");
        Path fixture = args.length > 0 ? Path.of(args[0]) : Path.of("target", "decoder-reference.bin");
        double maximum = 0;
        int checked = 0;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(fixture));
             NDManager base = NDManager.newBaseManager(Device.cpu(), "PyTorch")) {
            int cases = input.readInt();
            for (int sample = 0; sample < cases; sample++) {
                try (NDManager manager = base.newSubManager()) {
                    NDArray x = readTensor(input, manager, false);
                    NDArray memory = readTensor(input, manager, false);
                    NDArray tokenMask = readTensor(input, manager, true);
                    NDArray memoryMask = readTensor(input, manager, true);
                    for (String name : new String[] {"decoder1", "decoder2", "decoder3", "judge_decoder"}) {
                        try (decoder block = new decoder(modelFile, name)) {
                            Device selected = block.device();
                            if (checked == 0) System.out.println("Testing decoder device: " + selected);
                            for (int mode = 0; mode < 4; mode++) {
                                NDArray tm = (mode & 1) == 0 ? null : tokenMask;
                                NDArray mm = (mode & 2) == 0 ? null : memoryMask;
                                try (NDArray expected = readTensor(input, manager, false);
                                     NDArray actual = block.forward(x, memory, tm, mm)) {
                                    if (!actual.getDevice().equals(selected)) {
                                        throw new AssertionError("Output on unexpected device: " + actual.getDevice());
                                    }
                                    maximum = Math.max(maximum, compare(actual, expected));
                                    checked++;
                                }
                            }
                        }
                        // GPU 调用只复制输入，不能迁移或关闭调用方的 CPU 输入。
                        if (!x.getDevice().equals(Device.cpu()) || !memory.getDevice().equals(Device.cpu())) {
                            throw new AssertionError("Caller inputs were moved");
                        }
                        x.toFloatArray();
                        memory.toFloatArray();
                        tokenMask.toBooleanArray();
                        memoryMask.toBooleanArray();
                    }
                }
            }
        }
        System.out.println("Java/Python decoder parity passed: " + checked + " cases, max error=" + maximum);
    }
}
