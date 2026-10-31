# UI references

The Android source reference is `C:\Users\nayan\.copilot\repos\nayanby-stunning-engine` (Cortex Consumer). It is used read-only.

- Palette: `app/src/main/res/values/colors.xml` in the reference project.
- Headings: bundled Averia Serif Libre regular and bold fonts.
- Home cards: 16dp corners and the blue, sage and plum gradient families. Reson's only primary home cards are EPUB Books, PDF Books, Audio Books.
- PDF chrome: reference `PageChrome.kt`, `screen_paper.xml`, `paper.xml`, `activity_main.xml` and matching drawables. Title folder is 13sp; filename 14sp. White circular 34dp control backgrounds sit in 48dp touch targets. Footer uses previous/page/next at center and outline at right, with top and bottom fades.
- PDF rendering: reference `PdfEngine.kt`; same `com.artifex.mupdf:fitz:1.28.3` artifact and `AndroidDrawDevice.drawPageFitWidth` rendering API. Page work runs serially off the UI thread; a RecyclerView holds only nearby page images.
- EPUB: same book chrome and MuPDF renderer, with reflow text size in the top bar. Font changes restore a MuPDF location bookmark.
- Audio: `now-playing-reference.png` is the user's supplied layout. The user subsequently requested artwork first, all playback controls below artwork, full image proportions with a 10dp white border, 16dp outer corners and 14dp shadow elevation, a persistent horizontal volume slider, dedicated previous/next chapter buttons, a chapter button opening only the chapter list, and bookmark management/export in the corner menu. The initial reference remains a reference for the control groups.

## On-device checks still required

No device or emulator was connected during implementation. Test importing a PDF, EPUB, MP3 and M4A; password PDFs; long PDF scrolling; pinch zoom; stylus ink and undo; reflow location; bookmarks after restart; playback with screen off; headphone disconnection; notification actions; sleep timer; rotation; denied notification permission; and large text settings before release.



## Clavis+ UI match (version 54)

Uses the sibling Audivo/Clavis+ UI source for compact horizontal home cards, Averia typography, light/dark palettes, navigation, dialog insets, screen fades and responsive player layout. Notebook shell follows the same appearance preference and system-bar insets. Clavis package identity and its existing navigation destinations are preserved. PNG controls explicitly use FIT_CENTER; incomplete home rows retain equal card widths.

Validation: debug APK, signed release AAB and release lint passed. 68 on-device UI checks passed across light and dark modes, including icon fit, compact card dimensions, equal last-row width, player visibility and notebook system-bar placement. Original appearance preference restored.

## Online News and Articles source discovery (version 55)

News and Articles now search Feedly's source directory within the app, returning followable RSS sources through the same add flow as podcasts. Discover sources actions are visible on the shelves. Results retain the chosen shelf, deduplicate HTTPS URLs and reject invalid directory entries. Search failures offer retry/custom RSS; suggested sources remain available. Podcast search remains on its existing provider. Feedly source-search documentation: https://feedly.com/new-features/posts/introducing-the-feedly-teams-api


## Complete Clavis+ synchronization (0.10.6 / 84)

All 326 main/test implementation, resource, asset and library files were copied from the current sibling Clavis+ source with package and branding adaptations. Test assets were also copied. Normalized runtime implementation comparison found zero mismatches. Clavis keeps com.clavis.reader, its upload signing key and existing storage convention. Source and phone data were backed up before the update.

Validation: signed AAB/APK and release lint passed; installed as an update. 665 catalog checks, 21 discovery checks including live News/Articles feeds, 98 native reader checks, and 133 UI/journal/player checks passed. Screenshots were inspected. The reference article-post swipe test still fails at 'Swiping title area advances the whole card', as it also did in Clavis+; runtime code remains identical to the reference. Original appearance preferences restored after this failed test. The full-sync shorthand is recorded in AGENTS.md.


## Full Clavis+ sync — 0.10.8 / 86

Synchronized current main/test source, resources, assets and libraries from Clavis+. Zero normalized implementation mismatches and zero dependency differences. Includes compact catalog cards, square rounded audiobook jackets and portrait 3D ebooks. Existing Clavis package, signing and user data preserved. Source/app-data backups saved privately in ignored artifacts.

Debug APK, signed release AAB, test APK and release lint passed. Installed as an update on the connected phone; 660 catalog, pagination, download, import and shelf checks passed. Catalog screenshots inspected. Previously documented reference post-swipe limitation was outside this cover-layout change and was not retested.


## Full Clavis+ sync — 0.10.9 / 87

Complete source/resource/asset/library sync with zero normalized implementation or dependency differences. Includes BBC hidden-label/metadata and related-topic cleanup, and article cache v16. Clavis identity, upload key and saved data preserved. Source/app-data backups made before updating.

Installed on the connected phone; 30 publisher article-extraction regressions passed. Screen inspected; three exactly identified synthetic catalog-test shelf entries left by earlier test runs were removed after backing up the library. Build, release lint and signed AAB verification passed. An initial disk-space signing failure was resolved by removing the older generated clavis-ui-icons-build directory only; no source, user files or saved release artifacts were deleted.


## Full Clavis+ sync — 0.10.10 / 88

Synchronized all current runtime/test source, resources, assets and native libraries from Clavis+. Zero normalized runtime implementation mismatches and zero dependency differences. Clavis package, branding, upload key, download-folder convention and user data retained; source and phone data backed up before installation. Includes BBC image-copyright wrapper cleanup and article-text-v17 cache refresh.

Builds: debug APK, test APK, signed release AAB and release lint passed. Installed update on SM-S928B. All 162 post-carousel/extraction checks passed at font scale 1.0, including the two live BBC articles; light/dark screenshots inspected. The previously recorded post-swipe regression now passes in this reference test run.


## Full Clavis+ sync — 0.10.12 / 90

Synchronized current main/test implementation, resources, assets and native libraries. Zero normalized runtime mismatches and dependency differences. Includes News/Articles refresh controls, cache expiration/isolation/offline fallback, tab-preserving refresh completion and Ebooks wording. Package, upload signing, existing storage and data retained; source and available app data backed up before update.

Debug/test APK, signed AAB and release lint passed. Installed version 90. All 173 post-carousel/extraction/refresh checks passed at font scale 1.0. Verified the Ebooks home label in the device screenshot. No claim of successful live feed refresh is made by these controlled refresh-failure tests.

