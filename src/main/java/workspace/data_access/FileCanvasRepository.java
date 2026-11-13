package workspace.data_access;

import workspace.domain.CanvasState;
import workspace.domain.SavedCanvas;
import workspace.use_case.CanvasRepository;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Versioned binary document format, bounded reads, atomic replacement; no Java object deserialization. */
public final class FileCanvasRepository implements CanvasRepository {
    private static final int MAGIC = 0x4d4c4332; // MLC2
    private static final int MAX_STROKES = 100000, MAX_POINTS = 1000000, MAX_ASSETS = 1000;
    private final Path directory;
    public FileCanvasRepository(Path directory) { this.directory = Objects.requireNonNull(directory); }
    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), ".mathlab", "canvases");
    }
    private Path path(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid canvas ID");
        return directory.resolve(id + ".mlcanvas");
    }
    @Override public List<SavedCanvas> list() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<SavedCanvas> out = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory, "*.mlcanvas")) {
            for (Path file : files) {
                try { out.add(read(file)); }
                catch (IOException | IllegalArgumentException malformed) {
                    // A corrupt save must not hide healthy saves or crash startup.
                }
            }
        }
        return out;
    }
    @Override public Optional<SavedCanvas> find(String id) throws IOException {
        Path file = path(id);
        return Files.exists(file) ? Optional.of(read(file)) : Optional.empty();
    }
    @Override public void write(SavedCanvas canvas) throws IOException {
        Files.createDirectories(directory);
        Path target = path(canvas.id());
        Path temporary = Files.createTempFile(directory, "canvas-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(MAGIC);out.writeUTF(canvas.id());out.writeUTF(canvas.name());
                out.writeLong(canvas.modifiedAt().toEpochMilli());
                CanvasState state = canvas.state();
                out.writeDouble(state.zoom());out.writeDouble(state.offsetX());out.writeDouble(state.offsetY());
                out.writeInt(state.ink().size());
                for (var ink : state.ink()) {
                    out.writeInt(ink.argb());out.writeInt(ink.points().size());
                    for (var p : ink.points()) {out.writeInt(p.x());out.writeInt(p.y());out.writeLong(p.timeMillis());}
                }
                out.writeInt(state.formulas().size());
                for (var formula : state.formulas()) {
                    out.writeInt(formula.strokes().size());for (int index : formula.strokes()) out.writeInt(index);
                    out.writeUTF(formula.answer());out.writeBoolean(formula.error());
                    out.writeDouble(formula.x());out.writeDouble(formula.y());
                    out.writeDouble(formula.width());out.writeDouble(formula.height());
                    out.writeFloat(formula.fontSize());out.writeInt(formula.color());
                    out.writeInt(formula.textParts().size());
                    for (var part : formula.textParts()) {out.writeUTF(part.text());out.writeBoolean(part.superscript());}
                }
                out.writeInt(state.assets().size());
                for (var asset : state.assets()) {
                    out.writeUTF(asset.latex());out.writeUTF(asset.result());
                    out.writeDouble(asset.x());out.writeDouble(asset.y());
                    out.writeDouble(asset.width());out.writeDouble(asset.height());
                    out.writeBoolean(asset.graph());
                    byte[] png = asset.png();out.writeInt(png.length);out.write(png);
                }
            }
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IOException("Invalid saved document count");
        return count;
    }
    private static SavedCanvas read(Path file) throws IOException {
        // Refuse oversized files before allocating any arrays.
        if (Files.size(file) > 150L * 1024 * 1024) throw new IOException("Canvas file too large");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int format=in.readInt();
            if (format != MAGIC && format != 0x4d4c4331) throw new IOException("Unsupported canvas file format");
            String id = in.readUTF(), name = in.readUTF();
            Instant modified = Instant.ofEpochMilli(in.readLong());
            double zoom=in.readDouble(), x=in.readDouble(), y=in.readDouble();
            List<CanvasState.Ink> ink = new ArrayList<>();
            int remaining = MAX_POINTS;
            for (int i=count(in,MAX_STROKES);i>0;i--) {
                int rgb=in.readInt(), len=count(in,remaining);
                if (len == 0) throw new IOException("Empty stroke");
                remaining -= len;
                List<CanvasState.Point> points = new ArrayList<>(len);
                for (int j=0;j<len;j++) points.add(new CanvasState.Point(in.readInt(),in.readInt(),in.readLong()));
                ink.add(new CanvasState.Ink(points,rgb));
            }
            List<CanvasState.Formula> formulas = new ArrayList<>();
            for (int i=count(in,MAX_STROKES);i>0;i--) {
                int n=count(in,ink.size());
                List<Integer> ids=new ArrayList<>();
                for (int j=0;j<n;j++) {
                    int index=in.readInt();if (index<0 || index>=ink.size()) throw new IOException("Unknown stroke index");
                    ids.add(index);
                }
                String answer=in.readUTF();boolean error=in.readBoolean();
                double fx=in.readDouble(),fy=in.readDouble(),fw=in.readDouble(),fh=in.readDouble();
                float fs=in.readFloat();int color=in.readInt();
                List<CanvasState.TextPart> parts=new ArrayList<>();
                if (format == MAGIC) {
                    for (int j=count(in,10000);j>0;j--) parts.add(new CanvasState.TextPart(in.readUTF(),in.readBoolean()));
                }
                formulas.add(new CanvasState.Formula(ids,answer,error,fx,fy,fw,fh,fs,color,parts));
            }
            List<CanvasState.Asset> assets = new ArrayList<>();
            for (int i=count(in,MAX_ASSETS);i>0;i--) {
                String latex=in.readUTF(),result=in.readUTF();
                double ax=in.readDouble(),ay=in.readDouble(),aw=in.readDouble(),ah=in.readDouble();
                boolean isGraph=format == MAGIC && in.readBoolean();
                int size=in.readInt();
                if (size<1 || size>12*1024*1024) throw new IOException("Invalid image size");
                byte[] png=new byte[size];in.readFully(png);
                assets.add(new CanvasState.Asset(latex,result,png,ax,ay,aw,ah,isGraph));
            }
            if (in.read() != -1) throw new IOException("Trailing workspace data");
            try { return new SavedCanvas(id,name,modified,new CanvasState(ink,formulas,assets,zoom,x,y)); }
            catch (IllegalArgumentException ex) { throw new IOException("Damaged canvas data",ex); }
        }
    }
}
