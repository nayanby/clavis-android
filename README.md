# Clavis Android

Clavis is an Android app for ebooks, PDFs, audiobooks, podcasts, news, articles and journals. Its application ID is `com.clavis.reader`.

## Features

- EPUB reading with typography controls, bookmarks and reading progress.
- PDF reading and annotation.
- Audiobook and podcast playback with chapters, bookmarks and sleep controls.
- Local neural narration, voice selection and listening within the native ebook reader.
- Project Gutenberg ebook and LibriVox audiobook catalog browsing and downloads.
- RSS/Atom source discovery, topic filters, Hindi typography, background refresh and paged article reading.
- Typed journal entries and handwritten notebooks.
- Local imports and torrent download integration.
- Light and dark appearance with Averia Serif Libre typography.

## Build and test

Requires Android SDK 36 and JDK 17 or a compatible Android Studio JDK. Configure `sdk.dir` in your untracked `local.properties`.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Updates are tested using debug builds on a phone first. Do not increment version metadata or create release builds until explicitly requested.

For an explicitly requested signed release, configure an untracked `upload-signing.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Keep the keystore and all credentials private.

## Source and notices

Clavis is maintained from the sibling Clavis+ implementation, with its own package identity and storage convention. Existing library data and preferences are preserved during updates. Third-party notices are retained in `licenses/` and alongside bundled components. Content availability and rights depend on each publisher and territory.