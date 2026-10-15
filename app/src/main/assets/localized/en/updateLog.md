## 3.26.1015.1 · 15/10/2026

- Find and add books from Discovery on the web.
- Sync reading progress between phone and browser.
- Support manga, audio and video; download chapters and export TXT/EPUB.
- Refine the interface with Vietnamese, Chinese, English and e-ink support.
- Fix manga title translation, paragraphs and overflowing text.

# Legado R-qt

- Web Service: ranged downloads, configurable prefetch and throttling, TXT/EPUB browser downloads, and e-ink mode.
## In development · LAN Web Service

- Web Discovery: sources, categories, source search, details, add to library and read.
- Acknowledged chapter/position synchronization between browser and phone; stale writes are rejected.
- Shared compact navigation/settings and a persisted grid/list view.
- Vietnamese, Chinese and English interface/help; native documents follow app language.
- Web contents preserves VIP/purchased labels from the source.

## 3.26.1008.2 · 2026-10-08 · Build 12030

Android 5.0+. Updates use the app HTTP downloader instead of DownloadManager on affected devices. Download byte count and APK package are checked before installation. Downloads can be retried; error details omit private URLs/headers. The signing key is retained for in-place updates and data preservation. If an older build cannot download, install the GitHub APK over it once; do not uninstall the old app.

## 3.26.1008.1 · 2026-10-08 · Build 12029

- Preserve video/audio/manga types selected by scripts/users while fetching episodes.
- Translate displayed titles, episode names, source metadata and HTML/Markdown introductions from Chinese to Vietnamese without modifying source data or playback URLs. Translate duration metadata and player controls.
- Guard callbacks after library views are destroyed. Move selected-text Ask AI work to the background and keep cancellation state consistent.
- Save crash logs internally first, display them in logs and limit retained reports.
- Rename books/authors and bookmarks in one transaction; scope bookmark search to the book.
- Validate HTTPS sources on port 443 and handle non-HTTP identities without host validation.
- Report successful/failed/skipped book-list imports separately; search/save run in the background.
- Debug builds use the Android debug key without release passwords. Build RTMP JNI from source with 16 KB alignment and add ELF/RELRO/ZIP checks.

## 3.26.1007.1 · 2026-10-07 · Build 12028

- Improve original selected-text extraction for Ask AI when book translation is enabled.
- Reply-language instructions follow Vietnamese/Chinese/English interface selection. Improve Ask AI window position/drag behavior and translated book subtitles.
- Add Vietnamese labels across AI assistant settings/tools and three bundled skill-language variants.
- Improve book-detail text contrast and responsive action grids (up to three per row). Correct light/dark labels and localize dictionary-provider names.

## 3.26.1005.1 · 2026-10-05

- About links to R-qt source/releases. GitHub update checks use `duongden/legado-R-qt`, exclude drafts/prereleases/unrelated APKs and compare numeric versions. Optional startup checks run at most once per six hours; users choose download/installation.
- Add separate browser light/dark palettes with persistence, reset and contrast checks.
- Merge the Web Service start page into Library and share navigation for sources, uploads, RSS and help.
- Unify web colors, UI fonts, borders, radii, states and shadows while retaining separate reader content fonts. Improve narrow-screen source editing/upload pages.
- Add Vietnamese AI subpage labels. Translate Discovery card metadata and fix theme-following text/icons/hover, search sizing and alignment.

For a manual check, open **My → About → Check for updates**. Android's package installer handles final installation confirmation.

- Web Service: compact category dropdowns, bounded card text, sharper covers and LAN manga/audio/video readers.

- Manga downloads and export on the web; preserve description paragraphs and align page controls/cards.
