package com.vanipy.menit.diag;

/**
 * Trailing-edge throttle. A burst of events is read at most once per gap, and an event that arrives inside the gap
 * schedules one read at the end of it, so the last state in a burst is never dropped.
 */
final class Throttle {
    enum Act { READ_NOW, SCHEDULE, NONE }
    private final long gap; private long last = Long.MIN_VALUE / 2; private boolean pending; private long due;
    Throttle(long gapMs) { gap = gapMs; }
    Act onEvent(long now) {
        if (now - last >= gap) { last = now; pending = false; return Act.READ_NOW; }
        if (pending) return Act.NONE;
        pending = true; due = last + gap; return Act.SCHEDULE;
    }
    long dueAt() { return due; }
    /** Called when the scheduled timer fires. True if a read should happen now. */
    boolean onTimer(long now) { if (!pending) return false; pending = false; last = now; return true; }
}
