package dev.javaroot.antixray.sched;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class Exec {

    private final Plugin plugin;

    public Exec(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runEntity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    public void runEntity(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), retired);
    }

    public void runRegion(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    public void runRegion(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, scheduledTask -> task.run());
    }

    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public ScheduledTask runAsyncTimer(Runnable task, long initialDelaySeconds, long periodSeconds) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                initialDelaySeconds,
                periodSeconds,
                TimeUnit.SECONDS
        );
    }

    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
