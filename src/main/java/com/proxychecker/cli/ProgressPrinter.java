package com.proxychecker.cli;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple stderr progress printer.
 */
public class ProgressPrinter {

    private final int total;
    private final AtomicInteger completed = new AtomicInteger();
    private ScheduledExecutorService scheduler;

    public ProgressPrinter(int total) {
        this.total = total;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            System.err.printf("\rChecked %d/%d", completed.get(), total);
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void update(int done) {
        completed.set(done);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        System.err.printf("\rChecked %d/%d%n", completed.get(), total);
    }
}