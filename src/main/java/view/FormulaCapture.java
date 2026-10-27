package view;

import java.awt.image.BufferedImage;

// one formula's picture, tagged so a late result can be spotted
public final class FormulaCapture {
    private final long id;
    private final long revision;
    private final BufferedImage image;

    public FormulaCapture(long id, long revision, BufferedImage image) {
        this.id = id;
        this.revision = revision;
        this.image = image;
    }

    public long getId() {
        return id;
    }

    public long getRevision() {
        return revision;
    }

    public BufferedImage getImage() {
        return image;
    }
}
