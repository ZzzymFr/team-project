package interface_adapter.graph;

import use_case.graph_function.GraphFunctionOutputBoundary;
import use_case.graph_function.GraphFunctionOutputData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Turns the use case result into a displayable image (or error) in the {@link GraphViewModel}.
 */
public class GraphPresenter implements GraphFunctionOutputBoundary {
    private final GraphViewModel viewModel;

    public GraphPresenter(GraphViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(GraphFunctionOutputData outputData) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(outputData.getImageBytes()));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            prepareFailView("The graph service returned an image that could not be read.");
            return;
        }
        GraphState state = new GraphState(viewModel.getState());
        state.setLatex(outputData.getLatex());
        state.setGraphImage(image);
        state.setError(null);
        viewModel.setState(state);
        viewModel.firePropertyChanged();
    }

    @Override
    public void prepareFailView(String errorMessage) {
        GraphState state = new GraphState(viewModel.getState());
        state.setError(errorMessage);
        viewModel.setState(state);
        viewModel.firePropertyChanged();
    }
}
