package com.limelight.pocket.romm;

import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.ui.StreamMenuView;
import com.limelight.pocket.ui.TextPrompt;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** A user-opened RomM catalog. Connection and four recent pages live only in this panel. */
public final class RomMCatalog {
    private static final AtomicBoolean REQUEST_RUNNING = new AtomicBoolean();
    private final ConsoleActivity screen;
    private final LinkedHashMap<String, RomMCatalogClient.Page> cache = new LinkedHashMap<>(4, 0.75f, true);
    private RomMCatalogClient client;

    public RomMCatalog(ConsoleActivity screen) { this.screen = screen; }

    public void show() {
        close();
        TextPrompt.show(screen, screen.getString(R.string.horizon_romm_server), screen.getString(R.string.horizon_romm_server_help),
                "", "https://", server -> {
                    try { RomMCatalogClient.serverUri(server); }
                    catch (URISyntaxException invalid) { message(R.string.horizon_romm_bad_server); return; }
                    TextPrompt.show(screen, screen.getString(R.string.horizon_romm_token), screen.getString(R.string.horizon_romm_token_help),
                            "", null, true, token -> connect(server, token));
                });
    }

    public boolean close() {
        if (client == null) return false;
        client.close();
        client = null;
        cache.clear();
        return true;
    }

    private void connect(String server, String token) {
        if (screen.isFinishing() || screen.isDestroyed()) return;
        try { client = new RomMCatalogClient(server, token); }
        catch (IllegalArgumentException | URISyntaxException invalid) { message(R.string.horizon_romm_bad_token); return; }
        load(null, 0, "");
    }

    private void load(List<StreamMenuView.Item> visible, int offset, String search) {
        RomMCatalogClient request = client;
        if (request == null || !request.isOpen()) return;
        String pageKey = offset + "\n" + search;
        RomMCatalogClient.Page cached = cache.get(pageKey);
        if (cached != null && visible != null) {
            screen.replaceConsolePage(visible, pageItems(cached, search));
            return;
        }
        List<StreamMenuView.Item> pending = Collections.singletonList(StreamMenuView.Item.note(
                screen.getString(R.string.horizon_romm_loading)));
        if (visible == null) {
            screen.showConsolePanel(screen.getString(R.string.horizon_romm_title), null, null, pending);
        } else {
            screen.replaceConsolePage(visible, pending);
        }
        if (!REQUEST_RUNNING.compareAndSet(false, true)) {
            showFailure(pending, offset, search, R.string.horizon_romm_busy);
            return;
        }
        new Thread(() -> {
            try {
                RomMCatalogClient.Page page = request.readPage(offset, search);
                screen.runOnUiThread(() -> {
                    if (client != request || screen.isFinishing() || screen.isDestroyed()) return;
                    cache.put(pageKey, page);
                    if (cache.size() > 4) cache.remove(cache.keySet().iterator().next());
                    screen.replaceConsolePage(pending, pageItems(page, search));
                });
            } catch (RomMCatalogClient.RequestFailed failed) {
                int error = failed.failure == RomMCatalogClient.Failure.AUTHORIZATION ? R.string.horizon_romm_denied
                        : failed.failure == RomMCatalogClient.Failure.UNSUPPORTED ? R.string.horizon_romm_unsupported
                        : R.string.horizon_romm_connection;
                screen.runOnUiThread(() -> {
                    if (client == request && !screen.isFinishing() && !screen.isDestroyed()) {
                        showFailure(pending, offset, search, error);
                    }
                });
            } finally {
                REQUEST_RUNNING.set(false);
            }
        }, "RomM-catalog").start();
    }

    private void showFailure(List<StreamMenuView.Item> pending, int offset, String search, int error) {
        List<StreamMenuView.Item> failure = new ArrayList<>();
        failure.add(StreamMenuView.Item.note(screen.getString(error)));
        failure.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_romm_retry), () -> load(failure, offset, search)));
        failure.add(StreamMenuView.Item.action(screen.getString(R.string.horizon_romm_disconnect), this::close));
        screen.replaceConsolePage(pending, failure);
    }

    private List<StreamMenuView.Item> pageItems(RomMCatalogClient.Page page, String search) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.note(screen.getString(R.string.horizon_romm_page,
                page.titles.isEmpty() ? 0 : page.offset + 1, (long) page.offset + page.titles.size(), page.total)));
        rows.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_romm_search), () -> TextPrompt.show(screen,
                screen.getString(R.string.horizon_romm_search), null, search, null, query -> {
                    if (query.length() > RomMCatalogClient.MAX_SEARCH) message(R.string.horizon_romm_search_invalid);
                    else load(rows, 0, query);
                })).withIcon("SEARCH"));
        if (page.offset > 0) {
            rows.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_romm_previous),
                    () -> load(rows, page.offset - RomMCatalogClient.PAGE_SIZE, search)));
        }
        if (page.hasNext()) {
            rows.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_romm_next),
                    () -> load(rows, page.offset + RomMCatalogClient.PAGE_SIZE, search)));
        }
        rows.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_bridge_refresh), () -> {
            cache.clear();
            load(rows, page.offset, search);
        }));
        if (page.titles.isEmpty()) rows.add(StreamMenuView.Item.note(screen.getString(R.string.horizon_romm_empty)));
        for (RomMCatalogClient.Title title : page.titles) {
            rows.add(StreamMenuView.Item.submenu(title.name, () -> showTitle(title)).withValue(title.platform));
        }
        rows.add(StreamMenuView.Item.action(screen.getString(R.string.horizon_romm_disconnect), this::close));
        return rows;
    }

    private void showTitle(RomMCatalogClient.Title title) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        if (!title.platform.isEmpty()) rows.add(StreamMenuView.Item.note(title.platform));
        if (!title.summary.isEmpty()) rows.add(StreamMenuView.Item.note(title.summary));
        rows.add(StreamMenuView.Item.info(screen.getString(R.string.horizon_romm_id), Long.toString(title.id)));
        screen.pushConsolePage(title.name, rows);
    }

    private void message(int text) { Toast.makeText(screen, text, Toast.LENGTH_LONG).show(); }
}
