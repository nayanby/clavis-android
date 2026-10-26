LibreTorrent-derived downloader code

Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
Source: https://github.com/proninyaroslav/libretorrent
Pinned commit: ae96f20b9852d2140fd704a3f2451ed7679e35a0
License: GNU GPL v3 or later. See LibreTorrent-GPL-3.0.txt.

LibreTorrentSession.java adapts core/settings/SessionSettings.java and core/model/session/TorrentSessionImpl.java: session limits, listening interfaces, bootstrap nodes, tracker announcement policy, encryption, metadata/resume alerts and session/DHT state restoration. TorrentService adapts the partial CPU wake-lock lifecycle pattern from LibreTorrent service/TorrentService.java. Audivo retains its own browser, queue UI, library importer and foreground-service integration. HTTPS tracker certificate validation stays enabled. libtorrent4j remains MIT-licensed; it is also LibreTorrent's upstream engine. This is an adaptation of the relevant engine code, not the entire LibreTorrent application.
