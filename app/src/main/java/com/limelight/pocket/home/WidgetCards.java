package com.limelight.pocket.home;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.RecentGames;
import com.limelight.pocket.device.PocketNotifications;
import com.limelight.pocket.ui.FocusGlow;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Home's widgets, each drawn for what it shows rather than as a box of text: the clock under
 * a sky that follows the time of day, the music over its own blurred cover, the battery as a
 * ring, the weather with its sky drawn, the week's play as bars. All the same size and shape,
 * and chosen the same way as everything else (a white ring and a glow).
 */
public final class WidgetCards {

    static final int HEIGHT_DP = 172;

    private WidgetCards() {
    }

    /** Where the music card gets the session that's playing. */
    interface MusicSource {
        MediaController controller();
    }

    /** A widget: its shape, how it's chosen, and update() for fresh contents. */
    abstract static class Card extends FrameLayout {
        final float d;

        Card(Context c, String kind, int widthDp) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setTag(kind);
            setFocusable(true);
            setClickable(true);
            setClipChildren(false);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 22 * d);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), dp(HEIGHT_DP));
            lp.setMarginEnd(dp(14));
            setLayoutParams(lp);
            FocusGlow.apply(this, 22 * d, false, 1.045f, null);
        }

        abstract void update();

        /** A rounded inner layer (the card's own picture), clipped to its corners. */
        FrameLayout layer() {
            FrameLayout l = new FrameLayout(getContext());
            l.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 22 * d);
                }
            });
            l.setClipToOutline(true);
            addView(l, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return l;
        }

        int dp(float v) {
            return Math.round(v * d);
        }

        TextView text(float sp, int color, android.graphics.Typeface tf) {
            TextView t = new TextView(getContext());
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTextColor(color);
            t.setTypeface(tf);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            return t;
        }

        TextView caption(String s) {
            TextView t = text(9.5f, 0xB3FFFFFF, PocketFonts.bold());
            t.setLetterSpacing(0.14f);
            t.setText(s != null ? s.toUpperCase(Locale.ROOT) : "");
            t.setShadowLayer(6 * d, 0, d, 0x66000000);
            return t;
        }

        static GradientDrawable glass() {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0x2EFFFFFF, 0x12FFFFFF});
            return g;
        }

        LinearLayout column(int gravity) {
            LinearLayout l = new LinearLayout(getContext());
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(gravity);
            return l;
        }

        LinearLayout.LayoutParams wrap(int top) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(top);
            return lp;
        }
    }

    // ---- Clock -------------------------------------------------------------------------------

    /** The time over a sky that follows the hour: dawn, day, dusk and night, the sun or the moon. */
    static final class ClockCard extends Card {
        private final Sky sky;
        private final TextView day;
        private final TextView time;
        private final TextView ampm;
        private final TextView date;

        ClockCard(Context c, String kind) {
            super(c, kind, 236);
            FrameLayout l = layer();
            sky = new Sky(c);
            l.addView(sky, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout col = column(Gravity.BOTTOM);
            col.setPadding(dp(18), dp(16), dp(18), dp(16));
            l.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            day = caption("");
            col.addView(day);
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.BOTTOM);
            time = text(48f, 0xFFFFFFFF, PocketFonts.display());
            time.setLetterSpacing(-0.03f);
            time.setShadowLayer(14 * d, 0, 2 * d, 0x59000000);
            line.addView(time);
            ampm = text(14f, 0xD9FFFFFF, PocketFonts.semibold());
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            al.setMarginStart(dp(5));
            al.bottomMargin = dp(8);
            line.addView(ampm, al);
            col.addView(line, wrap(4));
            date = text(13f, 0xE6FFFFFF, PocketFonts.medium());
            date.setShadowLayer(8 * d, 0, d, 0x59000000);
            col.addView(date, wrap(4));
            update();
        }

        @Override
        void update() {
            Calendar now = Calendar.getInstance();
            boolean h24 = DateFormat.is24HourFormat(getContext());
            day.setText(DateFormat.format("EEEE", now).toString().toUpperCase(Locale.ROOT));
            time.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", now));
            ampm.setVisibility(h24 ? GONE : VISIBLE);
            ampm.setText(DateFormat.format("a", now).toString().toUpperCase(Locale.ROOT));
            date.setText(DateFormat.format("MMMM d, yyyy", now));
            sky.setHour(now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f);
        }
    }

    /** A sky for an hour of the day, with the sun or the moon on its arc (and stars at night). */
    static final class Sky extends View {
        private static final float[] AT = {0f, 5f, 6.5f, 8.5f, 16.5f, 18.5f, 20f, 24f};
        private static final int[] TOP = {0xFF060A1C, 0xFF060A1C, 0xFF2B2F6B, 0xFF2563C4, 0xFF2563C4, 0xFF3B2A6E, 0xFF0A0F2A, 0xFF060A1C};
        private static final int[] BOTTOM = {0xFF151B45, 0xFF151B45, 0xFFF29E7A, 0xFF8EC5F2, 0xFF8EC5F2, 0xFFF08A5D, 0xFF1D2358, 0xFF151B45};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArgbEvaluator mix = new ArgbEvaluator();
        private float hour = 12f;

        Sky(Context c) {
            super(c);
        }

        void setHour(float h) {
            if (Math.abs(h - hour) > 0.01f) {
                hour = h;
                invalidate();
            }
        }

        private int at(int[] colors) {
            for (int i = 0; i < AT.length - 1; i++) {
                if (hour >= AT[i] && hour <= AT[i + 1]) {
                    float f = (hour - AT[i]) / Math.max(0.001f, AT[i + 1] - AT[i]);
                    return (int) mix.evaluate(f, colors[i], colors[i + 1]);
                }
            }
            return colors[0];
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            float d = getResources().getDisplayMetrics().density;
            paint.setShader(new LinearGradient(0, 0, 0, h, at(TOP), at(BOTTOM), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            boolean sun = hour >= 6f && hour < 18.5f;
            float t = sun ? (hour - 6f) / 12.5f : (((hour - 18.5f) + 24f) % 24f) / 11.5f;
            float x = w * (0.18f + 0.72f * t);
            float y = h * (0.62f - 0.5f * (float) Math.sin(Math.PI * t));
            float r = 13 * d;
            if (!sun) {
                // Stars, always in the same places
                paint.setColor(0x99FFFFFF);
                long seed = 7;
                for (int i = 0; i < 22; i++) {
                    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
                    float sx = (seed % 1000) / 1000f * w;
                    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
                    float sy = (seed % 1000) / 1000f * h * 0.6f;
                    c.drawCircle(sx, sy, (i % 3 == 0 ? 1.3f : 0.8f) * d, paint);
                }
            }
            int glow = sun ? 0x66FFE08A : 0x40DDE6FF;
            paint.setShader(new RadialGradient(x, y, r * 4.5f, glow, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 4.5f, paint);
            paint.setShader(null);
            paint.setColor(sun ? 0xFFFFF1C2 : 0xFFF1F4FF);
            c.drawCircle(x, y, r, paint);
            if (!sun) {
                // A crescent: the night sky bites the moon
                paint.setColor(at(TOP));
                c.drawCircle(x + r * 0.45f, y - r * 0.25f, r * 0.85f, paint);
            }
            // The bottom darker, for the words
            paint.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0x73000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    // ---- Music --------------------------------------------------------------------------------

    /** What's playing in any app: over its own cover, blurred; the cover, the song, where it is. */
    static final class MusicCard extends Card {
        private final MusicSource source;
        private final ImageView back;
        private final ImageView thumb;
        private final ImageView appIcon;
        private final TextView from;
        private final TextView title;
        private final TextView artist;
        private final Progress progress;
        private final ImageView state;
        private String artKey;

        MusicCard(Context c, String kind, MusicSource source) {
            super(c, kind, 330);
            this.source = source;
            FrameLayout l = layer();
            GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF3A2A6E, 0xFF14121F});
            l.setBackground(base);
            back = new ImageView(c);
            back.setScaleType(ImageView.ScaleType.CENTER_CROP);
            back.setAlpha(0.9f);
            l.addView(back, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View scrim = new View(c);
            scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x8C000000, 0x59000000}));
            l.addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));
            row.setClipChildren(false);
            l.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 14 * d);
                }
            });
            thumb.setClipToOutline(true);
            thumb.setElevation(dp(8));
            row.addView(thumb, new LinearLayout.LayoutParams(dp(104), dp(104)));
            LinearLayout col = column(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            cl.setMarginStart(dp(14));
            row.addView(col, cl);
            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            appIcon = new ImageView(c);
            head.addView(appIcon, new LinearLayout.LayoutParams(dp(13), dp(13)));
            from = caption("");
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            fl.setMarginStart(dp(6));
            head.addView(from, fl);
            col.addView(head);
            title = text(16.5f, 0xFFFFFFFF, PocketFonts.semibold());
            title.setMaxLines(2);
            title.setLineSpacing(0, 0.98f);
            col.addView(title, wrap(8));
            artist = text(12.5f, 0xCCFFFFFF, PocketFonts.medium());
            col.addView(artist, wrap(4));
            LinearLayout bottom = new LinearLayout(c);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            state = new ImageView(c);
            bottom.addView(state, new LinearLayout.LayoutParams(dp(14), dp(14)));
            progress = new Progress(c);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(10), 1f);
            pl.setMarginStart(dp(8));
            bottom.addView(progress, pl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = dp(12);
            col.addView(bottom, bl);
            update();
        }

        @Override
        void update() {
            Context c = getContext();
            MediaController mc = PocketNotifications.allowed(c) ? source.controller() : null;
            if (!PocketNotifications.allowed(c) || mc == null || mc.getMetadata() == null) {
                boolean access = PocketNotifications.allowed(c);
                appIcon.setImageDrawable(PocketIcons.drawable("MUSIC", dp(13), 0xB3FFFFFF));
                from.setText(c.getString(R.string.pocket_widget_now_playing).toUpperCase(Locale.ROOT));
                title.setText(access ? R.string.pocket_widget_nothing_playing : R.string.pocket_widget_music_access);
                artist.setText(access ? R.string.pocket_widget_music_open : R.string.pocket_widget_music_access_sub);
                artist.setMaxLines(2);
                state.setVisibility(GONE);
                progress.setVisibility(GONE);
                setArt(null, "none");
                return;
            }
            artist.setMaxLines(1);
            MediaMetadata m = mc.getMetadata();
            CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (a == null) {
                a = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            String pkg = mc.getPackageName();
            try {
                appIcon.setImageDrawable(c.getPackageManager().getApplicationIcon(pkg));
                from.setText(c.getPackageManager().getApplicationLabel(c.getPackageManager()
                        .getApplicationInfo(pkg, 0)).toString().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                from.setText(pkg);
            }
            title.setText(t != null ? t : "");
            artist.setText(a != null ? a : "");
            PlaybackState st = mc.getPlaybackState();
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            state.setVisibility(VISIBLE);
            state.setImageDrawable(PocketIcons.drawable(playing ? "PLAY" : "PAUSE", dp(14), 0xFFFFFFFF));
            long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            progress.setVisibility(dur > 0 ? VISIBLE : INVISIBLE);
            if (st != null) {
                progress.set(st.getPosition(), st.getLastPositionUpdateTime(), playing ? st.getPlaybackSpeed() : 0f, dur);
            }
            Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) {
                b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            setArt(b, (t != null ? t.toString() : "") + "|" + pkg + "|" + (b != null ? b.getGenerationId() : 0));
        }

        private void setArt(Bitmap b, String key) {
            if (key.equals(artKey)) {
                return;
            }
            artKey = key;
            back.setImageBitmap(b);
            if (b != null && Build.VERSION.SDK_INT >= 31) {
                back.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(28 * d, 28 * d, Shader.TileMode.CLAMP));
            }
            if (b != null) {
                thumb.setImageBitmap(b);
                thumb.setBackground(null);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            else {
                thumb.setImageDrawable(PocketIcons.drawable("MUSIC", dp(40), 0x80FFFFFF));
                thumb.setScaleType(ImageView.ScaleType.CENTER);
                thumb.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF5B3FA8, 0xFF261B4A}));
            }
            if (PocketPrefs.animationsEnabled(getContext())) {
                thumb.setAlpha(0f);
                thumb.setScaleX(0.9f);
                thumb.setScaleY(0.9f);
                thumb.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(com.limelight.pocket.ui.Spring.POP).start();
            }
        }
    }

    /** A thin line of where the song is, moving on its own while it plays. */
    static final class Progress extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long pos;
        private long at;
        private float speed;
        private long duration;

        Progress(Context c) {
            super(c);
            track.setColor(0x40FFFFFF);
            done.setColor(0xFFFFFFFF);
        }

        void set(long pos, long at, float speed, long duration) {
            this.pos = pos;
            this.at = at;
            this.speed = speed;
            this.duration = duration;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float h = 3.5f * d;
            float cy = getHeight() / 2f;
            r.set(0, cy - h / 2, getWidth(), cy + h / 2);
            c.drawRoundRect(r, h, h, track);
            long now = pos;
            if (speed > 0 && at > 0) {
                now += (long) ((SystemClock.elapsedRealtime() - at) * speed);
            }
            float f = duration > 0 ? Math.max(0f, Math.min(1f, now / (float) duration)) : 0f;
            r.set(0, cy - h / 2, getWidth() * f, cy + h / 2);
            c.drawRoundRect(r, h, h, done);
            if (speed > 0 && isAttachedToWindow()) {
                postInvalidateDelayed(250);
            }
        }
    }

    // ---- Notifications ---------------------------------------------------------------------

    /** The newest few, each with its app's icon, and how many in all. */
    static final class NotesCard extends Card {
        private final TextView count;
        private final LinearLayout rows;
        private final TextView empty;

        NotesCard(Context c, String kind) {
            super(c, kind, 290);
            FrameLayout l = layer();
            l.setBackground(glass());
            LinearLayout col = column(Gravity.TOP);
            col.setPadding(dp(16), dp(14), dp(16), dp(12));
            l.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(caption(c.getString(R.string.pocket_device_notifications)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            count = text(11f, 0xFF0B0B0D, PocketFonts.bold());
            count.setGravity(Gravity.CENTER);
            count.setMinWidth(dp(20));
            count.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(10));
            pill.setColor(0xFFFFFFFF);
            count.setBackground(pill);
            head.addView(count);
            col.addView(head);
            rows = column(Gravity.TOP);
            col.addView(rows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            empty = text(13f, 0x99FFFFFF, PocketFonts.medium());
            empty.setGravity(Gravity.CENTER);
            empty.setMaxLines(2);
            empty.setCompoundDrawablePadding(dp(8));
            l.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            update();
        }

        @Override
        void update() {
            Context c = getContext();
            rows.removeAllViews();
            if (!PocketNotifications.allowed(c)) {
                count.setVisibility(GONE);
                showEmpty(c.getString(R.string.pocket_device_allow_notifications), "LOCK");
                return;
            }
            List<PocketNotifications.Note> notes = PocketNotifications.current(c);
            if (notes.isEmpty()) {
                count.setVisibility(GONE);
                showEmpty(c.getString(R.string.pocket_widget_caught_up), "BELL");
                return;
            }
            empty.setVisibility(GONE);
            count.setVisibility(VISIBLE);
            count.setText(String.valueOf(notes.size()));
            for (int i = 0; i < notes.size() && i < 3; i++) {
                PocketNotifications.Note n = notes.get(i);
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView icon = new ImageView(c);
                try {
                    icon.setImageDrawable(c.getPackageManager().getApplicationIcon(n.packageName));
                } catch (Exception e) {
                    icon.setImageDrawable(PocketIcons.drawable("BELL", dp(20), 0xFFFFFFFF));
                }
                row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
                LinearLayout words = column(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                wl.setMarginStart(dp(10));
                row.addView(words, wl);
                TextView t = text(12.5f, 0xFFFFFFFF, PocketFonts.semibold());
                t.setText(n.title.isEmpty() ? n.app : n.title);
                words.addView(t);
                TextView s = text(11f, 0xA6FFFFFF, PocketFonts.regular());
                s.setText(n.text.isEmpty() ? n.app : n.text);
                words.addView(s, wrap(2));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = dp(i == 0 ? 12 : 9);
                rows.addView(row, rl);
            }
        }

        private void showEmpty(String s, String glyph) {
            empty.setVisibility(VISIBLE);
            empty.setText(s);
            Drawable g = PocketIcons.drawable(glyph, dp(30), 0x66FFFFFF);
            g.setBounds(0, 0, dp(30), dp(30));
            empty.setCompoundDrawables(null, g, null, null);
        }
    }

    // ---- Device -------------------------------------------------------------------------------

    /** The battery as a ring (its colour by how full, a bolt when charging), then space, Wi-Fi and heat. */
    static final class DeviceCard extends Card {
        private final BatteryRing ring;
        private final TextView wifi;
        private final TextView storage;
        private final Bar storageBar;
        private final TextView heat;

        DeviceCard(Context c, String kind) {
            super(c, kind, 290);
            FrameLayout l = layer();
            l.setBackground(glass());
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            l.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout left = column(Gravity.CENTER_HORIZONTAL);
            left.addView(caption(c.getString(R.string.pocket_widget_battery)));
            ring = new BatteryRing(c);
            left.addView(ring, new LinearLayout.LayoutParams(dp(96), dp(96)));
            ((LinearLayout.LayoutParams) ring.getLayoutParams()).topMargin = dp(10);
            row.addView(left);
            LinearLayout col = column(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            cl.setMarginStart(dp(18));
            row.addView(col, cl);
            wifi = stat(col, "WIFI", 0);
            storage = stat(col, "FOLDER", 12);
            storageBar = new Bar(c);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
            bl.topMargin = dp(6);
            bl.setMarginStart(dp(22));
            col.addView(storageBar, bl);
            heat = stat(col, "PULSE", 12);
            update();
        }

        private TextView stat(LinearLayout col, String glyph, int top) {
            TextView t = text(12.5f, 0xF2FFFFFF, PocketFonts.medium());
            Drawable g = PocketIcons.drawable(glyph, dp(14), 0xB3FFFFFF);
            g.setBounds(0, 0, dp(14), dp(14));
            t.setCompoundDrawablesRelative(g, null, null, null);
            t.setCompoundDrawablePadding(dp(8));
            col.addView(t, wrap(top));
            return t;
        }

        @Override
        void update() {
            Context c = getContext();
            Intent b = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                int status = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                ring.set(Math.round(level * 100f / Math.max(1, scale)), charging);
                int tenths = b.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (tenths != Integer.MIN_VALUE) {
                    boolean f = Weather.fahrenheit(c);
                    float tc = tenths / 10f;
                    heat.setText(c.getString(R.string.pocket_widget_heat,
                            Math.round(f ? tc * 9 / 5 + 32 : tc) + (f ? "°F" : "°C")));
                }
            }
            wifi.setText(ConsoleActivity.isWifiConnected(c) ? c.getString(R.string.pocket_widget_wifi_on)
                    : c.getString(R.string.pocket_link_offline));
            try {
                android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
                long total = fs.getTotalBytes();
                long free = fs.getAvailableBytes();
                storage.setText(c.getString(R.string.pocket_device_free,
                        android.text.format.Formatter.formatShortFileSize(c, free)));
                storageBar.set(total > 0 ? 1f - free / (float) total : 0f);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** The battery: a ring that fills, green / amber / red, its number inside, a bolt when charging. */
    static final class BatteryRing extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Path path = new Path();
        private int pct = -1;
        private boolean charging;

        BatteryRing(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(7 * d);
            track.setColor(0x26FFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(7 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            text.setColor(0xFFFFFFFF);
            text.setTypeface(PocketFonts.display());
            text.setTextAlign(Paint.Align.CENTER);
            bolt.setColor(0xFFFFFFFF);
        }

        void set(int pct, boolean charging) {
            if (pct == this.pct && charging == this.charging) {
                return;
            }
            this.pct = pct;
            this.charging = charging;
            int color = charging ? 0xFF34C759 : pct > 50 ? 0xFF34C759 : pct > 20 ? 0xFFFFB020 : 0xFFFF453A;
            float d = getResources().getDisplayMetrics().density;
            arc.setColor(color);
            arc.setShadowLayer(7 * d, 0, 0, (color & 0x00FFFFFF) | 0x99000000);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float inset = 7 * d;
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            c.drawOval(oval, track);
            if (pct >= 0) {
                c.drawArc(oval, -90, 360f * pct / 100f, false, arc);
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            text.setTextSize(getHeight() * 0.26f);
            Paint.FontMetrics fm = text.getFontMetrics();
            float y = cy - (fm.ascent + fm.descent) / 2f + (charging ? 5 * d : 0);
            c.drawText(pct >= 0 ? pct + "%" : "", cx, y, text);
            if (charging) {
                float s = 7 * d;
                float by = cy - 15 * d;
                path.reset();
                path.moveTo(cx + s * 0.15f, by - s);
                path.lineTo(cx - s * 0.55f, by + s * 0.15f);
                path.lineTo(cx - s * 0.05f, by + s * 0.15f);
                path.lineTo(cx - s * 0.2f, by + s);
                path.lineTo(cx + s * 0.55f, by - s * 0.15f);
                path.lineTo(cx + s * 0.05f, by - s * 0.15f);
                path.close();
                c.drawPath(path, bolt);
            }
        }
    }

    /** A thin bar that fills (storage). */
    static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float f;

        Bar(Context c) {
            super(c);
            track.setColor(0x26FFFFFF);
        }

        void set(float f) {
            this.f = Math.max(0f, Math.min(1f, f));
            fill.setColor(this.f > 0.9f ? 0xFFFF453A : this.f > 0.75f ? 0xFFFFB020 : 0xFFFFFFFF);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            r.set(0, 0, getWidth() * f, h);
            c.drawRoundRect(r, h / 2, h / 2, fill);
        }
    }

    // ---- Weather -----------------------------------------------------------------------------

    /** The weather where the player is: its sky drawn, the temperature big, the next three days. */
    static final class WeatherCard extends Card {
        private final FrameLayout l;
        private final SkyIcon icon;
        private final TextView city;
        private final TextView temp;
        private final TextView words;
        private final TextView range;
        private final LinearLayout days;

        WeatherCard(Context c, String kind) {
            super(c, kind, 300);
            l = layer();
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2B6CC4, 0xFF173A73}));
            icon = new SkyIcon(c);
            LayoutParams il = new LayoutParams(dp(78), dp(78), Gravity.TOP | Gravity.END);
            il.topMargin = dp(10);
            il.rightMargin = dp(12);
            l.addView(icon, il);
            LinearLayout col = column(Gravity.TOP);
            col.setPadding(dp(18), dp(14), dp(18), dp(14));
            l.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            city = caption("");
            col.addView(city);
            temp = text(44f, 0xFFFFFFFF, PocketFonts.display());
            temp.setShadowLayer(12 * d, 0, 2 * d, 0x40000000);
            col.addView(temp, wrap(4));
            words = text(13f, 0xF2FFFFFF, PocketFonts.semibold());
            col.addView(words, wrap(2));
            range = text(11.5f, 0xCCFFFFFF, PocketFonts.medium());
            col.addView(range, wrap(3));
            days = new LinearLayout(c);
            days.setOrientation(LinearLayout.HORIZONTAL);
            days.setGravity(Gravity.END | Gravity.BOTTOM);
            LayoutParams dl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            dl.rightMargin = dp(14);
            dl.bottomMargin = dp(14);
            l.addView(days, dl);
            update();
        }

        @Override
        void update() {
            Context c = getContext();
            Weather.Now now = Weather.cached(c);
            show(now);
            Weather.refresh(c, false, this::show);
        }

        private long shownAt = -1;

        void show(Weather.Now now) {
            Context c = getContext();
            long at = now != null ? now.at : 0;
            if (at == shownAt && at != 0) {
                return;
            }
            shownAt = at;
            if (now == null) {
                String guess = Weather.city(c);
                city.setText((guess != null ? guess : c.getString(R.string.pocket_widget_weather)).toUpperCase(Locale.ROOT));
                temp.setText("--°");
                words.setText(guess != null ? R.string.pocket_weather_loading : R.string.pocket_weather_set_city);
                range.setText(R.string.pocket_weather_hint);
                icon.set(Weather.PARTLY, true);
                days.removeAllViews();
                return;
            }
            city.setText(now.city.toUpperCase(Locale.ROOT));
            temp.setText(now.temp + "°");
            words.setText(skyWord(c, now.sky));
            range.setText(c.getString(R.string.pocket_weather_range, now.high + "°", now.low + "°"));
            icon.set(now.sky, now.day);
            int[] g = gradient(now.sky, now.day);
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, g));
            days.removeAllViews();
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < now.nextHigh.length; i++) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
                LinearLayout one = column(Gravity.CENTER_HORIZONTAL);
                TextView dn = text(9.5f, 0xB3FFFFFF, PocketFonts.bold());
                dn.setText(DateFormat.format("EEE", cal).toString().toUpperCase(Locale.ROOT));
                one.addView(dn);
                SkyIcon mini = new SkyIcon(c);
                mini.set(now.nextSky[i], true);
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(24), dp(24));
                ml.topMargin = dp(2);
                one.addView(mini, ml);
                TextView hi = text(11f, 0xFFFFFFFF, PocketFonts.semibold());
                hi.setText(now.nextHigh[i] + "°");
                one.addView(hi, wrap(1));
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                ol.setMarginStart(dp(10));
                days.addView(one, ol);
            }
        }

        static int[] gradient(int sky, boolean day) {
            if (!day) {
                return new int[]{0xFF1B2250, 0xFF0A0D24};
            }
            switch (sky) {
                case Weather.CLEAR:
                    return new int[]{0xFF2F86E8, 0xFF1A4F9C};
                case Weather.PARTLY:
                    return new int[]{0xFF4A86C8, 0xFF24467A};
                case Weather.FOG:
                case Weather.CLOUDY:
                    return new int[]{0xFF6B7A90, 0xFF363F4E};
                case Weather.DRIZZLE:
                case Weather.RAIN:
                    return new int[]{0xFF4B5D7A, 0xFF1F2A3D};
                case Weather.SNOW:
                    return new int[]{0xFF8FA6C2, 0xFF4B5E7A};
                case Weather.STORM:
                    return new int[]{0xFF3D3858, 0xFF17142A};
                default:
                    return new int[]{0xFF2B6CC4, 0xFF173A73};
            }
        }

        static String skyWord(Context c, int sky) {
            int[] words = {R.string.pocket_sky_clear, R.string.pocket_sky_partly, R.string.pocket_sky_cloudy,
                    R.string.pocket_sky_fog, R.string.pocket_sky_drizzle, R.string.pocket_sky_rain,
                    R.string.pocket_sky_snow, R.string.pocket_sky_storm};
            return c.getString(words[Math.max(0, Math.min(words.length - 1, sky))]);
        }
    }

    /** The sky drawn: sun or moon, clouds, rain, snow, lightning, fog. */
    public static final class SkyIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int sky = Weather.CLEAR;
        private boolean day = true;

        public SkyIcon(Context c) {
            super(c);
        }

        public void set(int sky, boolean day) {
            this.sky = sky;
            this.day = day;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float u = Math.min(w, h);
            boolean body = sky == Weather.CLEAR || sky == Weather.PARTLY;
            boolean cloud = sky != Weather.CLEAR;
            if (body) {
                float bx = cloud ? w * 0.62f : w * 0.5f;
                float by = cloud ? h * 0.36f : h * 0.5f;
                float r = u * (cloud ? 0.2f : 0.26f);
                if (day) {
                    p.setShader(new RadialGradient(bx, by, r * 2.2f, 0x80FFD54A, 0x00FFD54A, Shader.TileMode.CLAMP));
                    c.drawCircle(bx, by, r * 2.2f, p);
                    p.setShader(null);
                    p.setColor(0xFFFFD54A);
                    c.drawCircle(bx, by, r, p);
                }
                else {
                    p.setColor(0xFFF1F4FF);
                    c.drawCircle(bx, by, r, p);
                    p.setColor(0xFF1B2250);
                    c.drawCircle(bx + r * 0.5f, by - r * 0.3f, r * 0.85f, p);
                }
            }
            if (cloud) {
                int col = sky == Weather.STORM ? 0xFFB9BCD0 : sky == Weather.RAIN || sky == Weather.DRIZZLE ? 0xFFDCE3EE : 0xFFFFFFFF;
                float cy = sky == Weather.PARTLY ? h * 0.58f : h * 0.46f;
                float cx = w * 0.46f;
                p.setColor(0x33000000);
                drawCloud(c, cx + u * 0.02f, cy + u * 0.03f, u);
                p.setColor(col);
                drawCloud(c, cx, cy, u);
            }
            p.setStrokeCap(Paint.Cap.ROUND);
            if (sky == Weather.RAIN || sky == Weather.DRIZZLE || sky == Weather.STORM) {
                p.setColor(0xFF8FC7FF);
                p.setStrokeWidth(u * (sky == Weather.DRIZZLE ? 0.035f : 0.05f));
                for (int i = 0; i < 3; i++) {
                    float x = w * (0.32f + 0.15f * i);
                    float y = h * 0.7f;
                    c.drawLine(x, y, x - u * 0.05f, y + u * 0.14f, p);
                }
            }
            if (sky == Weather.SNOW) {
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    c.drawCircle(w * (0.32f + 0.15f * i), h * (0.76f + (i % 2) * 0.06f), u * 0.04f, p);
                }
            }
            if (sky == Weather.STORM) {
                p.setColor(0xFFFFD54A);
                path.reset();
                float x = w * 0.5f;
                float y = h * 0.62f;
                path.moveTo(x, y);
                path.lineTo(x - u * 0.1f, y + u * 0.16f);
                path.lineTo(x, y + u * 0.16f);
                path.lineTo(x - u * 0.06f, y + u * 0.32f);
                path.lineTo(x + u * 0.1f, y + u * 0.1f);
                path.lineTo(x + u * 0.01f, y + u * 0.1f);
                path.lineTo(x + u * 0.06f, y);
                path.close();
                c.drawPath(path, p);
            }
            if (sky == Weather.FOG) {
                p.setColor(0xCCFFFFFF);
                p.setStrokeWidth(u * 0.05f);
                for (int i = 0; i < 2; i++) {
                    float y = h * (0.72f + 0.1f * i);
                    c.drawLine(w * 0.22f + i * u * 0.08f, y, w * 0.74f + i * u * 0.04f, y, p);
                }
            }
        }

        private void drawCloud(Canvas c, float cx, float cy, float u) {
            c.drawCircle(cx - u * 0.17f, cy + u * 0.03f, u * 0.13f, p);
            c.drawCircle(cx, cy - u * 0.06f, u * 0.19f, p);
            c.drawCircle(cx + u * 0.18f, cy + u * 0.02f, u * 0.14f, p);
            c.drawRect(cx - u * 0.17f, cy + u * 0.02f, cx + u * 0.18f, cy + u * 0.16f, p);
            c.drawCircle(cx - u * 0.17f, cy + u * 0.03f, u * 0.13f, p);
        }
    }

    // ---- The week's play -------------------------------------------------------------------

    /** How long was played each of the last seven days, as bars (today lit), and the total. */
    static final class WeekCard extends Card {
        private final TextView total;
        private final TextView sub;
        private final Bars bars;

        WeekCard(Context c, String kind) {
            super(c, kind, 290);
            FrameLayout l = layer();
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A2350, 0xFF120F24}));
            LinearLayout col = column(Gravity.TOP);
            col.setPadding(dp(16), dp(14), dp(16), dp(12));
            l.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            col.addView(caption(c.getString(R.string.pocket_widget_week)));
            total = text(22f, 0xFFFFFFFF, PocketFonts.display());
            col.addView(total, wrap(5));
            sub = text(11f, 0xB3FFFFFF, PocketFonts.medium());
            col.addView(sub, wrap(2));
            bars = new Bars(c);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            bl.topMargin = dp(8);
            col.addView(bars, bl);
            update();
        }

        @Override
        void update() {
            Context c = getContext();
            long[] days = PlayTime.lastDays(c, 7);
            long sum = 0;
            for (long s : days) {
                sum += s;
            }
            String t = PlayTime.format(c, sum);
            total.setText(t != null ? t : c.getString(R.string.pocket_widget_week_none));
            String today = PlayTime.format(c, days[0]);
            sub.setText(today != null ? c.getString(R.string.pocket_widget_week_today, today)
                    : c.getString(R.string.pocket_widget_week_today_none));
            bars.set(days);
        }
    }

    /** Seven bars, oldest on the left, today lit with a glow, the day's letter under each. */
    static final class Bars extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long[] days = new long[7];

        Bars(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            label.setColor(0x8CFFFFFF);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(PocketFonts.bold());
            label.setTextSize(9f * d);
        }

        void set(long[] days) {
            this.days = days;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int n = days.length;
            float labelH = 13 * d;
            float h = getHeight() - labelH;
            float gap = 8 * d;
            float bw = (getWidth() - gap * (n - 1)) / n;
            long max = 1;
            for (long s : days) {
                max = Math.max(max, s);
            }
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < n; i++) {
                int day = n - 1 - i; // oldest first
                float x = i * (bw + gap);
                float f = days[day] / (float) max;
                float bh = Math.max(3 * d, h * f);
                r.set(x, h - bh, x + bw, h);
                boolean today = day == 0;
                if (today) {
                    bar.setShader(new LinearGradient(0, h - bh, 0, h, 0xFFC08CFF, 0xFF6FB8FF, Shader.TileMode.CLAMP));
                    bar.setShadowLayer(8 * d, 0, 0, 0x99A98CFF);
                }
                else {
                    bar.setShader(null);
                    bar.clearShadowLayer();
                    bar.setColor(days[day] > 0 ? 0x66FFFFFF : 0x26FFFFFF);
                }
                c.drawRoundRect(r, bw / 2.5f, bw / 2.5f, bar);
                Calendar dc = (Calendar) cal.clone();
                dc.add(Calendar.DAY_OF_YEAR, -day);
                label.setColor(today ? 0xFFFFFFFF : 0x8CFFFFFF);
                c.drawText(DateFormat.format("EEEEE", dc).toString(), x + bw / 2, getHeight() - 2 * d, label);
            }
        }
    }

    // ---- PCs -----------------------------------------------------------------------------------

    /** The PCs: each with a dot (lit when it's on) and the game played on it last. */
    static final class PcsCard extends Card {
        private final LinearLayout rows;

        PcsCard(Context c, String kind) {
            super(c, kind, 280);
            FrameLayout l = layer();
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF173B3A, 0xFF0C1A1C}));
            LinearLayout col = column(Gravity.TOP);
            col.setPadding(dp(16), dp(14), dp(16), dp(12));
            l.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            col.addView(caption(c.getString(R.string.pocket_widget_pcs)));
            rows = column(Gravity.TOP);
            col.addView(rows);
            update();
        }

        @Override
        void update() {
            Context c = getContext();
            rows.removeAllViews();
            List<ComputerDetails> pcs = HomeStore.computers(c);
            if (pcs.isEmpty()) {
                TextView t = text(13f, 0xB3FFFFFF, PocketFonts.medium());
                t.setText(R.string.pocket_home_no_pcs);
                rows.addView(t, wrap(14));
                return;
            }
            for (int i = 0; i < pcs.size() && i < 3; i++) {
                ComputerDetails pc = pcs.get(i);
                boolean on = pc.state == ComputerDetails.State.ONLINE;
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout mark = new FrameLayout(c);
                ImageView g = new ImageView(c);
                g.setImageDrawable(PocketIcons.drawable("DESKTOP", dp(18), 0xFFFFFFFF));
                g.setScaleType(ImageView.ScaleType.CENTER);
                GradientDrawable gb = new GradientDrawable();
                gb.setCornerRadius(dp(10));
                gb.setColor(0x26FFFFFF);
                g.setBackground(gb);
                mark.addView(g, new FrameLayout.LayoutParams(dp(34), dp(34)));
                View dot = new View(c);
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(on ? 0xFF34C759 : 0xFF8E8E93);
                dd.setStroke(Math.round(2 * d), 0xFF0E1C1E);
                dot.setBackground(dd);
                FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.BOTTOM | Gravity.END);
                dl.rightMargin = -dp(2);
                dl.bottomMargin = -dp(2);
                mark.addView(dot, dl);
                mark.setClipChildren(false);
                row.addView(mark);
                LinearLayout words = column(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                wl.setMarginStart(dp(10));
                row.addView(words, wl);
                TextView name = text(13f, 0xFFFFFFFF, PocketFonts.semibold());
                name.setText(pc.name);
                words.addView(name);
                TextView last = text(11f, 0xA6FFFFFF, PocketFonts.regular());
                List<RecentGames.Game> g1 = RecentGames.forPc(c, pc.uuid, 1);
                String what = on ? c.getString(R.string.pocket_widget_pc_on) : c.getString(R.string.pocket_widget_pc_off);
                if (!g1.isEmpty() && g1.get(0).lastPlayed > 0) {
                    what += "  ·  " + g1.get(0).app.getAppName();
                }
                last.setText(what);
                words.addView(last, wrap(2));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = dp(i == 0 ? 12 : 9);
                rows.addView(row, rl);
            }
        }
    }
}
