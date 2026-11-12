package view.board.doc;

import org.junit.jupiter.api.Test;
import java.awt.Point;
import java.awt.Rectangle;
import static org.junit.jupiter.api.Assertions.*;
class LibraryCardHitTargetTest {
    @Test void topRightCrossDeletesOnlyWithinCard() {
        Rectangle card = new Rectangle(0, 77, 180, 76);
        assertTrue(LibraryCardHitTarget.deleteClicked(card, new Point(170, 87)));
        assertFalse(LibraryCardHitTarget.deleteClicked(card, new Point(130, 87)));
        assertFalse(LibraryCardHitTarget.deleteClicked(card, new Point(170, 125)));
        assertFalse(LibraryCardHitTarget.deleteClicked(card, new Point(170, 70)));
    }
    @Test void nullCellNeverDeletes() {
        assertFalse(LibraryCardHitTarget.deleteClicked(null,new Point(4,4)));
    }
}
