package dev.javaroot.antixray.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.javaroot.antixray.cfg.Cfg;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public final class UpdChecker {

    private static final String RELEASE_API_URL = "https://api.github.com/repos/javaroot1337/AntiXray/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
    private static final long CHECK_PERIOD_HOURS = 12L;

    public record ReleaseInfo(String version, String url, String assetUrl, String assetName) {
    }

    public enum UpdateStatus {
        DOWNLOADED,
        UP_TO_DATE,
        NO_ASSET,
        IN_PROGRESS,
        FAILED
    }

    public record UpdateResult(UpdateStatus status, String version, String detail) {
    }

    private final Plugin plugin;
    private final Supplier<Cfg> cfgSupplier;
    private final Logger logger;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final AtomicBoolean updating = new AtomicBoolean(false);

    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile String assetUrl;
    private volatile String assetName;
    private volatile ScheduledTask task;

    public UpdChecker(Plugin plugin, Supplier<Cfg> cfgSupplier) {
        this.plugin = plugin;
        this.cfgSupplier = cfgSupplier;
        this.logger = plugin.getLogger();
        this.currentVersion = normalizeVersion(plugin.getPluginMeta().getVersion());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() {
        stop();
        this.task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> checkIfEnabled(),
                0L,
                CHECK_PERIOD_HOURS,
                TimeUnit.HOURS
        );
    }

    public void stop() {
        ScheduledTask running = this.task;
        if (running != null) {
            running.cancel();
            this.task = null;
        }
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String latestVersion() {
        return latestVersion;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public boolean hasUpdate() {
        String latest = latestVersion;
        String url = downloadUrl;
        return latest != null && url != null && isNewerVersion(currentVersion, latest);
    }

    public void checkNow() {
        FetchResult fetch = fetchLatest();
        if (fetch.release() == null) {
            logger.warning("Update check failed: " + fetch.error());
            return;
        }
        cacheRelease(fetch.release());
        if (isNewerVersion(currentVersion, fetch.release().version())) {
            logger.info("New version available: " + fetch.release().version()
                    + " (current: " + currentVersion + ") - " + fetch.release().url());
        }
    }

    public UpdateResult downloadUpdate(File currentJar, File updateFolder) {
        if (!updating.compareAndSet(false, true)) {
            return new UpdateResult(UpdateStatus.IN_PROGRESS, currentVersion, "");
        }
        try {
            FetchResult fetch = fetchLatest();
            if (fetch.release() == null) {
                logger.warning("Update check failed: " + fetch.error());
                return new UpdateResult(UpdateStatus.FAILED, currentVersion, fetch.error());
            }

            ReleaseInfo release = fetch.release();
            cacheRelease(release);

            if (!isNewerVersion(currentVersion, release.version())) {
                return new UpdateResult(UpdateStatus.UP_TO_DATE, currentVersion, "");
            }
            if (release.assetUrl() == null || release.assetName() == null) {
                return new UpdateResult(UpdateStatus.NO_ASSET, release.version(), release.url());
            }

            Path updateDir = updateFolder.toPath();
            String targetFileName = currentJar != null ? currentJar.getName() : release.assetName();
            Path targetPath = updateDir.resolve(targetFileName);
            Path partPath = updateDir.resolve(targetFileName + ".part");

            try {
                Files.createDirectories(updateDir);
                HttpRequest downloadReq = HttpRequest.newBuilder(URI.create(release.assetUrl()))
                        .timeout(DOWNLOAD_TIMEOUT)
                        .header("User-Agent", plugin.getName() + "/" + currentVersion)
                        .GET()
                        .build();

                HttpResponse<Path> resp = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofFile(partPath));
                if (resp.statusCode() != 200) {
                    String err = "HTTP " + resp.statusCode();
                    logger.warning("Update download failed: " + err);
                    return new UpdateResult(UpdateStatus.FAILED, release.version(), err);
                }
                if (!isValidPluginJar(partPath)) {
                    logger.warning("Update download failed: invalid jar archive");
                    return new UpdateResult(UpdateStatus.FAILED, release.version(), "invalid jar archive");
                }
                Files.move(partPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Downloaded update " + release.version() + " to " + targetPath);
                return new UpdateResult(UpdateStatus.DOWNLOADED, release.version(), targetFileName);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.warning("Update download interrupted");
                return new UpdateResult(UpdateStatus.FAILED, release.version(), "interrupted");
            } catch (IOException ex) {
                String err = formatError(ex);
                logger.warning("Update download failed: " + err);
                return new UpdateResult(UpdateStatus.FAILED, release.version(), err);
            } finally {
                try {
                    Files.deleteIfExists(partPath);
                } catch (IOException ignored) {
                }
            }
        } finally {
            updating.set(false);
        }
    }

    private void cacheRelease(ReleaseInfo release) {
        this.latestVersion = release.version();
        this.downloadUrl = release.url();
        this.assetUrl = release.assetUrl();
        this.assetName = release.assetName();
    }

    private FetchResult fetchLatest() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", plugin.getName() + "/" + currentVersion)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new FetchResult(null, "interrupted");
        } catch (IOException ex) {
            return new FetchResult(null, formatError(ex));
        }

        if (response.statusCode() != 200) {
            return new FetchResult(null, "HTTP " + response.statusCode());
        }

        ReleaseInfo release = parseRelease(response.body());
        if (release == null) {
            return new FetchResult(null, "invalid response payload");
        }
        return new FetchResult(release, null);
    }

    private void checkIfEnabled() {
        Cfg cfg = cfgSupplier.get();
        if (cfg == null || !cfg.updateChecker.enabled()) {
            return;
        }
        checkNow();
    }

    private static boolean isValidPluginJar(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getEntry("paper-plugin.yml") != null;
        } catch (IOException ex) {
            return false;
        }
    }

    static ReleaseInfo parseRelease(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            return null;
        }

        String tagName = readString(root, "tag_name");
        String htmlUrl = readString(root, "html_url");
        if (tagName == null || htmlUrl == null) {
            return null;
        }

        String version = normalizeVersion(tagName);
        if (parseVersionParts(version) == null || !isSafeHttpUrl(htmlUrl)) {
            return null;
        }

        String foundAssetUrl = null;
        String foundAssetName = null;
        JsonElement assetsEl = root.get("assets");
        if (assetsEl != null && assetsEl.isJsonArray()) {
            JsonArray assets = assetsEl.getAsJsonArray();
            for (JsonElement item : assets) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject obj = item.getAsJsonObject();
                String name = readString(obj, "name");
                String dl = readString(obj, "browser_download_url");
                if (name == null || dl == null || !isSafeHttpUrl(dl)) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".jar")
                        || lower.endsWith("-thin.jar")
                        || lower.endsWith("-sources.jar")
                        || lower.endsWith("-javadoc.jar")) {
                    continue;
                }
                foundAssetName = name;
                foundAssetUrl = dl;
                break;
            }
        }

        return new ReleaseInfo(version, htmlUrl, foundAssetUrl, foundAssetName);
    }

    static boolean isNewerVersion(String current, String latest) {
        int[] curParts = parseVersionParts(current);
        int[] latParts = parseVersionParts(latest);
        if (curParts == null || latParts == null) {
            return false;
        }
        int len = Math.max(curParts.length, latParts.length);
        for (int i = 0; i < len; i++) {
            int c = i < curParts.length ? curParts[i] : 0;
            int l = i < latParts.length ? latParts[i] : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 1 && (trimmed.charAt(0) == 'v' || trimmed.charAt(0) == 'V')) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static int[] parseVersionParts(String raw) {
        String normalized = normalizeVersion(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        int cut = normalized.length();
        int dash = normalized.indexOf('-');
        if (dash >= 0) {
            cut = dash;
        }
        int plus = normalized.indexOf('+');
        if (plus >= 0 && plus < cut) {
            cut = plus;
        }
        String core = normalized.substring(0, cut).trim();
        if (core.isEmpty()) {
            return null;
        }

        String[] tokens = core.split("\\.", -1);
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (!isDigits(token)) {
                return null;
            }
            try {
                parts[i] = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return parts;
    }

    private static boolean isDigits(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String readString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        String val = el.getAsString().trim();
        return val.isEmpty() ? null : val;
    }

    private static boolean isSafeHttpUrl(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == '<' || c == '>') {
                return false;
            }
        }
        return true;
    }

    private static String formatError(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? name : name + ": " + msg;
    }

    private record FetchResult(ReleaseInfo release, String error) {
    }
}
