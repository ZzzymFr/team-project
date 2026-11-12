package pdf_editor.use_case;

import java.awt.image.BufferedImage;

/** Outbound recognition port; US4 does not depend on a concrete US1 model. */
@FunctionalInterface
public interface SelectedInkRecognitionGateway {
    String recognize(BufferedImage selection) throws Exception;
}
