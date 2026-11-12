package history.data_access;

import history.domain.HistoryEntry;
import history.use_case.HistoryRepository;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/** Local, dependency-free, versioned persistence: one atomic .properties file per unique item. */
public final class FileHistoryRepository implements HistoryRepository {
    private static final long MAX_FILE_BYTES = 18L * 1024 * 1024;
    private final Path directory;

    public FileHistoryRepository(Path directory) { this.directory = Objects.requireNonNull(directory); }
    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), ".mathlab", "history");
    }
    public Path directory() { return directory; }

    @Override public synchronized boolean saveIfAbsent(HistoryEntry entry) throws IOException {
        Objects.requireNonNull(entry);
        Files.createDirectories(directory);
        Path target = directory.resolve(entry.contentKey() + ".properties");
        if (Files.exists(target)) return false;
        Path temp = Files.createTempFile(directory, "history-", ".tmp");
        boolean moved = false;
        try {
            Properties fields = new Properties();
            fields.setProperty("version", "1");
            fields.setProperty("id", entry.id());
            fields.setProperty("kind", entry.kind().name());
            fields.setProperty("title", entry.title());
            fields.setProperty("formula", entry.formula());
            fields.setProperty("result", entry.result());
            fields.setProperty("createdAt", entry.createdAt().toString());
            fields.setProperty("width", Integer.toString(entry.imageWidth()));
            fields.setProperty("height", Integer.toString(entry.imageHeight()));
            if (entry.png() != null) fields.setProperty("png", Base64.getEncoder().encodeToString(entry.png()));
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                fields.store(writer, "Math Lab History v1");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target);
            }
            moved = true;
            return true;
        } finally {
            if (!moved) Files.deleteIfExists(temp);
        }
    }

    @Override public synchronized List<HistoryEntry> findAll() throws IOException {
        if (!Files.exists(directory)) return List.of();
        List<HistoryEntry> records = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().matches("[0-9a-f]{64}\\.properties")).toList()) {
                try {
                    if (Files.size(path) > MAX_FILE_BYTES) continue;
                    HistoryEntry record = read(path);
                    // Reject damaged or edited content whose filename no longer matches its fingerprint.
                    if (record.contentKey().concat(".properties").equals(path.getFileName().toString()))
                        records.add(record);
                } catch (RuntimeException | IOException malformed) {
                    // One corrupt item must not prevent access to the remaining history.
                    System.err.println("Math Lab: skipped invalid history record " + path.getFileName());
                }
            }
        }
        return List.copyOf(records);
    }

    @Override public synchronized boolean deleteById(String id) throws IOException {
        if (id == null) return false;
        for (HistoryEntry entry : findAll()) {
            if (entry.id().equals(id)) return Files.deleteIfExists(directory.resolve(entry.contentKey() + ".properties"));
        }
        return false;
    }

    private static HistoryEntry read(Path file) throws IOException {
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { data.load(reader); }
        if (!"1".equals(data.getProperty("version"))) throw new IllegalArgumentException("Unknown version");
        String image = data.getProperty("png");
        return new HistoryEntry(required(data, "id"),
                HistoryEntry.Kind.valueOf(required(data, "kind")),
                required(data, "title"), required(data, "formula"), required(data, "result"),
                image == null ? null : Base64.getDecoder().decode(image),
                Integer.parseInt(required(data, "width")), Integer.parseInt(required(data, "height")),
                Instant.parse(required(data, "createdAt")));
    }
    private static String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing history field: " + key);
        return value;
    }
}
