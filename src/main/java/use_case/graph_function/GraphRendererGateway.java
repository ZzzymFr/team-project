package use_case.graph_function;

import entity.GraphData;

/**
 * Turns sampled points into an image. Implemented by the QuickChart API client.
 */
public interface GraphRendererGateway {
    /**
     * @return the encoded image (PNG) of the graph
     * @throws GraphRenderException if the image could not be produced
     */
    byte[] render(GraphData graphData) throws GraphRenderException;
}
