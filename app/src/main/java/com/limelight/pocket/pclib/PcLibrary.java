package com.limelight.pocket.pclib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.limelight.nvstream.http.NvApp;
import com.limelight.pocket.PcCompanion;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.RecentGames;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a PC's game library: what Pocket Nexus PC finds on the PC (Steam, Epic, GOG) plus the
 * linked Steam account's games, and sends it the library's commands (start, install).
 */
public final class PcLibrary {
    private PcLibrary() {}

    public static final int OK = 0;
    /** Pocket Nexus PC didn't answer (not running, or the firewall). */
    public static final int NO_HELPER = 1;
    /** It answered, but this handheld isn't linked to it yet (PIN). */
    public static final int NOT_LINKED = 2;
    /** It's an older version without the library. */
    public static final int OLD_HELPER = 3;
    /** It's new, but its library part couldn't load on the PC (the reason is in detail). */
    public static final int ADDON_FAILED = 4;

    public static final class Result {
        public final int status;
        public final List<PcGame> games;
        /** Why, for ADDON_FAILED. */
        public final String detail;
        /** Local PC rows came from a persisted snapshot, not a verified live connection. */
        public final boolean cached;

        Result(int status, List<PcGame> games, String detail) {
            this(status,games,detail,false);
        }

        Result(int status, List<PcGame> games, String detail, boolean cached) {
            this.status = status;
            this.games = games;
            this.detail = detail;
            this.cached = cached;
        }
    }

    /** A program of the PC that can be added to the PC games. */
    public static final class Program {
        public final String id;
        public final String name;
        public final String where;

        Program(String id, String name, String where) {
            this.id = id;
            this.name = name;
            this.where = where;
        }
    }

    public interface ProgramsCallback {
        /** On the main thread; null when the PC couldn't be asked. */
        void onPrograms(List<Program> programs, String error);
    }

    public interface Callback {
        /** On the main thread. */
        void onLoaded(Result result);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<PcGame>> LAST = new HashMap<>();

    /** The list shown last for this PC (to show at once while a fresh one loads), or null. */
    public static synchronized List<PcGame> cached(String uuid) {
        List<PcGame> l = LAST.get(uuid);
        return l != null ? new ArrayList<>(l) : null;
    }

    private static synchronized void remember(String uuid, List<PcGame> games) {
        LAST.put(uuid, new ArrayList<>(games));
    }

    private static synchronized void forget(String uuid) {
        LAST.remove(uuid);
    }

