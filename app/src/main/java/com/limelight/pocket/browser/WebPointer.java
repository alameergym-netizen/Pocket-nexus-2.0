package com.limelight.pocket.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketTheme;

/**
 * A controller's pointer over a web page (a web client: Discord, YouTube Music, Spotify): the
 * left stick or the D-pad moves it (faster the further it's pushed, slower over something to
 * click), the right stick scrolls what's under it like a mouse wheel, A clicks. What's under it
 * that can be clicked gets a glowing outline that glides from one thing to the next.
 */
public final class WebPointer extends View {

    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF box = new RectF();
    private RectF goal;
    private float boxAlpha;
    private android.animation.ValueAnimator boxAnim;
    private float ring;
    private float px = -1;
    private float py = -1;
    private float stickX;
    private float stickY;
    private float scrollX;
    private float scrollY;
    private int dpadX;
    private int dpadY;
    private long lastFrame;
    private long lastProbe;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float d = getResources().getDisplayMetrics().density;
            float vx = stickX + dpadX;
            float vy = stickY + dpadY;
            boolean moving = Math.abs(vx) > 0.01f || Math.abs(vy) > 0.01f;
            boolean scrolling = Math.abs(scrollX) > 0.01f || Math.abs(scrollY) > 0.01f;
            if (moving) {
                float mag = (float) Math.min(1.0, Math.hypot(vx, vy));
                float speed = (220 + 900 * mag * mag) * d * (onTarget() ? 0.62f : 1f);
                move(vx / Math.max(0.0001f, mag) * mag * speed * dt, vy / Math.max(0.0001f, mag) * mag * speed * dt);
            }
            if (scrolling) {
                wheel(scrollX * 1.6f * dt * 60f, scrollY * 1.6f * dt * 60f);
            }
            if ((moving || scrolling) && now - lastProbe > 90) {
                lastProbe = now;
                probe();
            }
            if (moving || scrolling) {
                main.postDelayed(this, 16);
            }
            else {
                lastFrame = 0;
                main.postDelayed(WebPointer.this::probe, 40);
            }
        }
    };

    public WebPointer(Context c, WebView web) {
        super(c);
        this.web = web;
        float d = c.getResources().getDisplayMetrics().density;
        fill.setColor(0xFFFFFFFF);
        edge.setColor(0xFF111114);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.6f * d);
        edge.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (px < 0) {
            px = w / 2f;
            py = h / 2f;
        }
    }

    // ---- Input -----------------------------------------------------------------------------

    /** The sticks (and a D-pad that reports as a hat): true when taken. */
    public boolean onMotion(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || ev.getAction() != MotionEvent.ACTION_MOVE) {
            return false;
        }
        stickX = dead(ev.getAxisValue(MotionEvent.AXIS_X));
        stickY = dead(ev.getAxisValue(MotionEvent.AXIS_Y));
        scrollX = dead(ev.getAxisValue(MotionEvent.AXIS_Z));
        scrollY = dead(ev.getAxisValue(MotionEvent.AXIS_RZ));
        float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
        dpadX = hx > 0.5f ? 1 : hx < -0.5f ? -1 : 0;
        dpadY = hy > 0.5f ? 1 : hy < -0.5f ? -1 : 0;
        kick();
        return true;
    }

    /** The D-pad moves it, A clicks: true when taken. */
    public boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int vx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                int vy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                if (down) {
                    if (vx != 0) {
                        dpadX = vx;
                    }
                    if (vy != 0) {
                        dpadY = vy;
                    }
                }
                else if (up) {
                    if (vx != 0) {
                        dpadX = 0;
                    }
                    if (vy != 0) {
                        dpadY = 0;
                    }
                }
                kick();
                return true;
            }
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (up) {
                    click();
                }
                return true;
            default:
                return false;
        }
    }

    private static float dead(float v) {
        float a = Math.abs(v);
        if (a < 0.18f) {
            return 0f;
        }
        return Math.signum(v) * (a - 0.18f) / 0.82f;
    }

    private void kick() {
        main.removeCallbacks(frame);
        main.post(frame);
    }

    // ---- What it does ----------------------------------------------------------------------

    private void move(float dx, float dy) {
        float w = getWidth();
        float h = getHeight();
        float edgeRoom = 24 * getResources().getDisplayMetrics().density;
        px += dx;
        py += dy;
        // At the top or the bottom the page scrolls under it
        if ((py > h - edgeRoom && dy > 0) || (py < edgeRoom && dy < 0)) {
            wheel(0, dy * 0.06f);
        }
        px = Math.max(0, Math.min(w - 1, px));
        py = Math.max(0, Math.min(h - 1, py));
        invalidate();
    }

    /** Scrolls what's under the pointer (a list in the page, or the page), like a mouse wheel. */
    private void wheel(float dx, float dy) {
        MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        coords[0].x = px;
        coords[0].y = py;
        coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, -dy);
        coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, dx);
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, px, py, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        PocketSounds.play(getContext(), PocketSounds.SELECT);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(320);
        a.addUpdateListener(v -> {
            ring = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
        web.requestFocus();
    }

    private boolean onTarget() {
        return goal != null && goal.contains(px, py);
    }

    /** What's under the pointer that can be clicked (a link, a button, a box): lit around. */
    private void probe() {
        @SuppressWarnings("deprecation")
        final float scale = Math.max(0.1f, web.getScale());
        float x = px / scale;
        float y = py / scale;
        String js = "(function(x,y){var e=document.elementFromPoint(x,y),n=0;while(e&&n<7){var t=e.tagName;"
                + "if(t=='A'||t=='BUTTON'||t=='INPUT'||t=='SELECT'||t=='TEXTAREA'||t=='LABEL'||t=='SUMMARY'||e.onclick"
                + "||(e.getAttribute&&(e.getAttribute('role')=='button'||e.getAttribute('role')=='link'"
                + "||e.getAttribute('role')=='tab'||e.getAttribute('role')=='option'||e.getAttribute('role')=='menuitem'"
                + "||e.getAttribute('contenteditable')=='true'||e.getAttribute('onclick'))))break;e=e.parentElement;n++;}"
                + "if(!e||n>=7||e===document.body||e===document.documentElement)return '';"
                + "var r=e.getBoundingClientRect();if(r.width>window.innerWidth*0.9&&r.height>window.innerHeight*0.5)return '';"
                + "return r.left+','+r.top+','+r.width+','+r.height;})(" + x + "," + y + ")";
        try {
            web.evaluateJavascript(js, value -> {
                String v = value != null ? value.replace("\"", "") : "";
                String[] p = v.split(",");
                if (p.length != 4) {
                    target(null);
                    return;
                }
                try {
                    float l = Float.parseFloat(p[0]) * scale;
                    float t = Float.parseFloat(p[1]) * scale;
                    float w = Float.parseFloat(p[2]) * scale;
                    float h = Float.parseFloat(p[3]) * scale;
                    target(new RectF(l, t, l + w, t + h));
                } catch (NumberFormatException e) {
                    target(null);
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void target(RectF r) {
        if (r == null && goal == null) {
            return;
        }
        if (r != null && goal != null && Math.abs(r.left - goal.left) < 1 && Math.abs(r.top - goal.top) < 1
                && Math.abs(r.width() - goal.width()) < 1) {
            return;
        }
        if (r != null && goal == null) {
            PocketSounds.play(getContext(), PocketSounds.NAV);
        }
        final RectF from = new RectF(boxAlpha > 0.05f ? box : (r != null ? r : box));
        final float fromAlpha = boxAlpha;
        final RectF to = r != null ? r : new RectF(box);
        final float toAlpha = r != null ? 1f : 0f;
        goal = r;
        if (boxAnim != null) {
            boxAnim.cancel();
        }
        boxAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        boxAnim.setDuration(150);
        boxAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        boxAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            box.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t);
            boxAlpha = fromAlpha + (toAlpha - fromAlpha) * t;
            invalidate();
        });
        boxAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        if (boxAlpha > 0.01f) {
            int accent = PocketTheme.accent(getContext());
            float pad = 4 * d;
            RectF r = new RectF(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad);
            lit.setStyle(Paint.Style.FILL);
            lit.setColor((accent & 0x00FFFFFF) | (Math.round(36 * boxAlpha) << 24));
            c.drawRoundRect(r, 9 * d, 9 * d, lit);
            lit.setStyle(Paint.Style.STROKE);
            lit.setStrokeWidth(2.2f * d);
            lit.setColor((accent & 0x00FFFFFF) | (Math.round(230 * boxAlpha) << 24));
            c.drawRoundRect(r, 9 * d, 9 * d, lit);
        }
        if (ring > 0) {
            ringPaint.setColor((PocketTheme.accent(getContext()) & 0x00FFFFFF) | (Math.round(140 * ring) << 24));
            c.drawCircle(px, py, (1.6f - ring) * 18 * d, ringPaint);
        }
        float s = d * 1.1f;
        arrow.reset();
        arrow.moveTo(px, py);
        arrow.lineTo(px, py + 18 * s);
        arrow.lineTo(px + 4.6f * s, py + 13.6f * s);
        arrow.lineTo(px + 8 * s, py + 20.6f * s);
        arrow.lineTo(px + 10.6f * s, py + 19.4f * s);
        arrow.lineTo(px + 7.2f * s, py + 12.6f * s);
        arrow.lineTo(px + 13 * s, py + 12.6f * s);
        arrow.close();
        c.drawPath(arrow, fill);
        c.drawPath(arrow, edge);
    }
}
