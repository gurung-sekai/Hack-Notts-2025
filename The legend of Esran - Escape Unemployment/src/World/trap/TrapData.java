package World.trap;

public interface TrapData {
    String id();
    int logicWidth();
    int logicHeight();
    int visualWidth();
    int visualHeight();
    Offset visualOffset();
    boolean pixelAccurate();
    boolean[][] alphaMask();

    record Offset(int x, int y) { }
}
