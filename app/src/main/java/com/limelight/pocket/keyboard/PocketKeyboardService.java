package com.limelight.pocket.keyboard;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

/**
 * Pocket Nexus's keyboard for the controller, for every app (switch to it in Android's keyboard
 * settings): English and Arabic, typed with the D-pad or the stick and A, the way consoles do
 * it. X deletes, Y is a space, L1 / R1 move the cursor, L2 is shift, R2 the numbers and symbols,
 * Select the language, Start the field's own action (search, go, send...), B closes it. A
 * keyboard plugged in still types as usual.
 */
public class PocketKeyboardService extends InputMethodService implements KeyboardView.Listener {

    public static final String KEY_LANG = "pocket_kb_lang";
    public static final String KEY_SIZE = "pocket_kb_size";
    public static final int[] SIZES = {32, 38, 44};

    /**
     * Its settings. The keyboard runs in a process of its own (so typing into Pocket Nexus's own
     * screens never waits on itself), so they're read fresh from the file each time.
     */
    @SuppressWarnings("deprecation")
    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS);
    }

    /** The keys' height in dp (small, medium, large). */
    public static int keySizeDp(Context c) {
        int i = prefs(c).getInt(KEY_SIZE, 1);
        return SIZES[Math.max(0, Math.min(SIZES.length - 1, i))];
    }

    /** True when the keyboard is on in Android's settings (it can be picked). */
    public static boolean enabled(Context c) {
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getPackageName().equals(c.getPackageName())) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private KeyboardView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int stickX;
    private int stickY;
    private final Runnable stickRepeat = new Runnable() {
        @Override
        public void run() {
            if (view == null || (stickX == 0 && stickY == 0)) {
                return;
            }
            if (stickX != 0) {
                view.moveH(stickX);
            }
            if (stickY != 0) {
                view.moveV(stickY);
            }
            click(AudioManager.FX_FOCUS_NAVIGATION_UP);
            main.postDelayed(this, 120);
        }
    };

    @Override
    public View onCreateInputView() {
        view = new KeyboardView(this, this);
        return view;
    }

    /** Never the full screen editor: the field shows in the keyboard's own top line. */
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (view == null) {
            return;
        }
        view.setEnterLabel(enterLabel(info));
        view.setPlaceholder(info != null ? info.hintText : null);
        // A sentence starts with a capital where the field asks for it
        boolean caps = info != null && (info.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0;
        view.setShift(caps && before(1).length() == 0);
        refreshPreview();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        stickX = 0;
        stickY = 0;
        main.removeCallbacks(stickRepeat);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        refreshPreview();
    }

    private String enterLabel(EditorInfo info) {
        if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return "";
        }
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO:
                return getString(R.string.pocket_kb_go);
            case EditorInfo.IME_ACTION_SEARCH:
                return getString(R.string.pocket_kb_search);
            case EditorInfo.IME_ACTION_SEND:
                return getString(R.string.pocket_kb_send);
            case EditorInfo.IME_ACTION_NEXT:
                return getString(R.string.pocket_kb_next);
            case EditorInfo.IME_ACTION_DONE:
                return getString(R.string.pocket_kb_done);
            default:
                return "";
        }
    }

    private CharSequence before(int n) {
        InputConnection ic = getCurrentInputConnection();
        CharSequence s = ic != null ? ic.getTextBeforeCursor(n, 0) : null;
        return s != null ? s : "";
    }

    private boolean isPassword() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return false;
        }
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        return (cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private void refreshPreview() {
        if (view == null) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            view.setPreview("", "", false);
            return;
        }
        CharSequence b = ic.getTextBeforeCursor(64, 0);
        CharSequence a = ic.getTextAfterCursor(32, 0);
        if (isPassword()) {
            // A password shows as dots here too
            b = b != null ? dots(b.length()) : "";
            a = a != null ? dots(a.length()) : "";
        }
        view.setPreview(b, a, true);
    }

    private static String dots(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) {
            s.append('•');
        }
        return s.toString();
    }

    private void click(int effect) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.playSoundEffect(effect, 0.35f);
        }
    }

    // ---- Keys ---------------------------------------------------------------------------

    @Override
    public void onKey(KeyboardView.Key key) {
        try {
            type(key);
        } catch (RuntimeException e) {
            // A field that went away mid-press: nothing to do (never a crash)
        }
    }

    private void type(KeyboardView.Key key) {
        InputConnection ic = getCurrentInputConnection();
        switch (key.type) {
            case KeyboardView.CHAR:
                if (ic != null) {
                    ic.commitText(key.text, 1);
                }
                view.typed();
                click(AudioManager.FX_KEYPRESS_STANDARD);
                break;
            case KeyboardView.SPACE:
                if (ic != null) {
                    ic.commitText(" ", 1);
                }
                click(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;
            case KeyboardView.DELETE:
                delete();
                break;
            case KeyboardView.ENTER:
                enter();
                break;
            case KeyboardView.SHIFT:
                view.toggleShift();
                click(AudioManager.FX_KEYPRESS_STANDARD);
                break;
            case KeyboardView.SYMBOLS:
                view.toggleSymbols();
                click(AudioManager.FX_KEYPRESS_STANDARD);
                break;
            case KeyboardView.LANGUAGE:
                switchLanguage();
                break;
            case KeyboardView.LEFT:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case KeyboardView.RIGHT:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            default:
                break;
        }
        view.press();
        refreshPreview();
    }

    private void delete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        }
        else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
        click(AudioManager.FX_KEYPRESS_DELETE);
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        click(AudioManager.FX_KEYPRESS_RETURN);
        if (ic == null) {
            return;
        }
        int action = info != null ? info.imeOptions & EditorInfo.IME_MASK_ACTION : EditorInfo.IME_ACTION_NONE;
        boolean noAction = info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (noAction) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        else {
            ic.performEditorAction(action);
        }
    }

    private void switchLanguage() {
        String next = "ar".equals(view.language()) ? "en" : "ar";
        view.setLanguage(next);
        prefs(this).edit().putString(KEY_LANG, next).apply();
        click(AudioManager.FX_KEYPRESS_STANDARD);
    }

    private static boolean fromController(KeyEvent e) {
        int s = e.getSource();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || KeyEvent.isGamepadButton(e.getKeyCode());
    }

    /** The controller's buttons while the keyboard shows (a real keyboard's keys pass on). */
    private boolean ours(int keyCode, KeyEvent e) {
        if (view == null || !isInputViewShown()) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return fromController(e);
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!ours(keyCode, event)) {
            return super.onKeyDown(keyCode, event);
        }
        try {
            return button(keyCode, event);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private boolean button(int keyCode, KeyEvent event) {
        boolean first = event.getRepeatCount() == 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                view.moveV(-1);
                click(AudioManager.FX_FOCUS_NAVIGATION_UP);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                view.moveV(1);
                click(AudioManager.FX_FOCUS_NAVIGATION_DOWN);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                view.moveH(-1);
                click(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                view.moveH(1);
                click(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER: {
                KeyboardView.Key k = view.selected();
                if (k != null && (first || k.type == KeyboardView.CHAR || k.type == KeyboardView.DELETE
                        || k.type == KeyboardView.SPACE || k.type == KeyboardView.LEFT || k.type == KeyboardView.RIGHT)) {
                    onKey(k);
                }
                break;
            }
            case KeyEvent.KEYCODE_BUTTON_X:
                delete();
                view.press();
                refreshPreview();
                break;
            case KeyEvent.KEYCODE_BUTTON_Y: {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(" ", 1);
                }
                click(AudioManager.FX_KEYPRESS_SPACEBAR);
                refreshPreview();
                break;
            }
            case KeyEvent.KEYCODE_BUTTON_L1:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                if (first) {
                    view.toggleShift();
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                if (first) {
                    view.toggleSymbols();
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (first) {
                    switchLanguage();
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
                if (first) {
                    enter();
                    refreshPreview();
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                if (first) {
                    requestHideSelf(0);
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (ours(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** The left stick moves round the keys too (and keeps moving while it's held). */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (view == null || !isInputViewShown()
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return super.onGenericMotionEvent(event);
        }
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f) {
            // The D-pad as a hat: its key presses come too (handled there)
            return true;
        }
        int nx = x > 0.6f ? 1 : x < -0.6f ? -1 : 0;
        int ny = y > 0.6f ? 1 : y < -0.6f ? -1 : 0;
        if (Math.abs(x) > Math.abs(y)) {
            ny = 0;
        }
        else {
            nx = 0;
        }
        if (nx != stickX || ny != stickY) {
            stickX = nx;
            stickY = ny;
            main.removeCallbacks(stickRepeat);
            if (nx != 0 || ny != 0) {
                stickRepeat.run();
                main.removeCallbacks(stickRepeat);
                main.postDelayed(stickRepeat, 320);
            }
        }
        return true;
    }
}
