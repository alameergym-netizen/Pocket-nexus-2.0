# Stream DSP candidate

Optional stereo PCM16 EQ before AudioTrack, based on https://www.w3.org/TR/audio-eq-cookbook/ peaking filter equations. Attenuation-only presets and -2 dB headroom; saturating conversion is a numeric guard, not a true peak limiter. Default off; bypassed for external audio FX, unsupported formats or unexpected frame sizes. Separate channel history, reusable output buffer, no input mutation. No JamesDSP source, system-wide capture or Bluetooth vendor control. CPU cost, audio quality and latency require device measurements. Five unit tests in the existing com.limelight.pocket CI filter. Depends on horizon-audio-session; do not merge until that candidate and the integrated build pass.

## Integrated validation candidate
PR #6 passed 109 tests on the earlier baseline. This candidate preserves the 120-test performance and Theme Studio baseline and adds both the audio session fix and stream EQ. Avoids API 24-only Double.isFinite for minSdk 21, bypasses malformed profile preferences, and resets EQ state across discarded PCM blocks. No external effects or device hardware control is asserted. Full integrated build and physical RP6 audio tests are required.

## In-stream delivery diagnostics
Open PC stream audio route from the stream-specific menu to see the current Android output, track buffer and underruns, highest decoder queue, skipped PCM blocks and partial or failed writes. Reopening takes a new per-stream snapshot. The PCM callback keeps the prior 40 ms queue cutoff, checks the queue once per block, and counts skips without writing a log entry for every one. The EQ resets after an interrupted write. These counters distinguish renderer delivery from the Android track underrun signal. Physical FreeBuds/USB output, CPU cost, and end-to-end latency remain in the device matrix.

The playback backlog estimate compares complete frames accepted by `AudioTrack` with its playback timestamp. Partial writes contribute only accepted samples. The estimate uses timestamps up to one second old and rejects inconsistent readings above two seconds. It requires the active stream's track and uses no background polling. Use a separate measurement for acoustic Bluetooth or whole-stream latency. [Android AudioTrack timestamp](https://developer.android.com/reference/android/media/AudioTrack#getTimestamp(android.media.AudioTimestamp))

## Accessory and external DSP research

LibreBuds is GPL-3.0 and requires Android 13 or newer. Its supported-device list places the Huawei FreeBuds 7i in the expected basic battery and noise-control group. Verify the actual earbuds and firmware using the existing optional app handoff. [LibreBuds device table](https://github.com/librebuds/librebuds/blob/c7b0833e73e15c427719ed2b684060a98f67cb75/README.md#supported-models)

PC stream audio route allows a session-only output preference among the current Android media outputs. Use system default to clear it. The panel reports the preferred device separately from the observed route. Each change rechecks the endpoint and the owning AudioTrack, so an old menu or disconnected accessory cannot update a replacement stream. Device-level playback policy and headset settings stay under Android or the accessory app. Physical acceptance covers playback continuity and route readback with speakers, USB and FreeBuds.

RootlessJamesDSP is GPL-3.0. Its documentation describes internal playback capture, audible delay and coexistence limits with other audio effects. Keep the independent stream PCM equalizer and measure the existing Android effect-session handoff on RP6. [RootlessJamesDSP limitations](https://github.com/timschneeb/RootlessJamesDSP/blob/60d25ae8a53c6f4691c090673df290a73c6b6357/README.md#limitations)
