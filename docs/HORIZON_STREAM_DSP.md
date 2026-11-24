# Stream DSP candidate

Optional stereo PCM16 EQ before AudioTrack, based on https://www.w3.org/TR/audio-eq-cookbook/ peaking filter equations. Attenuation-only presets and -2 dB headroom; saturating conversion is a numeric guard, not a true peak limiter. Default off; bypassed for external audio FX, unsupported formats or unexpected frame sizes. Separate channel history, reusable output buffer, no input mutation. No JamesDSP source, system-wide capture or Bluetooth vendor control. CPU cost, audio quality and latency require device measurements. Five unit tests in the existing com.limelight.pocket CI filter. Depends on horizon-audio-session; do not merge until that candidate and the integrated build pass.

## Integrated validation candidate
PR #6 passed 109 tests on the earlier baseline. This candidate preserves the 120-test performance and Theme Studio baseline and adds both the audio session fix and stream EQ. Avoids API 24-only Double.isFinite for minSdk 21, bypasses malformed profile preferences, and resets EQ state across discarded PCM blocks. No external effects or device hardware control is asserted. Full integrated build and physical RP6 audio tests are required.
