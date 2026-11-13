package view;

import org.junit.jupiter.api.Test;
import workspace.domain.CanvasState;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end model adaptation: restore as editable canvas items, never one flattened screenshot. */
class CanvasWorkspaceRoundTripTest {
    private BufferedImage image() {
        BufferedImage picture=new BufferedImage(120,40,BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(5,5,Color.RED.getRGB());return picture;
    }
    private void dispatch(DrawingCanvas c,int type,int x,int y) {
        c.dispatchEvent(new MouseEvent(c,type,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
    }
    @Test void historyAssetCanBeDeletedAndRestoredByCanvasUndo() {
        var canvas=new DrawingCanvas();canvas.setSize(600,400);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        canvas.addHistoryAsset(image(),"y=x","",new Point(110,100));
        assertEquals(1,canvas.getPictureCount());
        // History import is selected immediately and is a normal deletable canvas object.
        assertTrue(canvas.hasSelection());
        canvas.deleteSelection();assertEquals(0,canvas.getPictureCount());
        canvas.undo();assertEquals(1,canvas.getPictureCount());
    }
    @Test void importedMathSupportsSourceEditWithoutRasterFlattening() {
        var canvas=new DrawingCanvas();canvas.setSize(600,400);
        canvas.addHistoryAsset(image(),"y=x","",new Point(110,100));
        assertTrue(canvas.hasSelectedHistoryAsset());
        assertTrue(canvas.editHistoryAsset("y=2x","",image()));
        assertEquals("y=2x",canvas.selectedHistoryFormula());
        canvas.undo();
        var saved=canvas.exportCanvasState();
        assertEquals("y=x",saved.assets().get(0).latex());
    }
    @Test void savedCanvasRetainsImportedAssetAndRestoresEditableSelection() throws IOException {
        var canvas=new DrawingCanvas();canvas.setSize(700,450);
        canvas.addHistoryAsset(image(),"3x=6","x=2",new Point(70,90));
        CanvasState saved=canvas.exportCanvasState();
        var reopened=new DrawingCanvas();reopened.restoreCanvasState(saved);
        assertEquals(1,reopened.getPictureCount());
        assertEquals("3x=6",reopened.exportCanvasState().assets().get(0).latex());
        reopened.setTool(DrawingCanvas.Tool.RECTANGLE);
        dispatch(reopened,MouseEvent.MOUSE_PRESSED,75,95);
        assertEquals("3x=6",reopened.selectedHistoryFormula());
        reopened.deleteSelection();assertEquals(0,reopened.getPictureCount());
    }
    @Test void importedHistoryObjectCanMoveAndUndoUsingBoxTool() {
        var canvas = new DrawingCanvas();canvas.setSize(600,400);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        canvas.addHistoryAsset(image(), "y=x", "", new Point(100,100));
        double left=canvas.exportCanvasState().assets().get(0).x();
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, 122,109);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, 172,129);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 172,129);
        assertEquals(left+50, canvas.exportCanvasState().assets().get(0).x());
        canvas.undo();
        assertEquals(left,canvas.exportCanvasState().assets().get(0).x());
    }
    @Test void originalPenStrokeCoordinatesAndInkColorSurviveRestore() throws IOException {
        var canvas = new DrawingCanvas();canvas.setSize(600,400);
        canvas.setInkColor(Color.BLUE);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, 80,110);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, 110,130);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 110,130);
        var before=canvas.exportCanvasState();
        assertEquals(1,before.ink().size());
        assertEquals(Color.BLUE.getRGB(),before.ink().get(0).argb());
        var reopened = new DrawingCanvas();reopened.restoreCanvasState(before);
        var after=reopened.exportCanvasState();
        assertEquals(before.ink().get(0).argb(),after.ink().get(0).argb());
        assertEquals(before.ink().get(0).points(),after.ink().get(0).points());
    }
    @Test void clearAndUndoAlsoRestoreImportedPictures() {
        var canvas=new DrawingCanvas();canvas.addHistoryAsset(image(),"y=x","",new Point(5,5));
        canvas.clear();assertTrue(canvas.isBlank());
        canvas.undo();assertFalse(canvas.isBlank());assertEquals(1,canvas.getPictureCount());
    }
}
