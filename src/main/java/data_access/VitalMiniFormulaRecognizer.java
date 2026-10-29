package data_access;

import interface_adapter.recognize.FormulaRecognizer;
import model.vital_mini;

import java.awt.image.BufferedImage;

// handwriting -> latex w/ trained model
public class VitalMiniFormulaRecognizer implements FormulaRecognizer {
    private final vital_mini model;

    // model is caller's, we dont close it
    public VitalMiniFormulaRecognizer(vital_mini model) {
        this.model = model;
    }

    // the model only takes 128 px high input, width a multiple of 16, so resize like training did
    @Override
    public String recognize(BufferedImage formulaImage) throws Exception {
        return model.generate(FormulaImagePreprocessor.prepare(formulaImage));
    }
}
