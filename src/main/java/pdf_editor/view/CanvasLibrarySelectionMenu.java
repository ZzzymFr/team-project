package pdf_editor.view;

import entity.Stroke;
import pdf_editor.adapter.SelectedInkRegistry;
import pdf_editor.use_case.SelectionToLibrary;
import view.DrawingCanvas;
import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** View-only context commands; the use case validates and publishes the result. */
public final class CanvasLibrarySelectionMenu {
    private CanvasLibrarySelectionMenu() { }
    public static void install(DrawingCanvas canvas, SelectionToLibrary action, SelectedInkRegistry registry) {
        Objects.requireNonNull(canvas); Objects.requireNonNull(action); Objects.requireNonNull(registry);
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { popup(event); }
            @Override public void mouseReleased(MouseEvent event) { popup(event); }
            private void popup(MouseEvent event) {
                if (!event.isPopupTrigger() || canvas.selectedInkSnapshot().isEmpty()) return;
                JPopupMenu menu = new JPopupMenu();
                JMenuItem formula = new JMenuItem("Recognize selection → Library");
                formula.addActionListener(e -> {
                    List<Stroke> strokes = canvas.selectedInkSnapshot();
                    BufferedImage image = canvas.selectedInkImage();
                    formula.setEnabled(false);
                    new SwingWorker<String, Void>() {
                        @Override protected String doInBackground() throws Exception { return action.recognize(image); }
                        @Override protected void done() {
                            try {
                                String latex = get();
                                if (canvas.containsAllInk(strokes)) {
                                    registry.track(action.addFormula(latex).id(), strokes);
                                }
                            } catch (Exception failure) {
                                JOptionPane.showMessageDialog(canvas, "Recognition failed: " +
                                        (failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage()));
                            }
                        }
                    }.execute();
                });
                JMenuItem ink = new JMenuItem("Add selected ink image → Library");
                ink.addActionListener(e -> {
                    List<Stroke> strokes = canvas.selectedInkSnapshot();
                    if (!strokes.isEmpty()) {
                        registry.track(action.addInk(canvas.selectedInkImage()).id(), strokes);
                    }
                });
                menu.add(formula);menu.add(ink);
                menu.show(canvas, event.getX(), event.getY());
            }
        });
    }
}