    public static void load(Context context, final String host, final String uuid, final boolean refreshAccount,
                            final Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            final Result result = loadNow(app, host, uuid, refreshAccount);
            if (result.status == OK) {
                remember(uuid, result.games);
            }
            MAIN.post(() -> callback.onLoaded(result));
        }, "PocketPcLibrary").start();
    }

    /** The same as load, blocking (never on the main thread). */
    public static Result loadBlocking(Context context, String host, String uuid) {
        Result r = loadNow(context.getApplicationContext(), host, uuid, false);
        if (r.status == OK) {
            remember(uuid, r.games);
        }
        return r;
    }

    /** The game of the PC library with this name (the same name, ignoring case and marks), or null. */
    public static PcGame byName(List<PcGame> games, String name) {
        String want = PcGame.normalize(name);
        if (games == null || want.isEmpty()) {
            return null;
        }
        PcGame found = null;
        for (PcGame g : games) {
            if (want.equals(PcGame.normalize(g.name))) {
                if (g.isInstalled()) {
                    return g;
                }
                if (found == null) {
                    found = g;
                }
            }
        }
        return found;
    }

    /** The last problems in Apollo's own log on the PC (blocking; empty when unknown). */
    public static List<String> apolloLog(Context context, String host, String uuid) {
        List<String> out = new ArrayList<>();
        PcCompanion.Session session = PcCompanion.open(context.getApplicationContext(), host, uuid);
        if (session == null) {
            return out;
        }
        try {
            if (session.authed) {
                session.setReadTimeout(8000);
                session.send("APOLLOLOG");
                for (int i = 0; i < 50; i++) {
                    String line = session.readLine();
                    if (line == null || line.startsWith("END") || line.startsWith("ERR")) {
                        break;
                    }
                    if (line.startsWith("L\t")) {
                        out.add(line.substring(2));
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            session.close();
        }
        return out;
    }

    private static Result loadNow(Context context, String host, String uuid, boolean refreshAccount) {
        List<String> lines = new ArrayList<>();
        int status = OK;
        String detail = "";
        PcCompanion.Session session = PcCompanion.open(context, host, uuid);
        if (session == null) {
            status = NO_HELPER;
        }
        else {
            PocketPrefs.markCompanionSeen(context, uuid);
            try {
                if (!session.authed) {
                    status = NOT_LINKED;
                }
                else {
                    // A big library on a slow disk takes a moment to read
                    session.setReadTimeout(25000);
                    session.send("GAMES");
                    HorizonLibraryReply reply = HorizonLibraryReply.read(session::readLine, "G\t", 20000,
                            line -> PcGame.parse(line) != null);
                    if (reply.error != null) {
                        String error = reply.error;
                        if (error.equals("ERR auth")) {
                            status = NOT_LINKED;
                        } else if (error.equals("ERR pclib") || error.startsWith("ERR pclib ")) {
                            status = ADDON_FAILED;
                            detail = error.substring(9).trim();
                        } else {
                            status = OLD_HELPER;
                        }
                    } else {
                        lines.addAll(reply.lines);
                    }
                }
            } catch (IOException e) {
                status = NO_HELPER;
            } finally {
                session.close();
            }
        }
        boolean cachedRows = false;
        if (status == OK) {
            // Save only the complete, counted PC response, never the merged Steam account list.
            HorizonLibraryStore.save(context, uuid, lines);
        } else if (status == NO_HELPER) {
            List<String> snapshot = HorizonLibraryStore.load(context, uuid);
            if (snapshot != null) {
                lines = snapshot;
                cachedRows = true;
            }
        } else {
            // An explicit auth / protocol error must not expose an earlier PC snapshot.
            forget(uuid);
            HorizonLibraryStore.clear(context, uuid);
        }
        List<PcGame> onPc = PcGame.parseAll(lines);
        List<PcGame> owned = SteamAccount.owned(context, refreshAccount);
        List<PcGame> all = PcGame.merge(onPc, owned);
        PcGame.sort(all);
        // Without the helper, the account's games still show (they just can't start or install)
        return new Result(status, all, detail, cachedRows);
    }

    /** The programs on the PC's Start menu and desktop (to add one to the PC games). */
    public static void programs(Context context, final String host, final String uuid, final ProgramsCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            List<Program> out = null;
            String error = null;
            PcCompanion.Session session = PcCompanion.open(app, host, uuid);
            if (session == null) {
                error = "unreachable";
            }
            else {
                try {
                    if (!session.authed) {
                        error = "auth";
                    }
                    else {
                        session.setReadTimeout(25000);
                        session.send("PROGRAMS");
                        HorizonLibraryReply reply = HorizonLibraryReply.read(session::readLine, "P\t", 5000,
                                line -> {
                                    String[] p = line.split("\t", -1);
                                    return p.length >= 3 && !p[1].trim().isEmpty() && !p[2].trim().isEmpty();
                                });
                        if (reply.error != null) {
                            error = reply.error;
                        } else {
                            out = new ArrayList<>();
                            for (String line : reply.lines) {
                                String[] p = line.split("\t", -1);
                                out.add(new Program(p[1], p[2], p.length > 3 ? p[3] : ""));
                            }
                        }
                    }
                } catch (IOException e) {
                    error = "unreachable";
                    out = null;
                } finally {
                    session.close();
                }
            }
            final List<Program> result = out;
            final String why = error;
            MAIN.post(() -> callback.onPrograms(result, why));
        }, "PocketPcPrograms").start();
    }

    /** Sends one command to Pocket Nexus PC (reply on the main thread, null if unreachable). */
    public static void send(Context context, String host, String uuid, String command, PcCompanion.Callback callback) {
        PcCompanion.runAsync(context, host, uuid, command, callback);
    }

    /** The Apollo app that is this game (same name), else null. */
    public static NvApp apolloAppFor(Context context, String uuid, PcGame game) {
        return apolloAppFor(context, uuid, game.name);
    }

    /** The Apollo app with this name, else null. */
    public static NvApp apolloAppFor(Context context, String uuid, String name) {
        String want = PcGame.normalize(name);
        if (want.isEmpty()) {
            return null;
        }
        for (RecentGames.Game g : RecentGames.forPc(context, uuid, 100000)) {
            if (want.equals(PcGame.normalize(g.app.getAppName()))) {
                return g.app;
            }
        }
        return null;
    }

    /** Apollo's "Desktop" app (the whole PC screen), else null. */
    public static NvApp desktopApp(Context context, String uuid) {
        NvApp loose = null;
        for (RecentGames.Game g : RecentGames.forPc(context, uuid, 100000)) {
            String n = PcGame.normalize(g.app.getAppName());
            if (n.equals("desktop")) {
                return g.app;
            }
            if (loose == null && n.contains("desktop")) {
                loose = g.app;
            }
        }
        return loose;
    }
}
