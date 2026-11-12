package com.leapmotion.leap;

/**
 * Callback interface for {@link Controller} events (same method names as the V2 SDK).
 * Callbacks run on the controller's background thread.
 *
 * @author Tiago Martins
 */
public class Listener {

    public Listener() {
    }

    public void onInit(Controller controller) { }
    public void onConnect(Controller controller) { }
    public void onDisconnect(Controller controller) { }
    public void onExit(Controller controller) { }
    public void onFrame(Controller controller) { }
    public void onFocusGained(Controller controller) { }
    public void onFocusLost(Controller controller) { }
    public void onServiceConnect(Controller controller) { }
    public void onServiceDisconnect(Controller controller) { }
    public void onDeviceChange(Controller controller) { }
    public void onImages(Controller controller) { }
    public void onServiceChange(Controller controller) { }
    public void onDeviceFailure(Controller controller) { }
    public void onLogMessage(Controller controller, int severity, long timestamp, String message) { }

    /** No-op kept for API compatibility with the SWIG generated class. */
    public void delete() { }
}
