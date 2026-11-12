package com.leapmotion.leap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration store. Gesture.* keys tune the Java gesture recogniser; other keys are
 * forwarded to the Ultraleap service with LeapSaveConfigValue when {@link #save()} is called.
 *
 * @author Tiago Martins
 */
public class Config {

    public enum ValueType {
        TYPE_UNKNOWN(0), TYPE_BOOLEAN(1), TYPE_INT32(2), TYPE_FLOAT(6), TYPE_STRING(8);

        private final int value;

        ValueType(int v) { value = v; }

        public int swigValue() { return value; }
    }

    private final Controller controller;
    private final Map<String, Object> values = new HashMap<String, Object>();
    private final Map<String, Object> dirty = new LinkedHashMap<String, Object>();

    Config(Controller controller) {
        this.controller = controller;
        values.put("Gesture.Circle.MinRadius", 5.0f);
        values.put("Gesture.Circle.MinArc", (float) (1.5 * Math.PI));
        values.put("Gesture.Swipe.MinLength", 150.0f);
        values.put("Gesture.Swipe.MinVelocity", 1000.0f);
        values.put("Gesture.KeyTap.MinDownVelocity", 50.0f);
        values.put("Gesture.KeyTap.HistorySeconds", 0.1f);
        values.put("Gesture.KeyTap.MinDistance", 3.0f);
        values.put("Gesture.ScreenTap.MinForwardVelocity", 50.0f);
        values.put("Gesture.ScreenTap.HistorySeconds", 0.1f);
        values.put("Gesture.ScreenTap.MinDistance", 5.0f);
    }

    public ValueType type(String key) {
        Object v = values.get(key);
        if (v instanceof Boolean) return ValueType.TYPE_BOOLEAN;
        if (v instanceof Integer) return ValueType.TYPE_INT32;
        if (v instanceof Float) return ValueType.TYPE_FLOAT;
        if (v instanceof String) return ValueType.TYPE_STRING;
        return ValueType.TYPE_UNKNOWN;
    }

    public boolean getBool(String key) { Object v = values.get(key); return v instanceof Boolean && (Boolean) v; }
    public int getInt32(String key) { Object v = values.get(key); return v instanceof Number ? ((Number) v).intValue() : 0; }
    public float getFloat(String key) { Object v = values.get(key); return v instanceof Number ? ((Number) v).floatValue() : 0f; }
    public String getString(String key) { Object v = values.get(key); return v == null ? "" : v.toString(); }

    public boolean setBool(String key, boolean value) { return set(key, value); }
    public boolean setInt32(String key, int value) { return set(key, value); }
    public boolean setFloat(String key, float value) { return set(key, value); }
    public boolean setString(String key, String value) { return set(key, value); }

    private boolean set(String key, Object value) {
        if (key == null) return false;
        values.put(key, value);
        if (key.startsWith("Gesture.")) {
            if (controller != null && value instanceof Number) controller.recognizer().setParameter(key, ((Number) value).floatValue());
        } else {
            dirty.put(key, value);
        }
        return true;
    }

    /** Applies pending non-gesture values to the tracking service (best effort). */
    public boolean save() {
        if (controller == null) { dirty.clear(); return true; }
        boolean ok = true;
        for (Map.Entry<String, Object> e : dirty.entrySet()) {
            ok &= controller.saveConfigValue(e.getKey(), e.getValue());
        }
        dirty.clear();
        return ok;
    }

    public void delete() { }
}
