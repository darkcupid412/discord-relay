package com.darkcupid412.relay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Best effort check for a newer GitHub release, logged as a notice. Never throws into the caller. */
public final class UpdateChecker {
    private final HytaleLogger logger;
    private final String currentVersion;
    private final String repo;

    public UpdateChecker(HytaleLogger logger, String currentVersion, String repo) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.repo = repo;
    }

    public void checkAsync() {
        if (repo == null || repo.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(this::check);
    }

    private void check() {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DiscordRelay/" + currentVersion)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!body.has("tag_name")) {
                return;
            }
            String tag = body.get("tag_name").getAsString();
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;
            if (isNewer(latest, currentVersion)) {
                logger.atWarning().log("DiscordRelay update available: %s (current %s) - https://github.com/%s/releases/latest", latest, currentVersion, repo);
            }
        } catch (Exception e) {
            logger.atInfo().log("Update check failed: %s", e.getMessage());
        }
    }

    private static boolean isNewer(String latest, String current) {
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        int parts = Math.max(a.length, b.length);
        for (int i = 0; i < parts; i++) {
            int x = i < a.length ? parsePart(a[i]) : 0;
            int y = i < b.length ? parsePart(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int parsePart(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length() && Character.isDigit(value.charAt(i)); i++) {
            digits.append(value.charAt(i));
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }
}
