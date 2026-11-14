package com.limelight.pocket.retro;

import android.content.Context;

import com.limelight.pocket.PocketArt;
import com.limelight.pocket.SteamGridDb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Art for retro games from libretro's thumbnail server (what RetroArch shows): the box, a
 * screenshot and the title screen of nearly every cartridge and disc, named exactly like the
 * No-Intro / Redump sets the ROMs usually come from. Free and without a key, so a game that
 * SteamGridDB and the stores don't know still gets a cover and a background.
 */
public final class LibretroThumbs {
    private LibretroThumbs() {}

    private static final String BASE = "https://thumbnails.libretro.com/";
    private static final int MAX = 6 * 1024 * 1024;

    /** ES-DE's system names to libretro's playlist (folder) names. */
    private static final Map<String, String> SYSTEMS = new HashMap<>();

    static {
        String[][] map = {
                {"nes", "Nintendo - Nintendo Entertainment System"},
                {"famicom", "Nintendo - Nintendo Entertainment System"},
                {"fds", "Nintendo - Family Computer Disk System"},
                {"snes", "Nintendo - Super Nintendo Entertainment System"},
                {"sfc", "Nintendo - Super Nintendo Entertainment System"},
                {"snesna", "Nintendo - Super Nintendo Entertainment System"},
                {"n64", "Nintendo - Nintendo 64"},
                {"n64dd", "Nintendo - Nintendo 64DD"},
                {"gc", "Nintendo - GameCube"},
                {"wii", "Nintendo - Wii"},
                {"gb", "Nintendo - Game Boy"},
                {"gbc", "Nintendo - Game Boy Color"},
                {"gba", "Nintendo - Game Boy Advance"},
                {"nds", "Nintendo - Nintendo DS"},
                {"n3ds", "Nintendo - Nintendo 3DS"},
                {"virtualboy", "Nintendo - Virtual Boy"},
                {"pokemini", "Nintendo - Pokemon Mini"},
                {"satellaview", "Nintendo - Satellaview"},
                {"sufami", "Nintendo - Sufami Turbo"},
                {"psx", "Sony - PlayStation"},
                {"ps2", "Sony - PlayStation 2"},
                {"ps3", "Sony - PlayStation 3"},
                {"psp", "Sony - PlayStation Portable"},
                {"psvita", "Sony - PlayStation Vita"},
                {"megadrive", "Sega - Mega Drive - Genesis"},
                {"genesis", "Sega - Mega Drive - Genesis"},
                {"megadrivejp", "Sega - Mega Drive - Genesis"},
                {"mastersystem", "Sega - Master System - Mark III"},
                {"markiii", "Sega - Master System - Mark III"},
                {"gamegear", "Sega - Game Gear"},
                {"sg-1000", "Sega - SG-1000"},
                {"segacd", "Sega - Mega-CD - Sega CD"},
                {"megacd", "Sega - Mega-CD - Sega CD"},
                {"megacdjp", "Sega - Mega-CD - Sega CD"},
                {"sega32x", "Sega - 32X"},
                {"sega32xna", "Sega - 32X"},
                {"sega32xjp", "Sega - 32X"},
                {"saturn", "Sega - Saturn"},
                {"saturnjp", "Sega - Saturn"},
                {"dreamcast", "Sega - Dreamcast"},
                {"naomi", "Sega - Naomi"},
                {"pcengine", "NEC - PC Engine - TurboGrafx 16"},
                {"tg16", "NEC - PC Engine - TurboGrafx 16"},
                {"pcenginecd", "NEC - PC Engine CD - TurboGrafx-CD"},
                {"tg-cd", "NEC - PC Engine CD - TurboGrafx-CD"},
                {"supergrafx", "NEC - PC Engine SuperGrafx"},
                {"pcfx", "NEC - PC-FX"},
                {"atari2600", "Atari - 2600"},
                {"atari5200", "Atari - 5200"},
                {"atari7800", "Atari - 7800"},
                {"atarilynx", "Atari - Lynx"},
                {"atarijaguar", "Atari - Jaguar"},
                {"atarist", "Atari - ST"},
                {"ngp", "SNK - Neo Geo Pocket"},
                {"ngpc", "SNK - Neo Geo Pocket Color"},
                {"neogeocd", "SNK - Neo Geo CD"},
                {"neogeo", "SNK - Neo Geo"},
                {"arcade", "FBNeo - Arcade Games"},
                {"fbneo", "FBNeo - Arcade Games"},
                {"mame", "MAME"},
                {"wonderswan", "Bandai - WonderSwan"},
                {"wonderswancolor", "Bandai - WonderSwan Color"},
                {"3do", "The 3DO Company - 3DO"},
                {"colecovision", "Coleco - ColecoVision"},
                {"intellivision", "Mattel - Intellivision"},
                {"vectrex", "GCE - Vectrex"},
                {"msx", "Microsoft - MSX"},
                {"msx1", "Microsoft - MSX"},
                {"msx2", "Microsoft - MSX2"},
                {"xbox", "Microsoft - Xbox"},
                {"c64", "Commodore - 64"},
                {"amiga", "Commodore - Amiga"},
                {"amstradcpc", "Amstrad - CPC"},
                {"zxspectrum", "Sinclair - ZX Spectrum"},
                {"odyssey2", "Magnavox - Odyssey2"},
                {"channelf", "Fairchild - Channel F"},
                {"dos", "DOS"},
                {"scummvm", "ScummVM"},
        };
        for (String[] m : map) {
            SYSTEMS.put(m[0], m[1]);
        }
    }

