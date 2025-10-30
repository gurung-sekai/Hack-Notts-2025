package launcher;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores key bindings for the supported control actions.
 */
public final class ControlsProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final EnumMap<ControlAction, Integer> bindings;

    public ControlsProfile() {
        this(createDefaultBindings());
    }

    public ControlsProfile(ControlsProfile other) {
        this(other.bindings);
    }

    public ControlsProfile(Map<ControlAction, Integer> bindings) {
        this.bindings = new EnumMap<>(ControlAction.class);
        this.bindings.putAll(Objects.requireNonNull(bindings, "bindings"));
    }

    private static Map<ControlAction, Integer> createDefaultBindings() {
        EnumMap<ControlAction, Integer> defaults = new EnumMap<>(ControlAction.class);
        defaults.put(ControlAction.MOVE_UP, java.awt.event.KeyEvent.VK_W);
        defaults.put(ControlAction.MOVE_DOWN, java.awt.event.KeyEvent.VK_S);
        defaults.put(ControlAction.MOVE_LEFT, java.awt.event.KeyEvent.VK_A);
        defaults.put(ControlAction.MOVE_RIGHT, java.awt.event.KeyEvent.VK_D);
        defaults.put(ControlAction.SHOOT, java.awt.event.KeyEvent.VK_SPACE);
        defaults.put(ControlAction.DASH, java.awt.event.KeyEvent.VK_SHIFT);
        defaults.put(ControlAction.PARRY, java.awt.event.KeyEvent.VK_E);
        defaults.put(ControlAction.SPECIAL, java.awt.event.KeyEvent.VK_Q);
        defaults.put(ControlAction.REROLL, java.awt.event.KeyEvent.VK_R);
        defaults.put(ControlAction.PAUSE, java.awt.event.KeyEvent.VK_ESCAPE);
        return defaults;
    }

    public int keyFor(ControlAction action) {
        Integer value = bindings.get(action);
        if (value == null) {
            throw new IllegalArgumentException("No binding present for action " + action);
        }
        return value;
    }

    public void rebind(ControlAction action, int keyCode) {
        bindings.put(Objects.requireNonNull(action, "action"), keyCode);
    }

    public Map<ControlAction, Integer> view() {
        return Collections.unmodifiableMap(bindings);
    }
}
