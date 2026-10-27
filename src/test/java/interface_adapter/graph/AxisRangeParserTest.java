package interface_adapter.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxisRangeParserTest {

    @Test
    void blankOrAutoMeansTheDefault() {
        assertNull(AxisRangeParser.parse("", "x"));
        assertNull(AxisRangeParser.parse("   ", "x"));
        assertNull(AxisRangeParser.parse(null, "x"));
        assertNull(AxisRangeParser.parse("auto", "y"));
        assertNull(AxisRangeParser.parse("AUTO", "y"));
    }

    @Test
    void acceptsTheUsualWaysOfWritingARange() {
        double[] expected = {-5, 5};
        for (String text : new String[]{"-5, 5", "-5,5", "-5 5", "[-5, 5]", "(-5; 5)", "-5 to 5", "-5 ~ 5", "−5, 5"}) {
            assertArrayEquals(expected, AxisRangeParser.parse(text, "x"), 1e-12, text);
        }
        assertArrayEquals(new double[]{0.001, 2.5}, AxisRangeParser.parse("1e-3, 2.5", "x"), 1e-12);
        assertArrayEquals(new double[]{-20, -10}, AxisRangeParser.parse("-20,-10", "x"), 1e-12);
    }

    @Test
    void rejectsWhatIsNotTwoNumbers() {
        for (String text : new String[]{"5", "a, b", "1, 2, 3", "-5,", "NaN, 3", "1, Infinity"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> AxisRangeParser.parse(text, "x"), text);
            assertTrue(e.getMessage().startsWith("The x range should be two numbers"), e.getMessage());
        }
    }

    @Test
    void rejectsARangeThatIsBackwardsOrEmpty() {
        IllegalArgumentException backwards = assertThrows(IllegalArgumentException.class,
                () -> AxisRangeParser.parse("5, -5", "y"));
        assertTrue(backwards.getMessage().contains("y range must go from a smaller number"));
        assertThrows(IllegalArgumentException.class, () -> AxisRangeParser.parse("3, 3", "y"));
    }
}
