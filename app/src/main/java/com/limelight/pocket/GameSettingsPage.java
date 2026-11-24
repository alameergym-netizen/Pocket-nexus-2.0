package com.limelight.pocket;

import android.content.Context;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.core.HorizonGamePreset;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

/**
 * A game's own stream settings (bitrate, frame rate, resolution, frame generation), as a page of
 * the console panel. The same page everywhere a game is: the library, the home screen's row and
 * the PC games.
 */
public final class GameSettingsPage {
    private GameSettingsPage() {}

    /** onReset: runs after "back to the normal settings" (may be null). */
    public static List<StreamMenuView.Item> build(final Context ctx, final String uuid, final int appId,
                                                  final String gameName, final Runnable onReset) {
        List<StreamMenuView.Item> page = new ArrayList<>();

        if (ctx instanceof ConsoleActivity && uuid != null && !uuid.trim().isEmpty() && appId != 0) {
            ConsoleActivity console = (ConsoleActivity) ctx;
            String title = ctx.getString(R.string.horizon_game_presets);
            HorizonGamePreset current = HorizonGamePreset.match(
                    GameProfile.resolution(ctx, uuid, appId), GameProfile.fps(ctx, uuid, appId),
                    GameProfile.bitrateKbps(ctx, uuid, appId));
            page.add(StreamMenuView.Item.submenu(title, () -> {
                List<StreamMenuView.Item> choices = new ArrayList<>();
                choices.add(StreamMenuView.Item.note(ctx.getString(R.string.horizon_game_presets_note)));
                for (HorizonGamePreset preset : HorizonGamePreset.values()) {
                    choices.add(StreamMenuView.Item.confirm(ctx.getString(presetLabel(preset)), () -> {
                        if (GameProfile.applyPreset(ctx, uuid, appId, preset)) {
                            Toast.makeText(ctx, R.string.horizon_game_preset_saved, Toast.LENGTH_SHORT).show();
                        }
                    }).withValue(preset.description()));
                }
                console.pushConsolePage(title, choices);
            }).withValue(current == null ? null : ctx.getString(presetLabel(current))));
        }

        page.add(StreamMenuView.Item.value(ctx.getString(R.string.pocket_sm_bitrate),
                bitrateLabel(ctx, GameProfile.bitrateKbps(ctx, uuid, appId)), direction -> {
                    int[] values = GameProfile.BITRATES_MBPS;
                    int i = GameProfile.indexOf(values, GameProfile.bitrateKbps(ctx, uuid, appId) / 1000);
                    i = Math.max(0, Math.min(values.length - 1, i + direction));
                    GameProfile.setBitrateKbps(ctx, uuid, appId, values[i] * 1000);
                    return bitrateLabel(ctx, values[i] * 1000);
                }));

        page.add(StreamMenuView.Item.value(ctx.getString(R.string.pocket_sm_fps),
                fpsLabel(ctx, GameProfile.fps(ctx, uuid, appId)), direction -> {
                    int[] values = GameProfile.FPS;
                    int i = GameProfile.indexOf(values, GameProfile.fps(ctx, uuid, appId));
                    i = Math.max(0, Math.min(values.length - 1, i + direction));
                    GameProfile.setFps(ctx, uuid, appId, values[i]);
                    return fpsLabel(ctx, values[i]);
                }));

        page.add(StreamMenuView.Item.value(ctx.getString(R.string.pocket_gs_resolution),
                resolutionLabel(ctx, GameProfile.resolution(ctx, uuid, appId)), direction -> {
                    String[] values = GameProfile.RESOLUTIONS;
                    int i = GameProfile.indexOf(values, GameProfile.resolution(ctx, uuid, appId));
                    i = Math.max(0, Math.min(values.length - 1, i + direction));
                    GameProfile.setResolution(ctx, uuid, appId, values[i]);
                    return resolutionLabel(ctx, values[i]);
                }));

        page.add(StreamMenuView.Item.value(ctx.getString(R.string.pocket_sm_framegen),
                frameGenLabel(ctx, GameProfile.frameGen(ctx, uuid, appId)), direction -> {
                    int mode = GameProfile.frameGen(ctx, uuid, appId);
                    int n = GameProfile.FRAME_GEN_MODES;
                    mode = (mode + (direction > 0 ? 1 : n - 1)) % n;
                    GameProfile.setFrameGen(ctx, uuid, appId, mode);
                    return frameGenLabel(ctx, mode);
                }));

        page.add(StreamMenuView.Item.action(ctx.getString(R.string.pocket_gs_reset), () -> {
            GameProfile.clear(ctx, uuid, appId);
            Toast.makeText(ctx, R.string.pocket_gs_reset_done, Toast.LENGTH_SHORT).show();
            if (onReset != null) {
                onReset.run();
            }
        }));
        page.add(StreamMenuView.Item.repeatable(ctx.getString(R.string.pocket_gs_note, gameName), null));
        return page;
    }

    private static int presetLabel(HorizonGamePreset preset) {
        switch (preset) {
            case REMOTE_SAVER: return R.string.horizon_game_remote_saver;
            case REMOTE_BALANCED: return R.string.horizon_game_remote_balanced;
            case REMOTE_HIGH_REFRESH: return R.string.horizon_game_remote_high_refresh;
            case LOCAL_BALANCED: return R.string.horizon_game_local_balanced;
            case LOCAL_HIGH_REFRESH: return R.string.horizon_game_local_high_refresh;
            case USB_HIGH_REFRESH: return R.string.horizon_game_usb_high_refresh;
            default: throw new IllegalArgumentException("Unknown preset");
        }
    }

    private static String defaultLabel(Context ctx) {
        return ctx.getString(R.string.pocket_gs_default);
    }

    public static String bitrateLabel(Context ctx, int kbps) {
        return kbps <= 0 ? defaultLabel(ctx) : (kbps / 1000) + " Mbps";
    }

    public static String fpsLabel(Context ctx, int fps) {
        return fps <= 0 ? defaultLabel(ctx) : fps + " FPS";
    }

    public static String resolutionLabel(Context ctx, String res) {
        int[] wh = GameProfile.parseResolution(res);
        return wh == null ? defaultLabel(ctx) : wh[0] + "×" + wh[1];
    }

    public static String frameGenLabel(Context ctx, int mode) {
        if (mode == GameProfile.FRAME_GEN_ON) {
            return ctx.getString(R.string.pocket_on);
        }
        if (mode == GameProfile.FRAME_GEN_OFF) {
            return ctx.getString(R.string.pocket_off);
        }
        if (mode == GameProfile.FRAME_GEN_LSFG) {
            return ctx.getString(R.string.pocket_framegen_lsfg_short);
        }
        return defaultLabel(ctx);
    }
}
