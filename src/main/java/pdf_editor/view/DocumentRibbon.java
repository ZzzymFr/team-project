package pdf_editor.view;

import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import view.DrawingCanvas;
import view.canvas.SharedCanvas;
import view.glass.GlassIcons;
import view.ribbon.Ribbon;
import view.ribbon.RibbonButton;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The document's part of the ribbon at the top of the window, like Word's: Save, Import and Export
 * PDF as coloured icons at the left of the title row, and the Edit tab with the library, a note, the
 * arrangement of items and Delete. Export writes one PDF page per sheet that has ink or items on it, the ink drawn
 * over the items as on the canvas, then the appendix of LaTeX sources.
 */
public final class DocumentRibbon {
    /** Pixels per world unit in the handwriting pictures of the PDF (192 dpi). */
    private static final double INK_RESOLUTION = 2.0;
    private static final Color SAVE_COLOR = new Color(0x2B6CD8);    // blue, like Word's Save
    private static final Color IMPORT_COLOR = new Color(0x1E9E5A);  // green
    private static final Color PDF_COLOR = new Color(0xD7372B);     // the red of PDF files

    private final SharedCanvas canvas;
    private final PdfEditorController controller;
    private final PaperItemsLayer items;
    private final LibraryPanel library;
    private final Ribbon ribbon;
    private JButton saveButton;
    private JButton importButton;
    private JButton exportButton;
    private Ribbon.Tab editTab;
    private RibbonButton libraryButton;
    private RibbonButton noteButton;
    private RibbonButton layoutButton;
    private RibbonButton frontButton;
    private RibbonButton backButton;
    private RibbonButton splitButton;
    private RibbonButton deleteButton;

    /** Adds the file commands and the Edit tab to the ribbon. */
    public DocumentRibbon(Ribbon ribbon, SharedCanvas canvas, PdfEditorController controller, PaperItemsLayer items,
                          LibraryPanel library) {
        this.ribbon = Objects.requireNonNull(ribbon);
        this.canvas = Objects.requireNonNull(canvas);
        this.controller = Objects.requireNonNull(controller);
        this.items = Objects.requireNonNull(items);
        this.library = Objects.requireNonNull(library);
        build();
    }

    private void build() {
        saveButton = ribbon.addQuickAction("Save", GlassIcons::save, SAVE_COLOR, () -> notReady("Save"));
        importButton = ribbon.addQuickAction("Import", GlassIcons::importFile, IMPORT_COLOR, () -> notReady("Import"));
        exportButton = ribbon.addQuickAction("Export PDF", GlassIcons::pdf, PDF_COLOR, this::export);

        DrawingCanvas drawing = canvas.getDrawingCanvas();
        editTab = ribbon.addTab("Edit", () -> true);
        libraryButton = editTab.addButton("Library", GlassIcons::books, this::toggleLibrary, () -> true);
        noteButton = editTab.addButton("Note", GlassIcons::note, items::addNote, () -> true);
        editTab.addSeparator();
        layoutButton = editTab.addButton("Auto Layout", GlassIcons::layout, items::autoLayout,
                () -> !controller.library().items().isEmpty());
        frontButton = editTab.addButton("Front", GlassIcons::bringFront, () -> items.arrange(2), items::hasSelection);
        backButton = editTab.addButton("Back", GlassIcons::sendBack, () -> items.arrange(-2), items::hasSelection);
        splitButton = editTab.addButton("Split", GlassIcons::scissors, items::split, items::canSplit);
        editTab.addSeparator();
        deleteButton = editTab.addButton("Delete", GlassIcons::trash, drawing::deleteSelection,
                drawing::hasSelection);
    }

    private void notReady(String command) {
        GlassDialogs.message(ribbon, command + " is not ready yet",
                command + " is coming in a later version. Export PDF already works.");
    }

    private void toggleLibrary() {
        library.setVisible(!library.isVisible());
    }

    /** Asks where to save, then exports there. */
    private void export() {
        Window window = SwingUtilities.getWindowAncestor(ribbon);
        FileDialog dialog = new FileDialog(window instanceof Frame ? (Frame) window : null, "Save PDF", FileDialog.SAVE);
        dialog.setFile("mathlab-notes.pdf");
        dialog.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        dialog.setVisible(true);
        String name = dialog.getFile();
        if (name == null) {
            return;
        }
        File output = new File(dialog.getDirectory(), name);
        if (!output.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            output = new File(output.getPath() + ".pdf");
        }
        if (output.exists() && !GlassDialogs.confirm(ribbon, "Replace existing PDF?",
                "A file with this name already exists. Replace it?", "Replace")) {
            return;
        }
        try {
            exportTo(output.toPath());
            GlassDialogs.message(ribbon, "PDF saved", "Saved to: " + output.getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            GlassDialogs.message(ribbon, "Export failed",
                    e.getMessage() == null ? "An unexpected error occurred." : e.getMessage());
        }
    }

    /** Writes the PDF: every sheet up to the last with ink or items, the ink over the items. */
    void exportTo(Path destination) throws IOException {
        DrawingCanvas drawing = canvas.getDrawingCanvas();
        int pages = Math.max(1, Math.max(drawing.getInkPageCount(), items.pagesUsed()));
        PdfDocument document = controller.documentSnapshot();
        while (document.pageCount() < pages) {
            document.addPage();
        }
        while (document.pageCount() > pages) {
            document.removePage(document.pageCount() - 1);
        }
        List<BufferedImage> ink = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            ink.add(drawing.renderSheetInk(page, INK_RESOLUTION));
        }
        controller.export(document, ink, destination);
    }

    Ribbon.Tab editTab() {
        return editTab;
    }

    JButton saveButton() {
        return saveButton;
    }

    JButton importButton() {
        return importButton;
    }

    JButton exportButton() {
        return exportButton;
    }

    RibbonButton libraryButton() {
        return libraryButton;
    }

    RibbonButton noteButton() {
        return noteButton;
    }

    RibbonButton layoutButton() {
        return layoutButton;
    }

    RibbonButton frontButton() {
        return frontButton;
    }

    RibbonButton backButton() {
        return backButton;
    }

    RibbonButton splitButton() {
        return splitButton;
    }

    RibbonButton deleteButton() {
        return deleteButton;
    }
}
