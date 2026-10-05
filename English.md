# Legado-R-qt

[Tiếng Việt](README.md) · [English](English.md)

<p align="center"><img width="125" height="125" src="docs/archive_icon.svg" alt="Reading Archive"></p>

Legado-R-qt builds on [Legado-R / Reading Archive](https://github.com/duongden/legado-R), extending the Legado branch maintained by Lyc on the foundation of [Legado](https://github.com/gedoor/legado). It supports text and EPUB reading, themes, text-to-speech, AI and scheduled tasks.

The qt fork adds Vietnamese UI localization and offline Chinese-to-Vietnamese dictionary translation for text novels. Translation is applied for display while preserving original book data. Its quality depends on imported dictionaries; UI localization remains in progress.

The app does not supply book content. Add your own book sources or import local TXT and EPUB files.

## Downloads and updates

- [Legado-R-qt Releases](https://github.com/duongden/legado-R-qt/releases): APKs and release notes for this fork.
- [Legado-R-qt source](https://github.com/duongden/legado-R-qt).
- [Reading Archive upstream](https://github.com/Rimchars/legado): the upstream project and its releases.

Each release records its version and changes. Reading Archive upstream APKs are distributed independently of Legado-R-qt.

## Additions in the qt fork

- Vietnamese localization for Android, dialogs and the Web interface.
- Chinese-to-Vietnamese translation using Names, VietPhrase, phonetic dictionaries and Rule.txt, with TXT import through dictionary management.
- Translation in native and Direct text reading and supported book metadata displays.
- A Web translation option independent of the Android translation switch.

Offline dictionary translation is separate from AI services. See the [translation implementation notes](docs/translation-implementation.md) for details and reading-mode limitations.

## Features

- Configurable book sources and local books, native and EPUB rendering, page-turn animations, reading styles, bookmarks and progress.
- Bookshelf lists and grids, groups, tags, batch management, book details, chapter lists and scheduled updates.
- Local RED highlight rules, image patterns, fonts, HTML/CSS/JavaScript page templates, horizontal and vertical layouts.
- Shared image/font libraries, day/night themes, backgrounds, advanced titles, headers, footers and comment bubbles.
- System and network TTS, text following, floating controls, comic and video entry points.
- Configurable AI services, book-source search, book and chapter reading, reading-history queries and web tools.
- Scheduled tasks, caching, backup/restore, WebDAV, object storage and storage management.
- DNS/DoH selection, per-feature routing, domain exceptions, service configuration and speed tests.

Core features are inherited from Legado / Reading Archive. See the [feature and mode guide](docs/features.md).

## Documentation

- [Reader templates: use, sharing and authoring](docs/reader-templates.md)
- [Network and DNS](docs/doh-network.md)
- [Visual resource packages](docs/visual-resource-packages.md)
- [Paragraph rules and comment packages](docs/online-package-import.md)
- [Web and Content Provider API](api.md)
- [Legado help](https://www.yuque.com/legado/wiki)

Highlight rules are imported from local `.red` files. Complex image and CSS effects use EPUB rendering. EPUB rendering for ordinary text currently disables paragraph rules and `pclick`; original image `click` actions and replacement rules remain supported. Page templates have a separate backup library.

Bug reports should include the app version, rendering mode and reproduction steps. Test notes have their own dates and scope and do not replace verification on the device in use.

## Open source and acknowledgements

This README follows the [original duongden/legado-R README](https://github.com/duongden/legado-R/blob/main/README.md). Thanks to [gedoor/legado](https://github.com/gedoor/legado), [Luoyacheng/legado](https://github.com/Luoyacheng/legado), [Rimchars/legado](https://github.com/Rimchars/legado) and their contributors. Scheduled-task functionality includes contributions and support from 明月.

Translation source includes [legado-qt](https://github.com/duongden/legado-qt) and [VietPhrase translator](https://github.com/duongden/duongden-vietphrase-translator).

The project uses Rhino, Jsoup, OkHttp, Glide, Miuix, Paged.js and other open-source components. See [LICENSE](LICENSE) for the project license and [third-party notices](app/src/main/assets/LICENSE.md) for component licenses.

Upstream history is retained in the [changelog](CHANGELOG.md), [July 2026 notes](docs/changelog/2026-07.md) and [Legado history](docs/changelog/upstream-2022.md).
