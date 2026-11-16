package com.vanipy.menit;

/** One usage event, reduced to what the analysis needs. Times are epoch millis. */
public final class Event {
    public static final int RESUMED = 1;            // UsageEvents.Event.ACTIVITY_RESUMED
    public static final int PAUSED = 2;             // ACTIVITY_PAUSED
    public static final int SCREEN_ON = 15;         // SCREEN_INTERACTIVE
    public static final int SCREEN_OFF = 16;        // SCREEN_NON_INTERACTIVE
    public static final int STOPPED = 23;           // ACTIVITY_STOPPED
    public static final int SHUTDOWN = 26;          // DEVICE_SHUTDOWN

    public final String pkg;
    public final int type;
    public final long time;

    public Event(String pkg, int type, long time) {
        this.pkg = pkg;
        this.type = type;
        this.time = time;
    }
}
