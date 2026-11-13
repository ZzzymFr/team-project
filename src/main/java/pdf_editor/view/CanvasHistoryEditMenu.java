package pdf_editor.view;

import view.DrawingCanvas;
import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** UI-only action: changes source LaTeX of selected imported history items, not individual strokes. */
public final class CanvasHistoryEditMenu {
    private CanvasHistoryEditMenu() { }
    public static void install(DrawingCanvas canvas,
                               java.util.function.BiFunction<String, String, BufferedImage> render) {
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { show(e); }
            @Override public void mouseReleased(MouseEvent e) { show(e); }
            private void show(MouseEvent e) {
                if (!e.isPopupTrigger() || !canvas.hasSelectedHistoryAsset()) return;
                JPopupMenu popup = new JPopupMenu();
                JMenuItem edit = new JMenuItem("Edit imported formula (LaTeX)…");
                edit.addActionListener(a -> {
                    String formula = JOptionPane.showInputDialog(canvas, "LaTeX formula",
                            canvas.selectedHistoryFormula());
                    if (formula == null || formula.isBlank()) return;
                    String answer = JOptionPane.showInputDialog(canvas, "Result (optional)",
                            canvas.selectedHistoryResult());
                    if (answer == null) return;
                    BufferedImage image = render.apply(formula, answer);
                    canvas.editHistoryAsset(formula, answer, image);
                });
                JMenuItem remove = new JMenuItem("Delete selected item");
                remove.addActionListener(a -> canvas.deleteSelection());
                popup.add(edit);popup.add(remove);
                popup.show(canvas, e.getX(), e.getY());
            }
        });
    }
}
