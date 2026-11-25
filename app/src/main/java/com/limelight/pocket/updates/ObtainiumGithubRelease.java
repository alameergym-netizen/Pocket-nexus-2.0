package com.limelight.pocket.updates;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ObtainiumGithubRelease {
    private static final String OWNER = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?";
    private static final String REPO = "[A-Za-z0-9_.-]{1,100}";
    private static final Pattern SOURCE = Pattern.compile("/(" + OWNER + "/" + REPO + ")(?:/releases)?/?");
    private static final Pattern REPOSITORY = Pattern.compile(OWNER + "/" + REPO);
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private static final long MAX_READ_NANOS = TimeUnit.SECONDS.toNanos(8);

    private ObtainiumGithubRelease() { }

    static String repositoryFrom(String source) {
        if (source == null) return null;
        try {
            URI uri = URI.create(source);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawPath() == null) return null;
            Matcher path = SOURCE.matcher(uri.getRawPath());
            return path.matches() && isRepository(path.group(1)) ? path.group(1) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isRepository(String repo) {
        if (repo == null || !REPOSITORY.matcher(repo).matches()) return false;
        String leaf = repo.substring(repo.indexOf('/') + 1);
        return !leaf.equals(".") && !leaf.equals("..");
    }

    static String latestTag(String repository) throws IOException, JSONException {
        if (!isRepository(repository)) throw new IOException("Invalid GitHub repository");
        URL url = new URL("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "PocketNexus");
            int response = connection.getResponseCode();
            if (response == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (response != HttpURLConnection.HTTP_OK || connection.getContentLength() > MAX_METADATA_BYTES) {
                throw new IOException("GitHub release metadata unavailable");
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long started = System.nanoTime();
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_METADATA_BYTES || System.nanoTime() - started > MAX_READ_NANOS) {
                        throw new IOException("GitHub release metadata exceeded its limit");
                    }
                    bytes.write(buffer, 0, count);
                }
                return stableTag(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            }
        } finally {
            connection.disconnect();
        }
    }

    static String stableTag(JSONObject release) throws JSONException {
        if (!Boolean.FALSE.equals(release.opt("draft")) || !Boolean.FALSE.equals(release.opt("prerelease"))
                || !(release.opt("tag_name") instanceof String)) throw new JSONException("Unexpected release metadata");
        String tag = release.getString("tag_name");
        if (tag.trim().isEmpty() || tag.length() > 128) throw new JSONException("Invalid release tag");
        for (int i = 0; i < tag.length(); i++) {
            char ch = tag.charAt(i);
            if (Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT) {
                throw new JSONException("Invalid release tag");
            }
        }
        return tag;
    }
}
