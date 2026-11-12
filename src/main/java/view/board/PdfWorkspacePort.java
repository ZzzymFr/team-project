package view.board;

import interface_adapter.history.HistoryItem;
import java.awt.Point;

/** Narrow UI-facing port: the one-board layout never manipulates PDF document internals. */
public interface PdfWorkspacePort {
    void fitWhenOpened();
    void addHistoryItem(HistoryItem item);
    boolean dropHistoryItem(HistoryItem item, Point screenTopLeft);
}
