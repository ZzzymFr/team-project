package view.board.doc;

import java.awt.Point;
import java.awt.Rectangle;

/** View-local hit testing for a Library card's close icon. */
final class LibraryCardHitTarget {
    private LibraryCardHitTarget() { }
    static boolean deleteClicked(Rectangle cell, Point at) {
        return cell != null && at != null && cell.contains(at)
                && at.y < cell.y + 30 && at.x >= cell.x + cell.width - 30;
    }
}
