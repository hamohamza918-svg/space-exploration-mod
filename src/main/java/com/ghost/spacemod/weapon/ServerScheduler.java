package com.ghost.spacemod.weapon;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minimal server-tick scheduler — Fabric has no BukkitScheduler. Drives delayed and repeating
 * ability animations (novas, spins, bursts, temp-block reverts) off END_SERVER_TICK.
 */
public final class ServerScheduler {

    private static final class Task {
        long runAt;
        int period;
        int runsLeft;
        Runnable body;
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long tick = 0;

    private ServerScheduler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (!PENDING.isEmpty()) {
                TASKS.addAll(PENDING);
                PENDING.clear();
            }
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (tick < t.runAt) {
                    continue;
                }
                try {
                    t.body.run();
                } catch (Exception ignored) {
                    // never let an ability break the server tick
                }
                t.runsLeft--;
                if (t.period > 0 && t.runsLeft > 0) {
                    t.runAt = tick + t.period;
                } else {
                    it.remove();
                }
            }
        });
    }

    public static long now() {
        return tick;
    }

    /** Run once after delayTicks. */
    public static void runLater(int delayTicks, Runnable body) {
        Task t = new Task();
        t.runAt = tick + Math.max(1, delayTicks);
        t.period = 0;
        t.runsLeft = 1;
        t.body = body;
        PENDING.add(t);
    }

    /** Run count times, starting after delayTicks, every periodTicks. */
    public static void runTimer(int delayTicks, int periodTicks, int count, Runnable body) {
        Task t = new Task();
        t.runAt = tick + Math.max(1, delayTicks);
        t.period = Math.max(1, periodTicks);
        t.runsLeft = Math.max(1, count);
        t.body = body;
        PENDING.add(t);
    }
}
