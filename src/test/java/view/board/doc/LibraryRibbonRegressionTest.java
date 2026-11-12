package view.board.doc;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Only Insert offers Library; View must not duplicate the command. */
class LibraryRibbonRegressionTest {
    private static DocEditorPanel editor() {
        DocumentEditor document = new DocumentEditor();
        return new DocEditorPanel(new PdfEditorController(new MathLibrary(), document,
                new AutoLayoutInteractor(document), new PdfExportInteractor((pdf, path) -> {}, (pdf, dpi) -> List.of())));
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) matches.addAll(find(child, type));
        }
        return matches;
    }

    private static void tab(DocEditorPanel editor, String title) {
        JToggleButton button = find(editor, JToggleButton.class).stream()
                .filter(b -> title.equals(b.getText())).findFirst().orElseThrow();
        button.doClick();
    }

    private static long activeLibraryCommands(DocEditorPanel editor) {
        return find(editor, RibbonButton.class).stream()
                .filter(b -> "ribbon.Library".equals(b.getName())).count();
    }

    @Test void libraryAppearsInInsertButNotView() {
        DocEditorPanel editor = editor();
        try {
            tab(editor, "Insert");
            assertEquals(1L, activeLibraryCommands(editor));
            tab(editor, "View");
            assertEquals(0L, activeLibraryCommands(editor));
            tab(editor, "Home");
            assertEquals(0L, activeLibraryCommands(editor));
        } finally { editor.close(); }
    }

    @Test void libraryHeaderHasDistinctAccessibleCloseControlAndActuallyCloses() {
        DocEditorPanel editor = editor();
        try {
            editor.showLibrary();
            assertTrue(editor.isLibraryVisible());
            LibraryHeaderCloseButton button = find(editor, LibraryHeaderCloseButton.class).stream()
                    .findFirst().orElseThrow();
            assertEquals("Close Library", button.getToolTipText());
            assertEquals("Close Library", button.getAccessibleContext().getAccessibleName());
            assertEquals("libraryCloseButton", button.getName());
            assertTrue(button.getPreferredSize().width >= 27);
            button.doClick();
            assertFalse(editor.isLibraryVisible());
        } finally { editor.close(); }
    }
}
