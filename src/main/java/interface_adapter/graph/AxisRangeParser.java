package interface_adapter.graph;

/**
 * Reads an axis range typed by the user: two numbers, smaller first, such as {@code -5, 5},
 * {@code -5 5}, {@code [-5, 5]}, {@code (-5; 5)}, {@code -5 to 5} or {@code -5 ~ 5}.
 * A blank box (or {@code auto}) means "use the default".
 */
public final class AxisRangeParser {
    private AxisRangeParser() {
    }

    /**
     * @param text what the user typed
     * @param axis the axis name for error messages, e.g. "x"
     * @return {min, max}, or null if the box is blank or says "auto"
     * @throws IllegalArgumentException with a message for the user if it is not a valid range
     */
    public static double[] parse(String text, String axis) {
        String cleaned = text == null ? "" : text.trim()
                .replace('−', '-')                       // typographic minus
                .replace('–', '-');
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("auto")) {
            return null;
        }
        if (cleaned.length() >= 2 && "[(".indexOf(cleaned.charAt(0)) >= 0
                && "])".indexOf(cleaned.charAt(cleaned.length() - 1)) >= 0) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.replaceAll("(?i)\\s+to\\s+", ",").replace('~', ',').replace(';', ',');
        String[] parts = cleaned.contains(",") ? cleaned.split(",") : cleaned.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException(usage(axis));
        }
        double min;
        double max;
        try {
            min = Double.parseDouble(parts[0].trim());
            max = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(usage(axis));
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException(usage(axis));
        }
        if (!(min < max)) {
            throw new IllegalArgumentException("The " + axis + " range must go from a smaller number to a larger one, e.g. -5, 5.");
        }
        return new double[]{min, max};
    }

    private static String usage(String axis) {
        return "The " + axis + " range should be two numbers, e.g. -5, 5 (leave it empty for the default).";
    }
}
