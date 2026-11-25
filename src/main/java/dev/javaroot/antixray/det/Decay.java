package dev.javaroot.antixray.det;

import dev.javaroot.antixray.cfg.Cfg;
import dev.javaroot.antixray.cfg.sec.SusCfg;
import dev.javaroot.antixray.plat.sched.Exec;
import dev.javaroot.antixray.sess.Sess;
import dev.javaroot.antixray.trap.TrapMgr;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Collection;
import java.util.function.Supplier;

public final class Decay {

    private final Supplier<Cfg> cfgSup;
    private final Supplier<Collection<Sess>> sessSup;
    private final TrapMgr trapMgr;
    private final Exec exec;

    private ScheduledTask decayTask;
    private ScheduledTask cleanTask;
    private volatile long lastDecayMs;

    public Decay(Supplier<Cfg> cfgSup, Supplier<Collection<Sess>> sessSup, TrapMgr trapMgr, Exec exec) {
        this.cfgSup = cfgSup;
        this.sessSup = sessSup;
        this.trapMgr = trapMgr;
        this.exec = exec;
    }

    public void start() {
        stop();
        lastDecayMs = System.currentTimeMillis();
        // Poll every 1s so reloaded decay.interval-seconds takes effect without restarting tasks
        decayTask = exec.runAsyncTimer(this::tickDecay, 1L, 1L);
        cleanTask = exec.runAsyncTimer(trapMgr::tickClean, 2L, 2L);
    }

    public void stop() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
        if (cleanTask != null) {
            cleanTask.cancel();
            cleanTask = null;
        }
    }

    private void tickDecay() {
        SusCfg sc = cfgSup.get().sus();
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastDecayMs < sc.decayIntervalSec() * 1000L) {
            return;
        }
        lastDecayMs = nowMs;

        for (Sess sess : sessSup.get()) {
            sess.sus().decay(sc.decayAmount(), nowMs);
            sess.broken().prune(nowMs);
        }
    }
}
