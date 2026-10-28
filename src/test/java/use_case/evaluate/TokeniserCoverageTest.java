package use_case.evaluate;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.UserFunction;
import entity.node.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TokeniserCoverageTest {
    private static final String FOLDER = "src/neutral network/";
    private static final Pattern KEY = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\\d+");
    private static final Pattern LATEX = Pattern.compile("\"latex\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void meaninglessFormulasAreUncalculable() {
        for (String s : new String[] {"\\text{Let }A", "\\square", "\\begin{pmatrix}1\\end{pmatrix}", "1;2",
                "f:\\mathbb{R}\\rightarrow\\mathbb{R}", "g:\\mathbb{N}\\rightarrow\\mathbb{R}", "v=(x_1,...x_n)",
                "\\mathbb{R}", "1\\oplus2", "\\sum_{i=1}^{25}ix_i=x_1+2x_2+...+24x_{24}+25x_{25}"}) {
            assertEquals("Uncalculable", Calc.once(s), s);
        }
    }

    @Test
    void everyVocabularyTokenIsHandled() throws IOException {
        List<String> vocabulary = vocabulary();
        assertTrue(vocabulary.size() > 150);
        for (String t : vocabulary) {
            for (String s : new String[] {t, "1" + t + "2", t + "{1}"}) {
                String answer = safeAnswer(s);
                assertFalse(answer.contains("is not supported"), s + " gave " + answer);
            }
        }
    }

    @Test
    void everyPrefixOfEveryTrainingLabelIsSafe() throws IOException {
        List<String> labels = labels(FOLDER + "train/labels.jsonl");
        labels.addAll(labels(FOLDER + "val/labels.jsonl"));
        assertTrue(labels.size() > 50);
        for (String label : labels) {
            for (int end = 1; end <= label.length(); end++) {
                safeAnswer(label.substring(0, end));
            }
        }
    }

    @Test
    void trainingLabelsGiveExpectedAnswers() {
        assertEquals("= 2", Calc.once("1+1="));
        assertEquals("true", Calc.once("4\\times3=12"));
        assertEquals("true", Calc.once("\\frac{6}{5}=1.2"));
        assertEquals("true", Calc.once("2^{2\\times3}=64"));
        assertEquals("true", Calc.once("\\frac{5\\times6\\times7\\times8}{2}=840"));
        assertEquals("x = 2", Calc.once("8x+4=20"));
        assertEquals("true", Calc.once("\\lim_{x\\rightarrow\\infty}8x+3=\\infty"));
        assertEquals("Uncalculable", Calc.once("\\forall x\\in\\mathbb{R}^{n}"));
        assertEquals("Uncalculable", Calc.once("f'(x)=\\cos(e^{x})"));
    }

    @Test
    void integralsAreUncalculable() {
        assertEquals("Uncalculable", Calc.once("\\int"));
        assertEquals("Uncalculable", Calc.once("\\int_a^x g=\\lim_{n\\rightarrow\\infty}\\int_a^x f_n'"));
        assertEquals("Uncalculable", Calc.once("\\int_{0}^{1}x"));
    }

    // go thru interactor + parser directly so catch cant hide crashes
    private static String safeAnswer(String s) {
        String answer = assertTimeout(Duration.ofSeconds(2), () -> Calc.once(s), s);
        assertFalse(answer.isEmpty(), s);
        try {
            List<String> tokens = MathLexer.tokenize(s);
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("=")) {
                tokens.remove(tokens.size() - 1);
            }
            Map<String, UserFunction> functions = new HashMap<>();
            Node node = new MathParser(tokens, functions).parse();
            Set<String> free = new HashSet<>();
            node.collectFreeVariables(free);
            if (free.isEmpty()) {
                node.evaluate(new Context(functions));
            }
        } catch (MathSyntaxException e) {
            // error msg or uncalc is fine
        } catch (RuntimeException | StackOverflowError e) {
            fail(s + " threw " + e);
        }
        return answer;
    }

    private static List<String> vocabulary() throws IOException {
        String text = new String(Files.readAllBytes(Paths.get(FOLDER + "tokeniser.json")), StandardCharsets.UTF_8);
        // every key after "vocab" is a token
        List<String> tokens = new ArrayList<>();
        Matcher matcher = KEY.matcher(text.substring(text.indexOf("\"vocab\"") + 7));
        while (matcher.find()) {
            String token = unescape(matcher.group(1));
            if (!token.equals("<pad>") && !token.equals("<bos>") && !token.equals("<eos>")
                    && !token.equals("<unk>")) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static List<String> labels(String path) throws IOException {
        List<String> labels = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            Matcher matcher = LATEX.matcher(line);
            if (matcher.find()) {
                labels.add(unescape(matcher.group(1)));
            }
        }
        return labels;
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i++;
                result.append(text.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
