# Cortex notebooks in Journalling

The original JournallingActivity Java editor has been removed. JournallingActivity.kt hosts the notebook library and paper viewer copied from the local Cortex Consumer 2 checkout on 21 October 2026. Cortex itself is unchanged.

## Ported implementation

`com.clavis.reader.notebook` contains Cortex's PaperListController, PaperViewer, PaperStore, InkCanvas/InkBoard, InkStore, PenMenu, ZoomShell/ZoomScrollView, RuledPaperView, NotebookImageStore/Layer, NotebookPdf, PdfEngine, OutlineRowSizing, and PaperListPrefs. The `cortex_` resource prefix isolates the ported layouts, icons, styles, colors and text from the rest of Clavis.

The activity connects notebook creation, rename/delete, library sorting/reordering, stylus tools, ink colors/widths, undo/redo, zoom, page navigation and page management, bookmarks, image insertion/pasting/moving/resizing/deletion, and PDF sharing. Finger gestures pan and zoom; stylus input writes. The insert-text action places typed text as a movable image.

Storage is private to Clavis under files/Journalling and files/ink. It never imports another installed app's files. FileProvider grants temporary access only to cache/shared PDF exports.

The local integration changes active-page tracking to use the page occupying the largest visible area; using the first partially visible page could insert an image on the preceding sheet after Add Page.

## Legacy entries

On first use, the old `journalling` preference entries are imported once. Pages and handwriting are converted into Cortex notebook storage; typed content is retained as text images. Original preference entries remain as backups. The `cortex_notebook_migration` preferences track imported entries.

## Verification

Debug app and instrumentation APK builds pass offline with the cached Android/Kotlin dependencies. On-device `ReaderChecks -e journalling true` runs 21 assertions covering migration, preserving source entries, page count, old typed content, old ink, layout, finger rejection for drawing, synthetic stylus events, undo/redo, page creation, image insertion/persistence, ink persistence, three-page PDF export, and reopening a notebook. The fixture is removed afterward.

Device screenshot: artifacts/journalling/cortex-notebook-check.png.

Review APK: artifacts/journalling/clavis-journalling-debug.apk.

The port script records the initial dependency-copy process. Subsequent integration changes in the copied sources are maintained directly; rerunning the script will overwrite them.

## Cortex chrome parity correction

The notebook host now also uses Cortex PageChrome, MaterialToolbar, title typography, paper/notebook menu resources, expandable pen group, and floating color-pill layout. Undo and redo move into the toolbar. The palette is anchored at toolbar.height + 8dp and 12dp from the right edge and appears only when Stylus is enabled, a marking tool is selected, and the pen group is expanded. The previous full-width swatch row has been removed. The obsolete undo container is hidden after its buttons move into the toolbar.

Device verification now includes 28 checks, including palette position, expansion/collapse visibility, and undo placement. Current screenshot: artifacts/journalling/cortex-notebook-chrome.png.

## Dated journal entries

The Journalling list and notebook overflow menus offer Create entry. Its creation dialog prefills the entry name with the current date in the device's local timezone (for example, 21 October 2026). Users can edit the name and choose ruled or blank paper before creating the notebook. Blank entry names are rejected. The original New notebook action and later Rename action remain available.
