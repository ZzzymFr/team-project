package pdf_editor.data_access;

import pdf_editor.adapter.CanvasSnapshotGateway;
import view.canvas.SharedCanvas;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Read-only infrastructure adapter for US1/US2/US3's shared Swing canvas. */
public final class DrawingCanvasSnapshotAdapter implements CanvasSnapshotGateway {
    private final SharedCanvas source;
    public DrawingCanvasSnapshotAdapter(SharedCanvas source) {
        this.source = Objects.requireNonNull(source, "source");
    }
    @Override public BufferedImage capture() {
        return new CanvasCapture(source.getDrawingCanvas()).overview();
    }
}
