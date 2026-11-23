package com.limelight.pocket;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.limelight.R;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Every setting of the full (advanced) settings screen, read from its own definition, so the
 * Streaming and Controls tabs can show all of them in their own console look, grouped as there,
 * nothing left behind (and anything added there later shows up here by itself).
 */
public final class AdvancedPrefs {

    public static final int CHECK = 0;
    public static final int LIST = 1;
    public static final int SEEK = 2;
    public static final int EDIT = 3;

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String SEEKBAR = "http://schemas.moonlight-stream.com/apk/res/seekbar";

    public static final class Item {
        public int type;
        public String key;
        public String title;
        public String summary;
        public String def;
        public String[] entries;
        public String[] values;
        public int min;
        public int max = 100;
        public int step = 1;
        public int divisor = 1;
        public String suffix;
        public String dependency;
    }

    public static final class Category {
        public String key;
        public String title;
        public final List<Item> items = new ArrayList<>();

        /** Its own key, or its first setting's (some groups have no key of their own). */
        public boolean is(String k) {
            return k.equals(key) || (!items.isEmpty() && k.equals(items.get(0).key));
        }
    }

    private static List<Category> cache;

    private AdvancedPrefs() {
    }

    public static synchronized List<Category> load(Context c) {
        if (cache != null) {
            return cache;
        }
        List<Category> out = new ArrayList<>();
        Resources r = c.getResources();
        Category current = null;
        try (XmlResourceParser p = r.getXml(R.xml.preferences)) {
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("PreferenceCategory".equals(tag)) {
                        current = new Category();
                        current.key = p.getAttributeValue(ANDROID, "key");
                        current.title = text(r, p, "title");
                        out.add(current);
                    }
                    else if (current != null) {
                        Item it = item(r, p, tag);
                        if (it != null && it.key != null && it.title != null) {
                            current.items.add(it);
                        }
                    }
                }
                ev = p.next();
            }
        } catch (Exception ignored) {
            // What was read so far is still shown
        }
        cache = out;
        return out;
    }

    private static Item item(Resources r, XmlResourceParser p, String tag) {
        Item it = new Item();
        if (tag.endsWith("CheckBoxPreference") || tag.endsWith("CheckboxPreference") || tag.endsWith("SwitchPreference")) {
            it.type = CHECK;
        }
        else if (tag.equals("ListPreference")) {
            it.type = LIST;
        }
        else if (tag.endsWith("SeekBarPreference")) {
            it.type = SEEK;
        }
        else if (tag.equals("EditTextPreference")) {
            it.type = EDIT;
        }
        else {
            return null;
        }
        it.key = p.getAttributeValue(ANDROID, "key");
        it.title = text(r, p, "title");
        it.summary = text(r, p, "summary");
        it.def = value(r, p, ANDROID, "defaultValue");
        it.dependency = p.getAttributeValue(ANDROID, "dependency");
        if (it.type == LIST) {
            it.entries = array(r, p, "entries");
            it.values = array(r, p, "entryValues");
            if (it.entries == null || it.values == null || it.entries.length != it.values.length) {
                return null;
            }
        }
        if (it.type == SEEK) {
            it.max = number(value(r, p, ANDROID, "max"), 100);
            it.min = number(value(r, p, SEEKBAR, "min"), 0);
            it.step = Math.max(1, number(value(r, p, SEEKBAR, "step"), 1));
            it.divisor = Math.max(1, number(value(r, p, SEEKBAR, "divisor"), 1));
            it.suffix = text(r, p, "text");
        }
        return it;
    }

    private static String text(Resources r, XmlResourceParser p, String name) {
        int id = p.getAttributeResourceValue(ANDROID, name, 0);
        if (id != 0) {
            try {
                return r.getString(id);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        return p.getAttributeValue(ANDROID, name);
    }

    private static String value(Resources r, XmlResourceParser p, String ns, String name) {
        int id = p.getAttributeResourceValue(ns, name, 0);
        if (id != 0) {
            try {
                String type = r.getResourceTypeName(id);
                if ("bool".equals(type)) {
                    return String.valueOf(r.getBoolean(id));
                }
                if ("integer".equals(type)) {
                    return String.valueOf(r.getInteger(id));
                }
                return r.getString(id);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        return p.getAttributeValue(ns, name);
    }

    private static String[] array(Resources r, XmlResourceParser p, String name) {
        int id = p.getAttributeResourceValue(ANDROID, name, 0);
        if (id == 0) {
            return null;
        }
        try {
            return r.getStringArray(id);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private static int number(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
