package dev.javaroot.antixray.upd;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

public final class GhClient implements AutoCloseable {

    private static final String API_URL = "https://api.github.com/repos/javaroot1337/AntiXray/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DL_TIMEOUT = Duration.ofSeconds(60);

    public record RelInfo(String ver, String url, String assetUrl, String assetName) {
    }

    public record FetchRes(RelInfo rel, String err) {
    }

    private final String ua;
    private final HttpClient http;

    public GhClient(String ua) {
        this.ua = ua;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public FetchRes fetchLatest() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", ua)
                .GET()
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new FetchRes(null, "interrupted");
        } catch (IOException ex) {
            return new FetchRes(null, fmtErr(ex));
        }

        if (resp.statusCode() != 200) {
            return new FetchRes(null, "HTTP " + resp.statusCode());
        }

        RelInfo rel = parseRel(resp.body());
        return rel != null ? new FetchRes(rel, null) : new FetchRes(null, "invalid response payload");
    }

    public HttpResponse<Path> download(String assetUrl, Path dst) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl))
                .timeout(DL_TIMEOUT)
                .header("User-Agent", ua)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofFile(dst));
    }

    public static RelInfo parseRel(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(body);
            if (!el.isJsonObject()) {
                return null;
            }
            root = el.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            return null;
        }

        String tag = readStr(root, "tag_name");
        String url = readStr(root, "html_url");
        if (tag == null || url == null) {
            return null;
        }

        String ver = VerCmp.normalize(tag);
        if (VerCmp.parseParts(ver) == null || !isSafeUrl(url)) {
            return null;
        }

        String aUrl = null;
        String aName = null;
        JsonElement assetsEl = root.get("assets");
        if (assetsEl != null && assetsEl.isJsonArray()) {
            JsonArray assets = assetsEl.getAsJsonArray();
            for (JsonElement item : assets) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject obj = item.getAsJsonObject();
                String name = readStr(obj, "name");
                String dl = readStr(obj, "browser_download_url");
                if (name == null || dl == null || !isSafeUrl(dl)) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".jar")
                        || lower.endsWith("-thin.jar")
                        || lower.endsWith("-sources.jar")
                        || lower.endsWith("-javadoc.jar")) {
                    continue;
                }
                aName = name;
                aUrl = dl;
                break;
            }
        }

        return new RelInfo(ver, url, aUrl, aName);
    }

    public static String fmtErr(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? name : name + ": " + msg;
    }

    private static String readStr(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        String val = el.getAsString().trim();
        return val.isEmpty() ? null : val;
    }

    private static boolean isSafeUrl(String url) {
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

    @Override
    public void close() {
        http.close();
    }
}
