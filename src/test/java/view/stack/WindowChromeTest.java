package view.stack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** On macOS the see-through window flickered when switching pages and when hovering a title. */
class WindowChromeTest {

    @Test
    void windowsKeepsTheSeeThroughWindow() {
        assertTrue(WindowChrome.useTranslucentWindow("Windows 11", true));
    }

    @Test
    void macOsGetsAnOpaqueWindow() {
        assertFalse(WindowChrome.useTranslucentWindow("Mac OS X", true));
        assertFalse(WindowChrome.useTranslucentWindow("macOS", true));
    }

    @Test
    void noTranslucencySupportMeansOpaqueEverywhere() {
        assertFalse(WindowChrome.useTranslucentWindow("Windows 11", false));
        assertFalse(WindowChrome.useTranslucentWindow("Linux", false));
    }
}
