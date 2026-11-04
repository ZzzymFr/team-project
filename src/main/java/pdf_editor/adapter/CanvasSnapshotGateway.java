package pdf_editor.adapter;

import java.awt.image.BufferedImage;

/** Input port for read-only access to the current Math Lab canvas. */
@FunctionalInterface
public interface CanvasSnapshotGateway {
    BufferedImage capture();
}
