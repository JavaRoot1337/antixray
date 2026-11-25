package dev.javaroot.antixray.upd;

import dev.javaroot.antixray.cfg.Cfg;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public final class UpdSvc {

    private static final long PERIOD_HOURS = 12L;

    public enum Status {
        DOWNLOADED,
        UP_TO_DATE,
        NO_ASSET,
        IN_PROGRESS,
        FAILED
    }

    public record UpdRes(Status status, String ver, String detail) {
    }

    private final Plugin plug;
    private final Supplier<Cfg> cfgSup;
    private final Logger log;
    private final String curVer;
    private final GhClient gh;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private volatile String latestVer;
    private volatile String dlUrl;
    private volatile ScheduledTask task;

    public UpdSvc(Plugin plug, Supplier<Cfg> cfgSup) {
        this.plug = plug;
        this.cfgSup = cfgSup;
        this.log = plug.getLogger();
        this.curVer = VerCmp.normalize(plug.getPluginMeta().getVersion());
        this.gh = new GhClient(plug.getName() + "/" + curVer);
    }

    public void start() {
        stopTask();
        task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plug, t -> checkIfEnabled(), 0L, PERIOD_HOURS, TimeUnit.HOURS
        );
    }

    public void stop() {
        stopTask();
        gh.close();
    }

    private void stopTask() {
        ScheduledTask running = task;
        if (running != null) {
            running.cancel();
            task = null;
        }
    }

    public String curVer() { return curVer; }
    public String latestVer() { return latestVer; }
    public String dlUrl() { return dlUrl; }

    public boolean hasUpd() {
        String latest = latestVer;
        String url = dlUrl;
        return latest != null && url != null && VerCmp.isNewer(curVer, latest);
    }

    public void checkNow() {
        GhClient.FetchRes fetch = gh.fetchLatest();
        if (fetch.rel() == null) {
            log.warning("Update check failed: " + fetch.err());
            return;
        }
        cache(fetch.rel());
        if (VerCmp.isNewer(curVer, fetch.rel().ver())) {
            log.info("New version available: " + fetch.rel().ver()
                    + " (current: " + curVer + ") - " + fetch.rel().url());
        }
    }

    public UpdRes downloadUpd(File curJar, File updDir) {
        if (!busy.compareAndSet(false, true)) {
            return new UpdRes(Status.IN_PROGRESS, curVer, "");
        }
        try {
            GhClient.FetchRes fetch = gh.fetchLatest();
            if (fetch.rel() == null) {
                log.warning("Update check failed: " + fetch.err());
                return new UpdRes(Status.FAILED, curVer, fetch.err());
            }

            GhClient.RelInfo rel = fetch.rel();
            cache(rel);

            if (!VerCmp.isNewer(curVer, rel.ver())) {
                return new UpdRes(Status.UP_TO_DATE, curVer, "");
            }
            if (rel.assetUrl() == null || rel.assetName() == null) {
                return new UpdRes(Status.NO_ASSET, rel.ver(), rel.url());
            }

            Path dir = updDir.toPath();
            String fileName = curJar != null ? curJar.getName() : rel.assetName();
            Path dst = dir.resolve(fileName);
            Path part = dir.resolve(fileName + ".part");

            try {
                Files.createDirectories(dir);
                HttpResponse<Path> resp = gh.download(rel.assetUrl(), part);
                if (resp.statusCode() != 200) {
                    String err = "HTTP " + resp.statusCode();
                    log.warning("Update download failed: " + err);
                    return new UpdRes(Status.FAILED, rel.ver(), err);
                }
                if (!isValidJar(part)) {
                    log.warning("Update download failed: invalid jar archive");
                    return new UpdRes(Status.FAILED, rel.ver(), "invalid jar archive");
                }
                Files.move(part, dst, StandardCopyOption.REPLACE_EXISTING);
                log.info("Downloaded update " + rel.ver() + " to " + dst);
                return new UpdRes(Status.DOWNLOADED, rel.ver(), fileName);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warning("Update download interrupted");
                return new UpdRes(Status.FAILED, rel.ver(), "interrupted");
            } catch (IOException ex) {
                String err = GhClient.fmtErr(ex);
                log.warning("Update download failed: " + err);
                return new UpdRes(Status.FAILED, rel.ver(), err);
            } finally {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                }
            }
        } finally {
            busy.set(false);
        }
    }

    private void cache(GhClient.RelInfo rel) {
        latestVer = rel.ver();
        dlUrl = rel.url();
    }

    private void checkIfEnabled() {
        Cfg cfg = cfgSup.get();
        if (cfg != null && cfg.upd().enabled()) {
            checkNow();
        }
    }

    private static boolean isValidJar(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getEntry("paper-plugin.yml") != null;
        } catch (IOException ex) {
            return false;
        }
    }
}
