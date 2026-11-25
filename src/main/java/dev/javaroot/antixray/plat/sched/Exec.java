package dev.javaroot.antixray.plat.sched;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class Exec {

    private final Plugin plug;

    public Exec(Plugin plug) {
        this.plug = plug;
    }

    public void runEntity(Entity ent, Runnable task) {
        ent.getScheduler().run(plug, t -> task.run(), null);
    }

    public void runEntity(Entity ent, Runnable task, Runnable retired) {
        ent.getScheduler().run(plug, t -> task.run(), retired);
    }

    public void runEntityDelayed(Entity ent, Runnable task, long delayTicks) {
        ent.getScheduler().runDelayed(plug, t -> task.run(), null, delayTicks);
    }

    public void runRegion(Location loc, Runnable task) {
        Bukkit.getRegionScheduler().run(plug, loc, t -> task.run());
    }

    public void runRegion(World w, int cx, int cz, Runnable task) {
        Bukkit.getRegionScheduler().run(plug, w, cx, cz, t -> task.run());
    }

    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plug, t -> task.run());
    }

    public void runForSnd(CommandSender snd, Runnable task) {
        if (snd instanceof Player p) {
            runEntity(p, task);
        } else {
            runGlobal(task);
        }
    }

    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plug, t -> task.run());
    }

    public ScheduledTask runAsyncTimer(Runnable task, long delaySec, long periodSec) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plug, t -> task.run(), delaySec, periodSec, TimeUnit.SECONDS);
    }

    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plug);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plug);
    }
}
