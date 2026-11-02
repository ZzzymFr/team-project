package interface_adapter.evaluate;

import entity.UncalculableReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UncalculableTextTest {

    @Test
    void everyReasonHasWords() {
        for (UncalculableReason reason : UncalculableReason.values()) {
            assertNotEquals("Uncalculable", UncalculableText.of(reason.name(), "\\sin"), reason.name());
        }
    }

    @Test
    void noReasonIsJustUncalculable() {
        assertEquals("Uncalculable", UncalculableText.of(null, null));
    }

    @Test
    void wordsStayTheSame() {
        assertEquals("Uncalculable — Number too big", UncalculableText.of("TOO_BIG", null));
        assertEquals("Uncalculable — sin⁻¹ is ambiguous", UncalculableText.of("INVERSE_AMBIGUOUS", "\\sin"));
    }
}
