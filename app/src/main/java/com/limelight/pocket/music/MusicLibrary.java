package com.limelight.pocket.music;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The music on the handheld (its own storage and the SD card), from Android's media index: the
 * songs, and the albums, artists and folders they make. Read once and kept (read again when
 * asked: new music copied over). Blocking: never on the main thread.
 */
public final class MusicLibrary {
    private MusicLibrary() {}

    public static final class Song {
        public final long id;
        public final String title;
        public final String artist;
        public final String album;
        public final long albumId;
        public final long durationMs;
        public final int track;
        public final int year;
        public final String path;
        public final long added;

        Song(long id, String title, String artist, String album, long albumId, long durationMs, int track,
             int year, String path, long added) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.albumId = albumId;
            this.durationMs = durationMs;
            this.track = track;
            this.year = year;
            this.path = path;
            this.added = added;
        }

        public Uri uri() {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }

        public String folder() {
            if (path == null) {
                return "";
            }
            int slash = path.lastIndexOf('/');
            return slash > 0 ? path.substring(0, slash) : path;
        }
    }

    public static final class Album {
        public final long id;
        public final String title;
        public final String artist;
        public final List<Song> songs = new ArrayList<>();
        public int year;

        Album(long id, String title, String artist) {
            this.id = id;
            this.title = title;
            this.artist = artist;
        }

        public long durationMs() {
            long t = 0;
            for (Song s : songs) {
                t += s.durationMs;
            }
            return t;
        }
    }

    public static final class Artist {
        public final String name;
        public final List<Album> albums = new ArrayList<>();
        public final List<Song> songs = new ArrayList<>();

        Artist(String name) {
            this.name = name;
        }

        /** The album whose cover stands for the artist. */
        public long coverAlbum() {
            return albums.isEmpty() ? -1 : albums.get(0).id;
        }
    }

    public static final class Folder {
        public final String path;
        public final String name;
        public final List<Song> songs = new ArrayList<>();

        Folder(String path) {
            this.path = path;
            String n = new File(path).getName();
            this.name = n.isEmpty() ? path : n;
        }
    }

    public static final class Snapshot {
        public final List<Song> songs;
        public final List<Album> albums;
        public final List<Artist> artists;
        public final List<Folder> folders;
        public final Map<Long, Song> byId;

        Snapshot(List<Song> songs, List<Album> albums, List<Artist> artists, List<Folder> folders, Map<Long, Song> byId) {
            this.songs = songs;
            this.albums = albums;
            this.artists = artists;
            this.folders = folders;
            this.byId = byId;
        }

        public boolean isEmpty() {
            return songs.isEmpty();
        }
    }

    private static volatile Snapshot cached;

    /** What was read last (null before the first read). */
    public static Snapshot cached() {
        return cached;
    }

    /** The library (read now when it wasn't yet, or again when fresh). */
    public static synchronized Snapshot load(Context c, boolean fresh) {
        if (cached != null && !fresh) {
            return cached;
        }
        cached = scan(c.getApplicationContext());
        return cached;
    }

    /** The album art of an album, as Android keeps it. */
    public static Uri albumArt(long albumId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId);
        }
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
    }

    private static Snapshot scan(Context c) {
        List<Song> songs = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        String[] cols = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
        };
        // Music only (not ringtones, recordings or a game's sounds), a few seconds long at least
        String where = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " >= 15000";
        try (Cursor cur = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, where, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (cur != null) {
                while (cur.moveToNext()) {
                    String path = cur.getString(8);
                    if (path != null && (path.contains("/Android/data/") || path.contains("/Notifications/")
                            || path.contains("/Ringtones/") || path.contains("/Alarms/"))) {
                        continue;
                    }
                    String artist = clean(cur.getString(2));
                    String album = clean(cur.getString(3));
                    String title = cur.getString(1);
                    if (title == null || title.isEmpty()) {
                        title = path != null ? new File(path).getName().replaceFirst("\\.[^.]+$", "") : "?";
                    }
                    songs.add(new Song(cur.getLong(0), title, artist, album, cur.getLong(4), cur.getLong(5),
                            cur.getInt(6) % 1000, cur.getInt(7), path, cur.getLong(9)));
                }
            }
        } catch (RuntimeException ignored) {
            // No access to the media yet: an empty library
        }

        Map<Long, Song> byId = new HashMap<>();
        Map<Long, Album> albums = new LinkedHashMap<>();
        Map<String, Artist> artists = new LinkedHashMap<>();
        Map<String, Folder> folders = new LinkedHashMap<>();
        for (Song s : songs) {
            byId.put(s.id, s);
            Album a = albums.get(s.albumId);
            if (a == null) {
                a = new Album(s.albumId, s.album, s.artist);
                albums.put(s.albumId, a);
            }
            a.songs.add(s);
            a.year = Math.max(a.year, s.year);
            String key = s.artist.toLowerCase(Locale.ROOT);
            Artist ar = artists.get(key);
            if (ar == null) {
                ar = new Artist(s.artist);
                artists.put(key, ar);
            }
            ar.songs.add(s);
            if (!ar.albums.contains(a)) {
                ar.albums.add(a);
            }
            String fp = s.folder();
            Folder f = folders.get(fp);
            if (f == null) {
                f = new Folder(fp);
                folders.put(fp, f);
            }
            f.songs.add(s);
        }
        List<Album> albumList = new ArrayList<>(albums.values());
        for (Album a : albumList) {
            // An album's songs in its own order (disc / track), then by name
            Collections.sort(a.songs, (x, y) -> x.track != y.track ? Integer.compare(x.track, y.track)
                    : x.title.compareToIgnoreCase(y.title));
        }
        Collections.sort(albumList, (x, y) -> x.title.compareToIgnoreCase(y.title));
        List<Artist> artistList = new ArrayList<>(artists.values());
        Collections.sort(artistList, (x, y) -> x.name.compareToIgnoreCase(y.name));
        for (Artist ar : artistList) {
            Collections.sort(ar.albums, (x, y) -> Integer.compare(y.year, x.year));
        }
        List<Folder> folderList = new ArrayList<>(folders.values());
        Collections.sort(folderList, (x, y) -> x.name.compareToIgnoreCase(y.name));
        return new Snapshot(songs, albumList, artistList, folderList, byId);
    }

    private static String clean(String s) {
        if (s == null || s.isEmpty() || "<unknown>".equals(s)) {
            return "Unknown";
        }
        return s;
    }

    /** "3:41", "1:02:05". */
    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s / 60) % 60;
        s = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
