package launcher;

import World.DungeonRoomsSnapshot;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists and restores dungeon room game states.
 */
public final class SaveManager {
    private final Path saveFile;

    public SaveManager(Path storageDir) {
        this.saveFile = storageDir.resolve("savegame.dat");
    }

    public void save(DungeonRoomsSnapshot snapshot) throws IOException {
        Files.createDirectories(saveFile.getParent());
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(saveFile))) {
            out.writeObject(snapshot);
        }
    }

    public Optional<DungeonRoomsSnapshot> load() {
        if (!Files.isRegularFile(saveFile)) {
            return Optional.empty();
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(saveFile))) {
            Object obj = in.readObject();
            if (obj instanceof DungeonRoomsSnapshot snapshot) {
                return Optional.of(snapshot);
            }
        } catch (IOException | ClassNotFoundException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public boolean hasSave() {
        return Files.isRegularFile(saveFile);
    }

    public Path saveFile() {
        return saveFile;
    }
}
