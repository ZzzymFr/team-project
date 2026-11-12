package pdf_editor.view;

import view.DrawingCanvas;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Explicit Ctrl+Shift+drag exports currently selected canvas ink as an image to the PDF page. */
public final class CanvasPdfDragController {
    private CanvasPdfDragController() { }

    public static void install(DrawingCanvas canvas) {
        TransferHandler handler = new TransferHandler() {
            @Override protected Transferable createTransferable(JComponent c) {
                BufferedImage image;
                try { image = canvas.toFormulaImage(); }
                catch (RuntimeException noInk) { return null; }
                if (image == null) return null;
                return new Transferable() {
                    @Override public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[]{DataFlavor.imageFlavor};
                    }
                    @Override public boolean isDataFlavorSupported(DataFlavor f) {
                        return DataFlavor.imageFlavor.equals(f);
                    }
                    @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                        if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
                        return image;
                    }
                };
            }
            @Override public int getSourceActions(JComponent c) { return COPY; }
        };
        canvas.setTransferHandler(handler);
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent event) {
                if (event.isControlDown() && event.isShiftDown()) {
                    handler.exportAsDrag(canvas, event, TransferHandler.COPY);
                }
            }
        });
    }
}
