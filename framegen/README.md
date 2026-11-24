# Built-in Lossless Scaling frame generation

Frame generation on the stream itself, inside Pocket Nexus, using the user's own copy of
Lossless Scaling (`Lossless.dll`, never shipped with the app).

* `lsfg-vk/` — [lsfg-vk](https://github.com/PancakeTAS/lsfg-vk) (MIT), Android branch by
  FrankBarretta ([lsfg-vk-android](https://github.com/FrankBarretta/lsfg-vk-android), commit
  `b55b182`), with its third-party libraries: DXVK's DXBC translator (zlib), pe-parse (MIT),
  volk (MIT). Vendored unchanged.
* `src/main/cpp/` — the native render loop, shader extraction and Vulkan session of
  [LSFG-Android](https://github.com/FrankBarretta/LSFG-Android-Application) (GPLv3, commit
  `b847541`). Changed here: the JNI class name, and the swapchain is never pre-rotated
  (IDENTITY transform, the window's own size), so a panel that is portrait by nature (Retroid
  Pocket 6) shows the stream the right way round.

The app's own part (decoder frames in, generated frames out to the stream's surface) is in
`com.limelight.pocket.video.LsfgPipeline`.
