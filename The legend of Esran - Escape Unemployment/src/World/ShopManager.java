package World;

import java.awt.Point;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encapsulates the logic for assigning and maintaining a single shop doorway in the dungeon.
 * <p>
 * The manager keeps track of which room owns the shop entrance and which direction the door faces.
 * Callers provide lightweight callbacks so the manager can integrate with {@link DungeonRooms}
 * without duplicating rendering or grid logic.
 */
final class ShopManager {

    private Point shopRoom;
    private DungeonRooms.Dir shopDoorFacing;
    private boolean initialized;

    ShopManager() {
        reset();
    }

    void reset() {
        shopRoom = null;
        shopDoorFacing = null;
        initialized = false;
    }

    void restore(Point room, DungeonRooms.Dir facing, boolean wasInitialized) {
        this.shopRoom = room == null ? null : new Point(room);
        this.shopDoorFacing = facing;
        this.initialized = wasInitialized && shopRoom != null && facing != null;
    }

    Point location() {
        return shopRoom == null ? null : new Point(shopRoom);
    }

    DungeonRooms.Dir doorFacing() {
        return shopDoorFacing;
    }

    boolean initialized() {
        return initialized;
    }

    /**
     * Guarantees that a shop doorway exists for the supplied room. When the shop has already been initialised the
     * manager simply replays the door setup for the persisted room state.
     */
    DungeonRooms.Dir ensureDoorway(DungeonRooms.Room candidate,
                                   Point location,
                                   Function<Point, Boolean> hasRoomAt,
                                   BiConsumer<DungeonRooms.Room, DungeonRooms.Dir> doorInstaller,
                                   Consumer<DungeonRooms.Room> dirtyMarker) {
        if (candidate == null || location == null) {
            return shopDoorFacing;
        }

        DungeonRooms.Room target = candidate;
        if (initialized) {
            if (shopRoom == null) {
                shopRoom = new Point(location);
            }
            if (target.shopDoor == null && shopDoorFacing != null) {
                installDoor(target, shopDoorFacing, doorInstaller, dirtyMarker);
            }
            return shopDoorFacing;
        }

        if (target.shopDoor != null) {
            shopDoorFacing = target.shopDoor;
            shopRoom = new Point(location);
            installDoor(target, shopDoorFacing, doorInstaller, dirtyMarker);
            initialized = true;
            return shopDoorFacing;
        }

        DungeonRooms.Dir door = selectShopDoor(target, location, hasRoomAt);
        installDoor(target, door, doorInstaller, dirtyMarker);
        shopDoorFacing = door;
        shopRoom = new Point(location);
        initialized = true;
        return door;
    }

    private void installDoor(DungeonRooms.Room target,
                             DungeonRooms.Dir door,
                             BiConsumer<DungeonRooms.Room, DungeonRooms.Dir> doorInstaller,
                             Consumer<DungeonRooms.Room> dirtyMarker) {
        if (target == null || door == null) {
            return;
        }
        target.shopDoor = door;
        if (target.doors == null) {
            target.doors = EnumSet.noneOf(DungeonRooms.Dir.class);
        }
        target.doors.add(door);
        if (target.lockedDoors != null) {
            target.lockedDoors.remove(door);
        }
        if (doorInstaller != null) {
            doorInstaller.accept(target, door);
        }
        if (dirtyMarker != null) {
            dirtyMarker.accept(target);
        }
    }

    private DungeonRooms.Dir selectShopDoor(DungeonRooms.Room target,
                                            Point location,
                                            Function<Point, Boolean> hasRoomAt) {
        EnumSet<DungeonRooms.Dir> pool = EnumSet.allOf(DungeonRooms.Dir.class);
        if (target.doors != null) {
            pool.removeAll(target.doors);
        }
        for (DungeonRooms.Dir dir : pool) {
            if (!Boolean.TRUE.equals(apply(hasRoomAt, step(location, dir)))) {
                return dir;
            }
        }
        for (DungeonRooms.Dir dir : DungeonRooms.Dir.values()) {
            if (!Boolean.TRUE.equals(apply(hasRoomAt, step(location, dir)))) {
                return dir;
            }
        }
        return DungeonRooms.Dir.N;
    }

    private static Boolean apply(Function<Point, Boolean> hasRoomAt, Point point) {
        return hasRoomAt == null ? Boolean.FALSE : hasRoomAt.apply(point);
    }

    private static Point step(Point origin, DungeonRooms.Dir dir) {
        if (origin == null || dir == null) {
            return null;
        }
        return switch (dir) {
            case N -> new Point(origin.x, origin.y - 1);
            case S -> new Point(origin.x, origin.y + 1);
            case W -> new Point(origin.x - 1, origin.y);
            case E -> new Point(origin.x + 1, origin.y);
        };
    }
}
