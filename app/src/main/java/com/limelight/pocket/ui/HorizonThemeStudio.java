package com.limelight.pocket.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Local, versioned visual snapshots. Never stores or changes game/stream/host credentials. */
public final class HorizonThemeStudio {
    private static final int SLOTS = 3;
    private static final String PREFIX = "horizon_look_slot_";
    private HorizonThemeStudio() {}

    static final class Look {
        final int accent, density, corners;
        final String backdrop, material, sound, layout, focus;
        final boolean motion, colorArt, liveGlass;
        Look(int accent, String backdrop, String material, String sound, String layout,
             String focus, int density, int corners, boolean motion, boolean colorArt, boolean liveGlass) {
            this.accent = accent;
            this.backdrop = backdrop; this.material = material; this.sound = sound;
            this.layout = layout; this.focus = focus;
            this.density = density; this.corners = corners;
            this.motion = motion; this.colorArt = colorArt; this.liveGlass = liveGlass;
        }

        JSONObject encode() throws JSONException {
            return new JSONObject().put("version",1).put("accent",accent)
                    .put("backdrop",backdrop).put("material",material).put("sound",sound)
                    .put("layout",layout).put("focus",focus).put("density",density)
                    .put("corners",corners).put("motion",motion).put("colorArt",colorArt)
                    .put("liveGlass",liveGlass);
        }

        static Look decode(String raw) throws JSONException {
            if (raw == null || raw.length() > 4096) { throw new JSONException("Invalid look"); }
            JSONObject j = new JSONObject(raw);
            if (j.getInt("version") != 1) { throw new JSONException("Unknown look version"); }
            int accent = j.getInt("accent"), density = j.getInt("density"), corners = j.getInt("corners");
            String backdrop = j.getString("backdrop"), material = j.getString("material"),
                    sound = j.getString("sound"), layout = j.getString("layout"), focus = j.getString("focus");
            boolean knownLayout = false;
            for (Layouts.Layout l : Layouts.ALL) { knownLayout |= l.id.equals(layout); }
            if ((accent >>> 24) != 255 || density < 0 || density > 2 || corners < 0 || corners > 2
                    || !Arrays.asList("glow","ambient","art","black").contains(backdrop)
                    || !Arrays.asList("frost","glass").contains(material)
                    || !Arrays.asList(PocketPrefs.SOUND_STYLES).contains(sound)
                    || !knownLayout || !Arrays.asList("ring_zoom","ring","zoom").contains(focus)) {
                throw new JSONException("Unsupported look");
            }
            return new Look(accent,backdrop,material,sound,layout,focus,density,corners,
                    j.getBoolean("motion"),j.getBoolean("colorArt"),j.getBoolean("liveGlass"));
        }

        static Look capture(Context c) {
            SharedPreferences p = PocketPrefs.prefs(c);
            return new Look(PocketPrefs.accentColor(c), PocketPrefs.backdrop(c),
                    PocketPrefs.frost(c) ? PocketPrefs.MATERIAL_FROST : PocketPrefs.MATERIAL_GLASS,
                    p.getString(PocketPrefs.KEY_SOUND_STYLE,PocketPrefs.DEFAULT_SOUND_STYLE),
                    Layouts.home(c).id, PocketPrefs.focusStyle(c), HorizonAppearance.density(c),
                    HorizonAppearance.corners(c), HorizonAppearance.artMotion(c),
                    PocketPrefs.colorBackgroundArt(c), PocketPrefs.liveGlass(c));
        }

        void apply(Context c) {
            PocketPrefs.prefs(c).edit().putInt(PocketPrefs.KEY_ACCENT,accent)
                    .putString(PocketPrefs.KEY_BACKDROP,backdrop).putString(PocketPrefs.KEY_MATERIAL,material)
                    .putString(PocketPrefs.KEY_SOUND_STYLE,sound).putString("pocket_home_layout",layout)
                    .putString(PocketPrefs.KEY_FOCUS_STYLE,focus)
                    .putInt(HorizonAppearance.KEY_DENSITY,density).putInt(HorizonAppearance.KEY_CORNERS,corners)
                    .putBoolean(HorizonAppearance.KEY_ART_MOTION,motion)
                    .putBoolean(PocketPrefs.KEY_COLOR_ART,colorArt).putBoolean(PocketPrefs.KEY_LIVE_GLASS,liveGlass)
                    .apply();
        }

        String summary() {
            return String.format(Locale.ROOT,"#%06X · %s · %s",accent & 0xffffff,layout,backdrop);
        }
    }

    private static Look load(Context c, int slot) {
        if (slot < 0 || slot >= SLOTS) { return null; }
        try { return Look.decode(PocketPrefs.prefs(c).getString(PREFIX+slot,null)); }
        catch (JSONException | RuntimeException e) { return null; }
    }

    public static int count(Context c) {
        int n = 0;
        for (int i=0;i<SLOTS;i++) { if(load(c,i)!=null) { n++; } }
        return n;
    }

    private static boolean save(Context c, int slot) {
        if (slot < 0 || slot >= SLOTS) { return false; }
        try {
            String raw = Look.capture(c).encode().toString();
            Look.decode(raw);
            PocketPrefs.prefs(c).edit().putString(PREFIX+slot,raw).apply();
            return true;
        } catch (JSONException | RuntimeException e) { return false; }
    }

    public static void show(ConsoleActivity a, Runnable changed) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_studio_note)));
        for (int i=0;i<SLOTS;i++) {
            final int slot=i;
            final Look look=load(a,i);
            rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_studio_slot,i+1)));
            rows.add(StreamMenuView.Item.note(look==null ? a.getString(R.string.horizon_studio_empty) : look.summary()));
            rows.add(StreamMenuView.Item.confirm(a.getString(look==null ? R.string.horizon_studio_save : R.string.horizon_studio_replace), () -> {
                Toast.makeText(a,save(a,slot) ? R.string.horizon_studio_saved : R.string.horizon_studio_error,Toast.LENGTH_SHORT).show();
                changed.run();
            }));
            if (look!=null) {
                rows.add(StreamMenuView.Item.confirm(a.getString(R.string.horizon_studio_apply), () -> {
                    Look current=load(a,slot);
                    if(current!=null) { current.apply(a); changed.run(); }
                }));
                rows.add(StreamMenuView.Item.confirm(a.getString(R.string.horizon_studio_delete), () -> {
                    PocketPrefs.prefs(a).edit().remove(PREFIX+slot).apply();
                    changed.run();
                }));
            }
        }
        a.showConsolePanel(a.getString(R.string.horizon_studio_title),null,null,rows);
    }
}
