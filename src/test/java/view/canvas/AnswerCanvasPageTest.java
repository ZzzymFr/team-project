package view.canvas;

import data_access.InMemoryFunctionDataAccessObject;
import view.answer.CanvasAnswer;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import org.junit.jupiter.api.Test;
import use_case.evaluate.EvaluateInteractor;
import view.DrawingCanvas;
import view.ribbon.Ribbon;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static view.canvas.CanvasDriver.check;
import static view.canvas.CanvasDriver.onEdt;
import static view.canvas.CanvasDriver.waitFor;
import static view.canvas.CanvasDriver.waitForReading;

// circle with the lasso -> the Math tab shows the formula -> Calculate -> answer on the canvas
class AnswerCanvasPageTest {
    private static final int NOTHING_HAPPENS_MILLIS = 300;

    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);
    private final MathLabViewModel viewModel = new MathLabViewModel();
    private final CanvasDriver driver = new CanvasDriver(canvas);
    private final Ribbon ribbon = new Ribbon("Math Lab");
    private Ribbon.Tab edit;
    private Ribbon.Tab math;
    private AnswerCanvasPage page;

    private void openPage() throws Exception {
        EvaluateController controller = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel)));
        onEdt(() -> {
            page = new AnswerCanvasPage(shared, controller, viewModel);
            canvas.setSize(900, 560);
            shared.attachTo(page);
            page.setSize(900, 560);
            edit = ribbon.addTab("Edit", () -> true);
            math = page.addMathTab(ribbon);
        });
    }

    // ink box (100,100)-(200,150)
    private void write() throws Exception {
        driver.write(100, 100, 200, 150);
    }

    private void circle() throws Exception {
        driver.lasso(80, 80, 220, 170);
    }

    private void calculate() throws Exception {
        onEdt(() -> page.getCalculateButton().doClick());
    }

    private List<CanvasAnswer> answers() throws Exception {
        AtomicReference<List<CanvasAnswer>> seen = new AtomicReference<>();
        onEdt(() -> seen.set(shared.getAnswers()));
        return seen.get();
    }

    private boolean answerIs(String text) {
        List<CanvasAnswer> all = canvas.getAnswers();
        return all.size() == 1 && text.equals(all.get(0).getText());
    }

    private boolean errorShown(String text) {
        return page.isMessageError() && page.currentMessage() != null && page.currentMessage().contains(text);
    }

    private void escape() throws Exception {
        onEdt(() -> canvas.getActionMap().get("clearSelection")
                .actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, "")));
    }

    @Test
    void writingAloneReadsNothing() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        write();
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertEquals(0, calls.get(), "no reading after a pause any more");
        assertTrue(answers().isEmpty());
    }

    @Test
    void theMathTabIsGreyedOutUntilAFormulaIsCircled() throws Exception {
        shared.setFormulaRecognizer(image -> "5\\times7");
        openPage();
        write();
        onEdt(() -> {
            ribbon.refresh();
            assertFalse(math.isEnabled());
            math.click();
            assertSame(edit, ribbon.getSelectedTab(), "a greyed-out tab cannot be chosen");
        });

        circle();
        waitForReading(page, "5\\times7");
        onEdt(() -> {
            ribbon.refresh();
            assertTrue(math.isEnabled());
            assertSame(edit, ribbon.getSelectedTab(), "circling does not switch tabs by itself");
            math.click();
            assertSame(math, ribbon.getSelectedTab());
            assertEquals("5\\times7", page.getPreview().getLatex());
            assertTrue(page.getPreview().isTypeset());
            assertTrue(page.getCalculateButton().isEnabled());
        });
        assertTrue(answers().isEmpty(), "nothing until Calculate");

        escape();
        onEdt(() -> {
            ribbon.refresh();
            assertFalse(math.isEnabled());
            assertSame(edit, ribbon.getSelectedTab(), "back to Edit once nothing is circled");
        });
    }

    @Test
    void calculateWritesTheAnswerAndEndsTheSelection() throws Exception {
        shared.setFormulaRecognizer(image -> "\\frac{1}{2}+2^{3}");
        openPage();
        write();
        circle();
        waitForReading(page, "\\frac{1}{2}+2^{3}");
        calculate();

        waitFor(() -> answerIs("= 8.5"), "the answer");
        CanvasAnswer a = answers().get(0);
        assertFalse(a.isError());
        assertTrue(a.getX() > 200, "right of the ink");
        assertFalse(check(canvas::hasSelection));
        waitFor(() -> page.currentMessage() == null, "the progress message to go away");
    }

    @Test
    void calculatePressedWhileReadingWaitsForTheReading() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        shared.setFormulaRecognizer(image -> {
            release.await(5, TimeUnit.SECONDS);
            return "1+1=";
        });
        openPage();
        write();
        circle();
        calculate();
        assertTrue(check(() -> page.currentMessage() != null && page.currentMessage().startsWith("Reading")));
        release.countDown();
        waitFor(() -> answerIs("2"), "the answer once read");
    }

    @Test
    void trailingEqualsAnswerGoesRight() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1=");
        openPage();
        write();
        circle();
        waitForReading(page, "1+1=");
        calculate();

        waitFor(() -> answerIs("2"), "the answer");
        assertTrue(answers().get(0).getX() > 200);
    }

    @Test
    void equationIsSolvedBelow() throws Exception {
        shared.setFormulaRecognizer(image -> "2x+1=7");
        openPage();
        write();
        circle();
        waitForReading(page, "2x+1=7");
        calculate();

        waitFor(() -> answerIs("x = 3"), "the solution");
        assertTrue(answers().get(0).getY() > 150, "below the ink");
    }

    @Test
    void editingAnAnsweredFormulaRemovesTheAnswerAndReadsNothing() throws Exception {
        AtomicReference<String> latex = new AtomicReference<>("1+1=");
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return latex.get();
        });
        openPage();
        write();
        circle();
        waitForReading(page, "1+1=");
        calculate();
        waitFor(() -> answerIs("2"), "the first answer");

        latex.set("1+2=");
        driver.write(205, 100, 205, 150);  // a stroke right on the formula
        assertTrue(answers().isEmpty(), "old answer gone at once");
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertEquals(1, calls.get(), "not read again by itself");

        driver.lasso(80, 80, 230, 170);
        waitForReading(page, "1+2=");
        calculate();
        waitFor(() -> answerIs("3"), "the new answer");
    }

    @Test
    void endingTheSelectionWhileItIsBeingReadDropsTheReading() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        shared.setFormulaRecognizer(image -> {
            reading.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "1+1";
        });
        openPage();
        write();
        circle();
        assertTrue(reading.await(5, TimeUnit.SECONDS));
        escape();
        release.countDown();
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertNull(page.getSelectedLatex());
    }

    @Test
    void recognitionProblemShowsTheReasonAndGreysOutCalculate() throws Exception {
        openPage();  // recognition not connected
        write();
        circle();
        waitFor(() -> page.getPreview().isProblem() && page.getPreview().getText().contains("not connected"),
                "the reason");
        onEdt(() -> {
            ribbon.refresh();
            assertTrue(math.isEnabled(), "the tab still shows why");
            assertFalse(page.getCalculateButton().isEnabled());
        });
        assertTrue(answers().isEmpty());
    }

    @Test
    void anErrorFromTheModelShowsAsTheReason() throws Exception {
        shared.setFormulaRecognizer(image -> {
            throw new UnsatisfiedLinkError("no native lib");
        });
        openPage();
        write();
        circle();
        waitFor(() -> page.getPreview().isProblem() && "no native lib".equals(page.getPreview().getText()),
                "the reason");
    }

    @Test
    void uncalculableOnlyShowsTheReason() throws Exception {
        shared.setFormulaRecognizer(image -> "\\int");
        openPage();
        write();
        circle();
        waitForReading(page, "\\int");
        calculate();

        waitFor(() -> errorShown("Uncalculable — Integrals not supported"), "the reason");
        assertTrue(answers().isEmpty(), "no red ?");
    }

    @Test
    void lineBreakGivesAWarningAndNoAnswer() throws Exception {
        shared.setFormulaRecognizer(image -> "x+1=3\\\\x=2");
        openPage();
        write();
        circle();
        waitForReading(page, "x+1=3\\\\x=2");
        calculate();
        waitFor(() -> errorShown("Uncalculable — Not a calculation"), "the warning");
        assertTrue(answers().isEmpty());
    }

    @Test
    void functionDefinitionOnlyShowsAMessage() throws Exception {
        shared.setFormulaRecognizer(image -> "f(x)=x^{2}");
        openPage();
        write();
        circle();
        waitForReading(page, "f(x)=x^{2}");
        calculate();

        waitFor(() -> page.currentMessage() != null && page.currentMessage().startsWith("Defined"), "the message");
        assertFalse(page.isMessageError());
        assertTrue(answers().isEmpty());
    }

    @Test
    void justCirclingAFormulaKeepsItsAnswer() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1");
        openPage();
        write();
        circle();
        waitForReading(page, "1+1");
        calculate();
        waitFor(() -> !canvas.getAnswers().isEmpty(), "the answer");

        circle();
        waitForReading(page, "1+1");
        assertEquals(1, canvas.getAnswers().size(), "only Calculate replaces it");
        assertEquals(1, canvas.getFormulas().size());
    }

    @Test
    void circlingTwoFormulasReadsOnlyTheCircledOne() throws Exception {
        AtomicReference<BufferedImage> seen = new AtomicReference<>();
        shared.setFormulaRecognizer(image -> {
            seen.set(image);
            return "1+1";
        });
        openPage();
        write();                                     // A: 50 tall
        driver.write(100, 380, 300, 420);            // B, far below
        circle();                                    // around A
        waitForReading(page, "1+1");
        assertEquals(51 + 2 * 18, seen.get().getHeight(), "the image holds only A");
        calculate();
        waitFor(() -> !canvas.getAnswers().isEmpty(), "the answer");
        assertTrue(canvas.getAnswers().get(0).getBox().getCenterY() < 300, "beside A, not B");
    }

    @Test
    void anotherPageInFrontNeverReads() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        write();
        onEdt(() -> shared.attachTo(new AnswerCanvasPage(shared)));
        circle();
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertEquals(0, calls.get());
        assertFalse(check(page::hasFormulaSelected));
    }

    @Test
    void switchingPageForgetsTheReading() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1");
        openPage();
        write();
        circle();
        waitForReading(page, "1+1");
        onEdt(() -> shared.attachTo(new AnswerCanvasPage(shared)));
        assertNull(page.getSelectedLatex());
        SwingUtilities.invokeAndWait(() -> assertFalse(page.getPreview().isTypeset()));
    }

    @Test
    void aLassoAroundNothingLeavesTheMathTabGreyedOut() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        write();
        driver.lasso(500, 300, 600, 400);
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertEquals(0, calls.get());
        onEdt(() -> {
            ribbon.refresh();
            assertFalse(math.isEnabled());
        });
    }

    @Test
    void movingTheSelectionKeepsTheReading() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        write();
        circle();
        waitForReading(page, "1+1");
        driver.drag(150, 125, 450, 125);  // drag the selection away
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertEquals(1, calls.get(), "not read again");
        assertEquals("1+1", page.getSelectedLatex());
        assertTrue(check(page::hasFormulaSelected), "still circled, to keep moving it");
    }
}