    /** libretro's name for this system, or null when it has none. */
    public static String playlist(String system) {
        return system == null ? null : SYSTEMS.get(system.toLowerCase(Locale.ROOT));
    }

    /**
     * The names a game may be under there: its file's own name (a ROM set's name, with its
     * region), then with the usual regions when the file has none.
     */
    static List<String> names(String fileName, String title) {
        Set<String> out = new LinkedHashSet<>();
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.trim();
        out.add(base);
        // "Game (USA) (Disc 1)" -> "Game (USA)"; "Game (USA) [!]" -> "Game (USA)"
        String noDisc = base.replaceAll("\\s*\\((Disc|Disk|CD) ?\\d+[^)]*\\)", "").replaceAll("\\s*\\[[^]]*]", "").trim();
        out.add(noDisc);
        boolean hasRegion = noDisc.matches(".*\\((USA|Europe|Japan|World|U|E|J|[A-Z][a-z]+(, [A-Z][a-z]+)*)\\).*");
        String plain = (title != null && !title.trim().isEmpty() ? title : noDisc).replaceAll("\\s*\\([^)]*\\)", "")
                .replaceAll("\\s*\\[[^]]*]", "").trim();
        if (!hasRegion && !plain.isEmpty()) {
            for (String region : new String[]{"USA", "USA, Europe", "World", "Europe", "Japan"}) {
                out.add(plain + " (" + region + ")");
            }
        }
        out.remove("");
        return new ArrayList<>(out);
    }

    /** libretro's rule for file names: these characters become "_". */
    static String clean(String name) {
        return name.replaceAll("[&*/:`<>?\\\\|\"]", "_");
    }

    static String url(String playlist, String folder, String name) {
        return BASE + enc(playlist) + "/" + folder + "/" + enc(clean(name)) + ".png";
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    /**
     * Fills in what the game is missing (blocking): its box as the cover, a screenshot (else
     * its title screen) as the background. True when something new was saved.
     */
    public static boolean fill(Context app, String uuid, int id, String system, String fileName, String title) {
        String playlist = playlist(system);
        if (playlist == null || fileName == null) {
            return false;
        }
        boolean cover = !PocketArt.has(app, uuid, id, PocketArt.COVER);
        boolean hero = !PocketArt.has(app, uuid, id, PocketArt.HERO);
        if (!cover && !hero) {
            return false;
        }
        boolean changed = false;
        // The first name that has a box is the game's name there (the rest use it too)
        String found = null;
        for (String name : names(fileName, title)) {
            if (cover) {
                if (save(app, uuid, id, PocketArt.COVER, url(playlist, "Named_Boxarts", name))) {
                    found = name;
                    changed = true;
                    break;
                }
            }
            else if (save(app, uuid, id, PocketArt.HERO, url(playlist, "Named_Snaps", name))) {
                return true;
            }
        }
        if (hero && found != null) {
            changed |= save(app, uuid, id, PocketArt.HERO, url(playlist, "Named_Snaps", found))
                    || save(app, uuid, id, PocketArt.HERO, url(playlist, "Named_Titles", found));
        }
        return changed;
    }

    private static boolean save(Context app, String uuid, int id, int kind, String url) {
        if (PocketArt.has(app, uuid, id, kind)) {
            return false;
        }
        try {
            PocketArt.save(app, uuid, id, kind, SteamGridDb.download(url, MAX));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
