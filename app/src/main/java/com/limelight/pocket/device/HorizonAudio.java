package com.limelight.pocket.device;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.StreamMenuView;
import java.util.ArrayList;
import java.util.List;

/** Android-visible audio endpoints and supported media controls. No system DSP is installed. */
public final class HorizonAudio {
    private HorizonAudio() {}

    static boolean supportsToggle(long actions) {
        return (actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    /** Re-check capabilities at press time; the session may have changed since opening a panel. */
    static void transport(ConsoleActivity a, MediaController mc, int direction) {
        try {
            PlaybackState state = mc.getPlaybackState();
            if (state == null) { return; }
            long actions = state.getActions();
            MediaController.TransportControls controls = mc.getTransportControls();
            if (direction > 0 && (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                controls.skipToNext();
            } else if (direction < 0 && (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
                controls.skipToPrevious();
            } else if (direction == 0) {
                boolean playing = state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING;
                if (playing && (actions & PlaybackState.ACTION_PAUSE) != 0) {
                    controls.pause();
                } else if (!playing && (actions & PlaybackState.ACTION_PLAY) != 0) {
                    controls.play();
                } else if ((actions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
                    mc.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    mc.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                } else { return; }
            } else { return; }
            PocketSounds.play(a, PocketSounds.SELECT);
        } catch (RuntimeException e) {
            Toast.makeText(a, R.string.horizon_audio_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    static int typeLabel(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return R.string.horizon_audio_speaker;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return R.string.horizon_audio_earpiece;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return R.string.horizon_audio_wired;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return R.string.horizon_audio_bluetooth;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return R.string.horizon_audio_usb;
            case AudioDeviceInfo.TYPE_HDMI:
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return R.string.horizon_audio_hdmi;
            default:
                return R.string.horizon_audio_other;
        }
    }

    public static void show(ConsoleActivity a) {
        a.showConsolePanel(a.getString(R.string.horizon_audio_title), null, null, items(a));
    }

    public static List<StreamMenuView.Item> items(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_audio_outputs)));
        boolean found = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    for (AudioDeviceInfo output : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                        CharSequence name = output.getProductName();
                        rows.add(StreamMenuView.Item.info(a.getString(typeLabel(output.getType())),
                                name == null || name.length() == 0
                                        ? a.getString(R.string.horizon_audio_unavailable) : name.toString()));
                        found = true;
                    }
                }
            } catch (RuntimeException ignored) {}
        }
        if (!found) { rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_unavailable))); }
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_snapshot)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_route_title),
                () -> showStreamRoute(a)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_buds_title),
                () -> showBluetooth(a)));
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_audio_fx_title)));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_audio_fx_title),
                a.getString(com.limelight.pocket.PocketPrefs.prefs(a)
                        .getBoolean("checkbox_enable_audiofx", false) ? R.string.pocket_on : R.string.pocket_off)));
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_fx_sub)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_audio_effects_inventory), () -> showEffects(a)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_audio_sound_settings),
                () -> launch(a, new Intent(Settings.ACTION_SOUND_SETTINGS))));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_audio_bluetooth_settings),
                () -> launch(a, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        // Huawei AI Life is an optional official-app handoff, not an ANC or EQ protocol.
        try {
            Intent aiLife = a.getPackageManager().getLaunchIntentForPackage("com.huawei.smarthome");
            if (aiLife != null) {
                rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_audio_ai_life),
                        () -> launch(a, aiLife)));
            }
        } catch (RuntimeException ignored) {}
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_limit)));
        return rows;
    }

    private static void showStreamRoute(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = streamRouteRows(a);
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_bridge_refresh), () -> showStreamRoute(a)));
        a.showConsolePanel(a.getString(R.string.horizon_route_title), null, null, rows);
    }

    public static List<StreamMenuView.Item> streamRouteRows(Context a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        com.limelight.binding.audio.HorizonAudioRoute.Snapshot s =
                com.limelight.binding.audio.HorizonAudioRoute.snapshot();
        if (s == null) {
            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_route_inactive)));
        } else {
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_actual),
                    s.type < 0 ? a.getString(R.string.horizon_audio_unavailable)
                            : a.getString(typeLabel(s.type)) + (s.name.isEmpty() ? "" : " · " + s.name)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_format),
                    s.sampleRate + " Hz · " + a.getString(R.string.horizon_route_channels,s.channels)));
            double ms = com.limelight.binding.audio.HorizonAudioRoute.bufferMs(s.bufferFrames,s.sampleRate);
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_buffer),
                    ms < 0 ? a.getString(R.string.horizon_audio_unavailable)
                            : a.getString(R.string.horizon_route_ms,ms)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_audio_backlog),
                    s.queuedAudioMs < 0 ? a.getString(R.string.horizon_audio_unavailable)
                            : a.getString(R.string.horizon_route_ms, s.queuedAudioMs)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_underruns),
                    s.underruns < 0 ? a.getString(R.string.horizon_audio_unavailable) : Integer.toString(s.underruns)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_queue_peak),
                    s.delivery.highestQueueMs < 0 ? a.getString(R.string.horizon_audio_unavailable)
                            : a.getString(R.string.horizon_pc_ms, s.delivery.highestQueueMs)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_dropped),
                    Long.toString(s.delivery.droppedBlocks)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_partial),
                    Long.toString(s.delivery.partialWrites)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_errors),
                    Long.toString(s.delivery.writeErrors)));
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_route_mode),
                    a.getString(s.performanceMode < 0 ? R.string.horizon_audio_unavailable
                            : s.performanceMode == 1 ? R.string.horizon_route_low_latency
                            : s.performanceMode == 2 ? R.string.horizon_route_power_saving
                            : R.string.horizon_route_standard)));
        }
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_route_note)));
        return rows;
    }

    private static void showBluetooth(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_buds_note)));
        boolean found = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    for (AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                        int t = device.getType();
                        if (t != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                && t != AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                && t != AudioDeviceInfo.TYPE_BLE_HEADSET
                                && t != AudioDeviceInfo.TYPE_BLE_SPEAKER
                                && t != AudioDeviceInfo.TYPE_BLE_BROADCAST) { continue; }
                        CharSequence raw = device.getProductName();
                        String name = raw == null || raw.length() == 0
                                ? a.getString(R.string.horizon_audio_unavailable) : raw.toString();
                        String transport = t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ? "A2DP"
                                : t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ? "SCO" : "Bluetooth LE";
                        rows.add(StreamMenuView.Item.header(name));
                        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_buds_transport),transport));
                        if (HorizonBudsPolicy.matches7i(name)) {
                            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_buds_name_match)));
                        }
                        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_buds_rates),
                                capability(a,device.getSampleRates())));
                        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_buds_channels),
                                capability(a,device.getChannelCounts())));
                        found = true;
                    }
                }
            } catch (RuntimeException ignored) {}
        }
        if (!found) { rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_unavailable))); }
        try {
            Intent libre = a.getPackageManager().getLaunchIntentForPackage("io.github.librebuds");
            if (libre != null) {
                rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_buds_open_libre),() -> launch(a,libre)));
            } else {
                rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_buds_libre_missing)));
            }
        } catch (RuntimeException ignored) {}
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_audio_bluetooth_settings),
                () -> launch(a,new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_bridge_refresh),() -> showBluetooth(a)));
        a.showConsolePanel(a.getString(R.string.horizon_buds_title),null,null,rows);
    }

    private static String capability(Context c, int[] values) {
        String result = HorizonBudsPolicy.positiveValues(values);
        return result.isEmpty() ? c.getString(R.string.horizon_audio_unavailable) : result;
    }

    private static void showEffects(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_effects_note)));
        try {
            android.media.audiofx.AudioEffect.Descriptor[] effects = android.media.audiofx.AudioEffect.queryEffects();
            if (effects != null) {
                int shown = 0;
                for (android.media.audiofx.AudioEffect.Descriptor effect : effects) {
                    if (effect == null || shown >= 64) { continue; }
                    String name = effect.name == null || effect.name.trim().isEmpty()
                            ? a.getString(R.string.horizon_audio_unavailable) : effect.name;
                    String maker = effect.implementor == null ? "" : effect.implementor;
                    rows.add(StreamMenuView.Item.info(name,maker));
                    shown++;
                }
            }
        } catch (RuntimeException ignored) {}
        if (rows.size() == 1) { rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_audio_unavailable))); }
        a.showConsolePanel(a.getString(R.string.horizon_audio_effects_inventory),null,null,rows);
    }

    private static void launch(ConsoleActivity a, Intent intent) {
        try { a.startActivity(intent); }
        catch (RuntimeException e) {
            Toast.makeText(a, R.string.horizon_audio_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
