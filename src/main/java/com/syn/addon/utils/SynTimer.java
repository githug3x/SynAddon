package com.syn.addon.utils;

public final class SynTimer {
    private long last = System.currentTimeMillis();

    public boolean passed(double ms) {
        return (double) (System.currentTimeMillis() - last) >= ms;
    }

    public void reset() {
        last = System.currentTimeMillis();
    }

    public long getMs() {
        return System.currentTimeMillis() - last;
    }
}
