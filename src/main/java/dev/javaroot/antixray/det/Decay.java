package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.mdl.Session;
import dev.javaroot.antixray.sched.Exec;
import dev.javaroot.antixray.trap.TrapMgr;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Collection;
import java.util.function.Supplier;

public final class Decay {

    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Collection<Session>> sessionsSupplier;
    private final TrapMgr trapMgr;
    private final Exec exec;

    private ScheduledTask decayTask;
    private ScheduledTask cleanupTask;
    private volatile long lastDecayRunMs;

    public Decay(
            Supplier<Cfg> cfgSupplier,
            Supplier<Collection<Session>> sessionsSupplier,
            TrapMgr trapMgr,
            Exec exec
    ) {
        this.cfgSupplier = cfgSupplier;
        this.sessionsSupplier = sessionsSupplier;
        this.trapMgr = trapMgr;
        this.exec = exec;
    }

    public void start() {
        stop();
        this.lastDecayRunMs = System.currentTimeMillis();
        // Poll every 1 second so config reload of decay.interval-seconds takes effect without restarting tasks
        this.decayTask = exec.runAsyncTimer(this::tickDecay, 1L, 1L);
        this.cleanupTask = exec.runAsyncTimer(trapMgr::tickCleanup, 2L, 2L);
    }

    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void tickDecay() {
        Cfg cfg = cfgSupplier.get();
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastDecayRunMs < cfg.decayIntervalSeconds * 1000L) {
            return;
        }
        lastDecayRunMs = nowMs;

        for (Session session : sessionsSupplier.get()) {
            session.decaySuspicion(cfg.decayAmountPerInterval, nowMs);
            session.pruneBrokenBlocks(nowMs);
        }
    }
}
