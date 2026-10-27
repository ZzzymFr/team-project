package data_access;

import entity.GraphData;
import use_case.graph_function.GraphRenderException;
import use_case.graph_function.GraphRendererGateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Renders a graph with the QuickChart API (https://quickchart.io/documentation/):
 * the points are sent as a Chart.js line chart and QuickChart returns a PNG.
 */
public class QuickChartGraphRenderer implements GraphRendererGateway {
    public static final String DEFAULT_ENDPOINT = "https://quickchart.io/chart";

    private final HttpClient httpClient;
    private final String endpoint;
    private final int width;
    private final int height;

    public QuickChartGraphRenderer() {
        this(DEFAULT_ENDPOINT, 800, 500);
    }

    public QuickChartGraphRenderer(String endpoint, int width, int height) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.endpoint = endpoint;
        this.width = width;
        this.height = height;
    }

    @Override
    public byte[] render(GraphData graphData) throws GraphRenderException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(graphData), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new GraphRenderException("cannot reach QuickChart (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GraphRenderException("the request to QuickChart was interrupted", e);
        }
        if (response.statusCode() != 200) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw new GraphRenderException("QuickChart returned HTTP " + response.statusCode()
                    + (body.isEmpty() || body.length() > 300 ? "" : ": " + body));
        }
        return response.body();
    }

    /** Builds the QuickChart POST body: image options plus a Chart.js v4 config. */
    String buildRequestBody(GraphData data) {
        StringBuilder json = new StringBuilder();
        json.append("{\"version\":\"4\",\"format\":\"png\",\"backgroundColor\":\"white\"")
                .append(",\"width\":").append(width)
                .append(",\"height\":").append(height)
                .append(",\"chart\":{\"type\":\"line\",\"data\":{\"datasets\":[");

        appendDataset(json, data.getXs(), data.getYs(), "#2563eb", 2);
        // Draw the x and y axes as thin grey lines when they are inside the window.
        if (data.getYMin() <= 0 && data.getYMax() >= 0) {
            json.append(',');
            appendDataset(json, new double[]{data.getXMin(), data.getXMax()}, new double[]{0, 0}, "#6b7280", 1);
        }
        if (data.getXMin() <= 0 && data.getXMax() >= 0) {
            json.append(',');
            appendDataset(json, new double[]{0, 0}, new double[]{data.getYMin(), data.getYMax()}, "#6b7280", 1);
        }

        json.append("]},\"options\":{\"animation\":false")
                .append(",\"plugins\":{\"legend\":{\"display\":false}")
                .append(",\"title\":{\"display\":true,\"font\":{\"size\":16},\"text\":")
                .append(quote(data.getTitle())).append("}}")
                .append(",\"scales\":{")
                .append("\"x\":{\"type\":\"linear\",\"min\":").append(number(data.getXMin()))
                .append(",\"max\":").append(number(data.getXMax()))
                .append(",\"title\":{\"display\":true,\"text\":\"x\"}}")
                .append(",\"y\":{\"type\":\"linear\",\"min\":").append(number(data.getYMin()))
                .append(",\"max\":").append(number(data.getYMax()))
                .append(",\"title\":{\"display\":true,\"text\":\"y\"}}")
                .append("}}}}");
        return json.toString();
    }

    private static void appendDataset(StringBuilder json, double[] xs, double[] ys, String color, int lineWidth) {
        json.append("{\"borderColor\":\"").append(color).append('"')
                .append(",\"borderWidth\":").append(lineWidth)
                .append(",\"pointRadius\":0,\"fill\":false,\"tension\":0,\"spanGaps\":false,\"data\":[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"x\":").append(number(xs[i]))
                    .append(",\"y\":").append(Double.isNaN(ys[i]) ? "null" : number(ys[i]))
                    .append('}');
        }
        json.append("]}");
    }

    private static String number(double value) {
        String s = String.format(Locale.ROOT, "%.6g", value);
        // Trim trailing zeros of the mantissa ("1.50000" -> "1.5", "2.00000e+10" -> "2e+10").
        int exp = s.indexOf('e');
        String mantissa = exp >= 0 ? s.substring(0, exp) : s;
        String exponent = exp >= 0 ? s.substring(exp) : "";
        if (mantissa.contains(".")) {
            mantissa = mantissa.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return mantissa + exponent;
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
