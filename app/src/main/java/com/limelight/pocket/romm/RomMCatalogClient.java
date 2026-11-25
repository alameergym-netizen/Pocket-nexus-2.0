package com.limelight.pocket.romm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The read-only catalog endpoints for RomM 5.4.1. No paths or artwork URLs are retained. */
final class RomMCatalogClient {
    static final int PAGE_SIZE = 24;
    static final int MAX_SEARCH = 120;
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private final URI endpoint;
    private String token;
    private boolean versionChecked;

    enum Failure { CONNECTION, AUTHORIZATION, UNSUPPORTED }

    static final class RequestFailed extends Exception {
        private static final long serialVersionUID = 1L;
        final Failure failure;

        RequestFailed(Failure failure) { this.failure = failure; }
    }

    static final class Title {
        final long id;
        final String name, platform, summary;

        Title(long id, String name, String platform, String summary) {
            this.id = id;
            this.name = name;
            this.platform = platform;
            this.summary = summary;
        }
    }

    static final class Page {
        final int offset;
        final long total;
        final List<Title> titles;

        Page(int offset, long total, List<Title> titles) {
            this.offset = offset;
            this.total = total;
            this.titles = Collections.unmodifiableList(titles);
        }

        boolean hasNext() {
            return titles.size() == PAGE_SIZE && offset <= Integer.MAX_VALUE - PAGE_SIZE && (long) offset + PAGE_SIZE < total;
        }
    }

    RomMCatalogClient(String server, String token) throws URISyntaxException {
        this.endpoint = serverUri(server);
        if (token.length() > 2048 || !token.matches("[A-Za-z0-9._~-]+")) {
            throw new IllegalArgumentException("Invalid RomM token");
        }
        this.token = token;
    }

    synchronized void close() { token = null; }

    private synchronized String currentToken() { return token; }

    boolean isOpen() { return currentToken() != null; }

    static URI serverUri(String value) throws URISyntaxException {
        String address = value.trim();
        URI uri = new URI(address);
        String path = uri.getRawPath();
        if (address.length() > 1024 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535 || !path.matches("(?:/[A-Za-z0-9._~-]+)*/?")
                || !uri.normalize().equals(uri)) {
            throw new URISyntaxException("", "Use an HTTPS RomM server address");
        }
        return new URI(address.endsWith("/") ? address : address + "/");
    }

    static URI listUri(URI endpoint, int offset, String search) throws IOException {
        if (offset < 0 || search.length() > MAX_SEARCH) throw new IOException("Invalid catalog page");
        String query = "api/roms?limit=" + PAGE_SIZE + "&offset=" + offset
                + "&with_char_index=false&with_filter_values=false&with_rom_id_index=false&with_files=false";
        if (!search.isEmpty()) query += "&search_term=" + URLEncoder.encode(search, "UTF-8");
        return endpoint.resolve(query);
    }

    Page readPage(int offset, String search) throws RequestFailed {
        if (!isOpen()) throw new RequestFailed(Failure.AUTHORIZATION);
        try {
            if (!versionChecked) {
                JSONObject heartbeat = get(endpoint.resolve("api/heartbeat"), null);
                if (!"5.4.1".equals(heartbeat.getJSONObject("SYSTEM").getString("VERSION"))) {
                    throw new RequestFailed(Failure.UNSUPPORTED);
                }
                versionChecked = true;
            }
            String bearer = currentToken();
            if (bearer == null) throw new RequestFailed(Failure.AUTHORIZATION);
            return parse(get(listUri(endpoint, offset, search), bearer), offset);
        } catch (JSONException | IllegalArgumentException malformed) {
            throw new RequestFailed(Failure.UNSUPPORTED);
        } catch (IOException io) {
            throw new RequestFailed(Failure.CONNECTION);
        }
    }

    private static JSONObject get(URI address, String bearer) throws IOException, JSONException, RequestFailed {
        HttpURLConnection connection = (HttpURLConnection) address.toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new RequestFailed(Failure.AUTHORIZATION);
            if (status == 404) throw new RequestFailed(Failure.UNSUPPORTED);
            if (status != 200) throw new RequestFailed(Failure.CONNECTION);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long start = System.nanoTime();
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (System.nanoTime() - start > 15_000_000_000L) throw new RequestFailed(Failure.CONNECTION);
                    if (bytes.size() + n > MAX_JSON_BYTES) throw new RequestFailed(Failure.UNSUPPORTED);
                    bytes.write(buffer, 0, n);
                }
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    static Page parse(JSONObject json, int offset) throws JSONException {
        if (offset < 0 || json.getInt("offset") != offset || json.getInt("limit") != PAGE_SIZE) {
            throw new JSONException("Unexpected RomM page");
        }
        long total = json.getLong("total");
        JSONArray items = json.getJSONArray("items");
        if (total < 0 || items.length() > PAGE_SIZE || (items.length() > 0 && (long) offset + items.length() > total)) {
            throw new JSONException("Invalid RomM page size");
        }
        List<Title> titles = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            long id = item.getLong("id");
            String name = name(item, "name", 160);
            if (name.isEmpty()) name = name(item, "fs_name_no_ext", 160);
            if (id <= 0 || !seen.add(id) || name.isEmpty()) throw new JSONException("Invalid title identity");
            titles.add(new Title(id, name, name(item, "platform_display_name", 100), name(item, "summary", 240)));
        }
        return new Page(offset, total, titles);
    }

    private static String name(JSONObject json, String key, int max) {
        if (json.isNull(key)) return "";
        Object value = json.opt(key);
        if (!(value instanceof String)) return "";
        String text = ((String) value).replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
