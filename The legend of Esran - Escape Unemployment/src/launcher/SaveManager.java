package launcher;

import World.DungeonRoomsSnapshot;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Persists and restores dungeon room game states.
 */
public final class SaveManager {
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path saveFile;

    public SaveManager(Path storageDir) {
        this.saveFile = storageDir.resolve("savegame.dat");
    }

    public synchronized void save(DungeonRoomsSnapshot snapshot) throws IOException {
        Files.createDirectories(saveFile.getParent());
        Path tmp = Files.createTempFile(saveFile.getParent(), "save", ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp))) {
            out.writeObject(snapshot);
        }
        Path backup = backupPath();
        if (Files.isRegularFile(saveFile)) {
            try {
                Files.move(saveFile, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Ignore backup failures – we'll still replace the primary save atomically when possible.
            }
        }
        try {
            Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // Retry without atomic move when not supported on this filesystem.
            Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized Optional<DungeonRoomsSnapshot> load() {
        if (!Files.isRegularFile(saveFile)) {
            return Optional.empty();
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(saveFile))) {
            Object obj = in.readObject();
            if (obj instanceof DungeonRoomsSnapshot snapshot) {
                return Optional.of(snapshot);
            }
            System.err.println("[SaveManager] Unexpected object in save file – resetting.");
        } catch (InvalidClassException ex) {
            System.err.println("[SaveManager] Save file is from an incompatible version: " + ex.getMessage());
        } catch (IOException | ClassNotFoundException ex) {
            System.err.println("[SaveManager] Failed to read save file: " + ex.getMessage());
        }
        quarantineCorruptSave();
        return Optional.empty();
    }

    public synchronized boolean hasSave() {
        return Files.isRegularFile(saveFile);
    }

    public Path saveFile() {
        return saveFile;
    }

    private Path backupPath() {
        String fileName = "savegame-" + BACKUP_FORMAT.format(LocalDateTime.now()) + ".bak";
        return saveFile.resolveSibling(fileName);
    }

    private void quarantineCorruptSave() {
        try {
            if (!Files.isRegularFile(saveFile)) {
                return;
            }
            Path quarantine = saveFile.resolveSibling(saveFile.getFileName() + ".corrupt");
            Files.move(saveFile, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // If we cannot quarantine the corrupt file, leave it in place so the user can inspect it manually.
        }
    }
}
