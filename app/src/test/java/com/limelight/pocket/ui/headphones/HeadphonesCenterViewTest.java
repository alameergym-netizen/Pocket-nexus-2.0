package com.limelight.pocket.ui.headphones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.limelight.R;
import com.limelight.pocket.ui.HorizonLook;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Cell;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Level;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Mode;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Phase;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.State;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The Center on a Retroid Pocket 6-sized landscape screen: fit, focus, words and contrast. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class, qualifiers = "w640dp-h360dp-land-xhdpi")
public class HeadphonesCenterViewTest {
    private Activity activity;
    private HeadphonesCenterView view;
    private final List<Object> asked = new ArrayList<>();

    private static State live(Mode mode, Level level) {
        return new State(Phase.LIVE, "HUAWEI FreeBuds 7i", new Cell(82, false), new Cell(79, false),
                new Cell(40, true), mode, level, null, android.os.SystemClock.elapsedRealtime());
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new HeadphonesCenterView(activity, true);
        view.setListener(new HeadphonesCenterView.Listener() {
            public void onMode(Mode mode) { asked.add(mode); }
            public void onLevel(Level level) { asked.add(level); }
            public void onRefresh() { asked.add("refresh"); }
            public void onChooseDevice() { asked.add("choose"); }
            public void onBluetoothSettings() { asked.add("bluetooth"); }
            public void onAllowNearbyDevices() { asked.add("allow"); }
        });
        FrameLayout root = new FrameLayout(activity);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        activity.setContentView(root);
    }

    private int heightDp() {
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(600 * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
        return Math.round(view.getMeasuredHeight() / density);
    }

    private TextView v(int id) {
        TextView t = view.findViewById(id);
        assertNotNull(t);
        return t;
    }

    private static String state(View v) {
        CharSequence s = ViewCompat.getStateDescription(v);
        return s == null ? "" : s.toString();
    }

    @Test
    public void everythingFitsALandscapeHandheldWithoutScrolling() {
        view.bind(live(Mode.ANC, Level.BALANCED));
        int closed = heightDp();
        assertTrue("closed Center is " + closed + " dp tall", closed <= 280);
        v(R.id.horizon_hp_levels).performClick();
        int open = heightDp();
        assertTrue("with levels open the Center is " + open + " dp tall", open <= 320);
    }

    @Test
    public void activeMarksComeFromTheReadbackAndAreSpoken() {
        view.bind(live(Mode.ANC, Level.DEEP));
        TextView anc = v(R.id.horizon_hp_mode_anc);
        TextView off = v(R.id.horizon_hp_mode_off);
        assertTrue(anc.isSelected());
        assertEquals(activity.getString(R.string.horizon_hp_state_active), state(anc));
        assertEquals(activity.getString(R.string.horizon_hp_state_off), state(off));
        assertTrue(v(R.id.horizon_hp_level_deep).isSelected());
        assertTrue(v(R.id.horizon_hp_levels).getText().toString().contains("Deep"));
        assertEquals("Case, 40 percent, charging", view.findViewById(R.id.horizon_hp_case).getContentDescription());

        view.bind(new State(Phase.UNREACHABLE, "HUAWEI FreeBuds 7i", null, null, null, Mode.ANC, Level.DEEP, null, 0));
        assertTrue("an unanswered read marks nothing", !anc.isSelected());
        assertEquals(activity.getString(R.string.horizon_hp_state_unread), state(off));
        assertEquals("Left, not reported", view.findViewById(R.id.horizon_hp_left).getContentDescription());
        assertEquals(activity.getString(R.string.horizon_hp_phase_unreachable), v(R.id.horizon_hp_status).getText());
    }

    @Test
    public void commandsAreOnlyAskedFromAVerifiedStateAndNeverRepeated() {
        view.bind(new State(Phase.READING, "Buds", null, null, null, null, null, null, 0));
        v(R.id.horizon_hp_mode_off).performClick();
        assertTrue("nothing read yet", asked.isEmpty());

        view.bind(live(Mode.AWARENESS, null));
        v(R.id.horizon_hp_mode_awareness).performClick();
        assertTrue("already active", asked.isEmpty());
        v(R.id.horizon_hp_mode_off).performClick();
        assertEquals(Mode.OFF, asked.get(0));

        view.bind(new State(Phase.APPLYING, "Buds", null, null, null, Mode.AWARENESS, null, Mode.OFF, 1));
        assertTrue(v(R.id.horizon_hp_mode_off).isActivated());
        v(R.id.horizon_hp_mode_anc).performClick();
        assertEquals("one command at a time", 1, asked.size());

        view.bind(live(Mode.ANC, Level.LIGHT));
        v(R.id.horizon_hp_levels).performClick();
        v(R.id.horizon_hp_level_deep).performClick();
        assertEquals(Level.DEEP, asked.get(1));
    }

    @Test
    public void theDpadWalksRowsLeftToRightThenDown() {
        view.bind(live(Mode.ANC, Level.BALANCED));
        heightDp();
        View awareness = v(R.id.horizon_hp_mode_awareness);
        assertEquals(R.id.horizon_hp_mode_off, awareness.focusSearch(View.FOCUS_RIGHT).getId());
        assertEquals(R.id.horizon_hp_mode_anc,
                v(R.id.horizon_hp_mode_off).focusSearch(View.FOCUS_RIGHT).getId());
        assertEquals(R.id.horizon_hp_levels, v(R.id.horizon_hp_mode_anc).focusSearch(View.FOCUS_DOWN).getId());

        v(R.id.horizon_hp_levels).requestFocus();
        v(R.id.horizon_hp_levels).performClick();
        assertTrue(view.levelsOpen());
        assertTrue("opening the levels focuses the active one", v(R.id.horizon_hp_level_balanced).isFocused());
        assertEquals(activity.getString(R.string.horizon_hp_expanded), state(v(R.id.horizon_hp_levels)));
    }

    @Test
    public void permissionShowsOnlyTheWayForward() {
        view.bind(new State(Phase.PERMISSION, null, null, null, null, null, null, null, 0));
        assertEquals(View.VISIBLE, v(R.id.horizon_hp_allow).getVisibility());
        assertEquals(View.GONE, v(R.id.horizon_hp_refresh).getVisibility());
        assertEquals(activity.getString(R.string.horizon_hp_no_device), v(R.id.horizon_hp_device).getText());
        v(R.id.horizon_hp_allow).performClick();
        assertEquals("allow", asked.get(0));
    }

    @Test
    public void everyThemesAccentKeepsActiveTextReadable() {
        for (PocketTheme.Preset p : PocketTheme.THEMES) {
            double l = HorizonLook.luminance(p.accent);
            double contrast = (l + 0.05) / 0.05;
            assertTrue(activity.getString(p.name) + " gives black text " + contrast + ":1", contrast >= 4.5);
        }
    }
}
