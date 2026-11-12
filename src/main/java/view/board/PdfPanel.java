package view.board;

import interface_adapter.history.HistoryItem;
import pdf_editor.adapter.PdfEditorController;
import view.board.doc.DocEditorPanel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/** Visual adapter: the peer's Word-style PDF panel, backed by real US4 use cases. */
public final class PdfPanel extends JPanel implements PdfWorkspacePort {
    private final DocEditorPanel editor;
    public PdfPanel(PdfEditorController controller) {
        super(new BorderLayout());
        editor = new DocEditorPanel(controller);
        add(editor, BorderLayout.CENTER);
    }
    public void setCanvasCapture(Supplier<BufferedImage> capture) { editor.setCanvasCapture(capture); }
    public void setRegionCapture(Runnable command) { editor.setRegionCapture(command); }
    public DocEditorPanel editor() { return editor; }
    public void close() { editor.close(); }
    @Override public void fitWhenOpened() { editor.fitWhenOpened(); }
    @Override public void addHistoryItem(HistoryItem item) { editor.addHistoryItem(item); }
    @Override public boolean dropHistoryItem(HistoryItem item, Point screenTopLeft) {
        return editor.dropHistoryItem(item, screenTopLeft);
    }
}
