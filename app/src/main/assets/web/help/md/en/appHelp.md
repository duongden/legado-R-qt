# Legado guide

## Getting started
Legado reads local files and fetches content according to source rules. It does not include a built-in book library. Import TXT/EPUB files or add compatible sources.

## Sources and Discovery
Import sources from files or links in source management. Enable only sources you use. A source needs Discovery rules to appear in Discover. A listing-only or ranking source may not provide readable content; choose a source with working details, contents and chapter rules.
Source validation is a test, not proof that every book is unavailable. Check the original site, sign in on the phone, retry or switch sources when necessary.

## LAN Web Service
1. Enable Web Service in the phone app.
2. Open the displayed address in a PC browser on the same network.
3. Read added books in Library. In Discover, choose a source/category or search the selected source.
4. Open details and choose **Add to library** or **Add and read**.
5. Wait for **Synced** before switching devices. Reading to chapter 5 in the browser resumes chapter 5 when you return to the phone.
The phone service must remain running and reachable. Stale requests cannot overwrite newer progress. During an outage, keep the tab open to retry; there is no durable offline queue after closing the browser. Some interactive source filters must be set in the app. Grid/list selection is saved in the browser.

## Local files and WebDAV
Import TXT/EPUB from the library menu or open files with the app. Grant access to a subfolder such as `Documents/Books`. For an empty TXT contents list, try another chapter rule. For garbled text, check encoding; UTF-8 is recommended. Use your own server/account for WebDAV books.
WebDAV backup and the WebDAV book library are separate features. Check a backup before deleting data or restoring over existing data.

## Reading and the library
Tap a book to read; long-press for details, metadata, groups and removal. Use contents to navigate chapters and book search to locate text. Choose a TTF/OTF font and adjust size, lines, paragraphs and width for your screen.
VIP/purchased labels come from the source. Reading rights depend on that source and the signed-in account. Failed chapter loads are not replaced with fabricated content.

## Progress and translation
LAN synchronization uses the phone database; WebDAV is not required when the PC reads through that phone. Cloud synchronization is configured separately. If translation/replacement changes the text between readers, the chapter remains shared but the character offset may be approximate.
Vietnamese/Chinese/English interface language is independent of Chinese-to-Vietnamese content translation.

## Rules and tools
Replacement rules modify text; paragraph rules process a chapter when enabled for the book. Test on one book before applying changes broadly. Read the relevant guide before changing JS, XPath, regex, TTS or scheduled tasks.

## Troubleshooting
Keep the source name, book URL, chapter and error for diagnosis. Reduce concurrency if search slows the phone. Check connectivity, folder access and the original site first. Back up before upgrading, editing sources or restoring.


## Web Service downloads, exports and e-ink
Open **Downloads**, choose a library book and enter a chapter range (starting at 1), or download the whole book. Chapters are cached on the phone and are available to its native reader. Tasks show progress, cancellation and retry. Leaving the page keeps tasks running; keep the app and Web Service active.

**Export TXT / Export EPUB** uses the phone's exporter and downloads missing chapters first. A source error or unavailable entitlement blocks export instead of creating a complete-looking file with missing content. Choose **Download file** when ready to save it on the browser device. Temporary files expire after one hour or earlier when task history is pruned; save them promptly. TXT is text only without separate image folders; EPUB uses native packaging. Web exports do not automatically upload to WebDAV.

Under **Settings → Download settings**, set chapters to prefetch (0–100; 0 disables web prefetch) and chapter interval (0–60000 ms). The chapter count is shared with the phone. The interval applies to uncached reading, prefetch and bulk downloads through Web Service. Source-specific limits still apply. A multi-page chapter can make multiple HTTP requests. Cached reads do not wait for the interval.

Choose **E-ink** under Appearance for black-and-white colors, high contrast, no motion/shadows and no continuous chapter scrolling. Use previous/next chapter buttons or arrow keys. The mode applies across Library, Discover, reader, uploads and help. Hardware/full refresh remains controlled by the browser and e-ink device.

## Manga, audio and video in Web Service

In Discovery, use **Content type** to filter novel, manga, audio or video sources. Category groups use compact dropdowns with internal scrolling. Add an item to the library, then open it to read manga pages or play audio/video with seeking and episode selection. Manga progress stores a page index; audio/video store milliseconds in the app database.

Direct media and HLS require browser codec support. Sources requiring login, CAPTCHA, DASH/MPD or custom DRM may need the phone. Source filters with custom JS actions remain in the app. Web downloads support text books and manga; manga exports use EPUB with images. Larger cover images avoid enlarging tiny thumbnails; sharpness still depends on the source image.

## Manga downloads and export on the web

Chapter download and EPUB export support both text books and manga in the library. Manga has Download / Export actions on its card and reader. A manga chapter is counted as cached only when all images are present and valid. EPUB export downloads missing chapters/images first and reports a failure for missing pages. TXT is for text; use EPUB to keep manga images. Discovery uses Category group and Genres dropdowns; descriptions preserve line breaks and paragraphs.
