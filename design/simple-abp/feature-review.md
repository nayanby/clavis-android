# Simple ABP reference review

Inspected the user-supplied `mdmt-sabp-free` APK with Android's `aapt2`: package `mdmt.sabp.free`, label Simple ABP Free, version 1.8.3. No source code or binary assets from it were incorporated into Reson. `resources.txt` records the local resource inspection.

Its resources and menus expose folder-based listening, saved positions, bookmarks, next-book navigation, configurable seeking, playback speed, remote/headset controls, audio effects, covers, sleep/delayed pause, theme scheduling and widgets. Some strings describe paid-version features; the presence of a string does not establish that the free APK enables the feature.

Reson 0.2.0 adds folder-based book/chapter/cover import, natural chapter sorting, disc-folder grouping, automatic chapter advance, saved chapter/position/speed, named chapter-aware bookmarks with export/delete, adjustable skip intervals, undo seek, configurable resume rewind, optional next-book advance, end-of-chapter/custom sleep timers, equalizer and voice gain. Existing notification/headset controls remain.

Not ported: embedded M4B/MP3 chapter tables, home-screen widgets, scheduled themes, motion-based sleep resume, network downloads or arbitrary remote-button mappings. UI continues to follow the supplied Now Playing image and Cortex rather than the reference APK.
