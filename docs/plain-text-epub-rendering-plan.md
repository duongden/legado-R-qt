# Ordinary text with the Direct EPUB renderer

## Accepted scope

- Add an ordinary-text renderer preference (native by default, Direct EPUB optional).
- Keep source format, chapter URLs, directory order, downloads and source switching independent of the renderer.
- Skip paragraph rules in Direct mode, including background refresh and read-aloud consumers. Preserve saved rules for native mode.
- Use the existing Direct reader capabilities and restrictions.
- Maintain separate native and EPUB layout profiles, including fonts, spacing, colors, backgrounds and reader chrome. Seed EPUB settings once from the existing profile; back up and restore both.
- Reuse the existing Direct WebView, navigation, preloading and frame pipeline. Generate one bounded document per ordinary chapter; do not build an EPUB archive or reconstruct a TOC.

## Implementation and verification

1. Introduce a shared renderer policy, profile storage and preference UI. Check mode defaults, exclusions and profile persistence/backup isolation.
2. Add an ordinary-text document/session provider using original BookChapter identities and existing content fetching. Normalize text once for display and read aloud; keep resources in the existing source context.
3. Connect startup, TOC navigation, chapter turns, source refresh and mode changes. Reject stale sessions and loads; include content revisions in document identity/cache invalidation.
4. Preserve character-based ordinary-book progress across modes and reflow. Prefer a matching text anchor when paragraph rules changed the displayed content; otherwise retain the same chapter. Do not persist Direct page numbers as native character offsets.
5. Apply Direct capability restrictions consistently to menus, style controls and content processors. Keep direct read-aloud coordination in the existing read-aloud subsystem.
6. Run focused policy/document/navigation tests, the existing EPUB regression suites, Kotlin compilation, browser/runtime checks and available device checks. Review differences before signing the arm64 release.
7. Build with a monotonically increasing version and the existing release identity/signature. Upload the reviewed APK and matching metadata to the existing R2 target; verify size and SHA-256.

## Required scenarios

- Native/Direct/native switches; restart and reflow; fonts, themes and chrome remain independent.
- Directory click, sequential forward/backward chapter turns, volume entries, repeated titles and missing content.
- Directory/content refresh and source changes while an older request is in flight.
- Paragraph rules remain unexecuted in Direct mode and do not leak through cached results.
- Read aloud uses the displayed text and retains correct chapter ownership.
- Large chapters, inline images, renderer failure and rapid page turns preserve a committed page or an actionable error.

## Delivery notes

- Added **Other settings / 普通正文渲染方式**, with native as the default. The preference explains that first layout, large chapters and image-heavy content can be slower and that behavior depends on the device and System WebView.
- Native and EPUB profiles have separate fonts, spacing, colors/backgrounds, chrome and selected/shared styles. Both are backed up. The active EPUB profile can no longer overwrite the native files during background restoration.
- Original chapter indices, URLs, volume entries and order are retained. Bookmarks/cloud progress use explicit navigation transactions and ordinary character offsets; source URL fragments are not interpreted as EPUB anchors.
- Stale ordinary-text targets are not clamped to the last chapter when the directory shrinks. Foreground, explicit navigation and session lookup preserve the requested index and reject a missing target. Persisted text cues do not split supplementary Unicode characters at either boundary.
- Direct mode skips paragraph rules and native text transformations, including speech preparation. Native-only menus and old tap shortcuts are gated. Source edits, chapter deletion/refresh, TOC updates and mode changes invalidate stale work.
- Network images use the original source URL/options and chapter context. MOBI images retain their container resource ids and use the existing local image cache path; image HEAD requests inspect cached metadata without downloading bodies.
- Final JVM regression after the API compatibility corrections: 486 tests, 484 passed, 2 local-sample probes skipped, 0 failures/errors. Coverage includes renderer policy, paragraph processing, normalization/escaping, stable chapter identities, Unicode-safe persisted anchors, Direct navigation/session behavior and the existing EPUB policies. Tests, release lint and signed arm64 assembly run together using `output/build-text-reader-release.py`.
- Browser checks use actual `EpubDirectDocumentBuilder` output and the shipped `direct-runtime.js`. All four generated-text cases passed page/offset round trips, font reflow and anchor restoration, with no script errors. Image decoding and real EPUB hook exclusion passed.

| Browser fixture | Initial pages | Larger-font pages | Maximum sampled Range reads |
| --- | ---: | ---: | ---: |
| Ordinary paragraphs | 41 | 63 | 18 |
| Long paragraph with supplementary characters | 123 | 179 | 20 |
| Paragraphs with an inline image | 42 | 64 | 18 |
| Vertical scrolling | 36 | 55 | 16 |

These are desktop Chromium checks at a 400 × 700 CSS-pixel viewport. Range counts include positioning/viewport-anchor work and do not establish Android frame rate, startup latency or memory use. The stored position returned to its sampled page; font reflow kept the text anchor within the new visible page.

Six existing EPUB browser regressions also passed: fragment windows, LTR/RTL/vertical direction, fragment renderability, wrapper reflow, resource readiness and annotations. The renderability probe was rerun in an isolated page after a shared browser page retained an earlier runtime; the original failure and successful isolated result are both saved.

No Android device is connected. Vendor WebView behavior, low-memory recovery, rapid physical gestures, rotation/process recreation and an on-device backup/restore round trip remain unverified. This R2 build must be described as device-unverified; neither zero bugs nor a measured device-performance improvement is claimed.

Verification artifacts are under `output/text-reader-release-build.log`, `output/playwright/text-reader-runtime-results.json` and `output/playwright/text-reader-regression/verified-results.json`. Final package and remote verification are recorded after the release build.

## Full Lint audit and compatibility correction

The first unbaselined full release Lint completed with **152 errors, 1,521 warnings and 44 hints**, so that combined build stopped before assembly. For all 151 source error locations, the reported line and its seven-line context match commit `02862085087b9a76f4c79b3eb46882096c0c7141`; the remaining error was an unescaped drive-letter colon in the local SDK property. This is a source comparison, not a claim that the pre-change application passed Lint.

The Direct reader's nine compatibility diagnostics were excluded from the baseline and addressed: use an explicit `TimeInterpolator` return type, handle scrolling through `WebView.onScrollChanged` on all supported API levels, and distinguish the legacy load-error callback from the API 23 callback. A null session prevents scroll callbacks during construction or after teardown from reaching the reader. The local SDK property was also escaped correctly.

The remaining **142 existing errors** are retained as an explicit, release-audit-only baseline. They are not fixed or presented as passing unbaselined Lint. The project Lint configuration is unchanged; `output/text-reader-lint-baseline.init.gradle` enables this exact baseline for the delivery build and keeps `abortOnError` enabled. Warnings remain visible. Every baselined issue has recorded HEAD source evidence, and no Direct compatibility finding or local configuration error is baselined.

Original reports and the failed build log are in `output/text-reader-lint-initial/`. The issue-by-issue comparison is in `output/text-reader-lint-triage.json`; the accepted baseline and provenance are in `output/text-reader-existing-lint-baseline.xml` and `output/text-reader-lint-baseline-audit.json`. The APK verifier checks those report and baseline hashes in addition to requiring zero remaining Lint errors. Compilation, relevant JVM tests, Lint and signed assembly are repeated after the compatibility changes.

## Verified release on 2026-09-08

The combined Gradle build completed successfully in **48m 22s**. The final JVM run contains 486 tests: **484 passed, 2 sample-dependent probes skipped, 0 failures/errors**. Full release Lint passed with **0 new errors**, 142 explicitly baselined existing errors and 1,521 warnings. The nine Direct compatibility findings and the local SDK property error are absent from the final report. AGP also completed its release-critical analysis; its separate report/check tasks were skipped by the normal task graph after full Lint.

| Release property | Verified value |
| --- | --- |
| Version | `3.26.09080626` (`29813666`) |
| Application | `io.legado.app.Archive`, non-debuggable |
| ABI / Android SDK | `arm64-v8a` only; min SDK 21, target SDK 36 |
| APK size | 40,562,518 bytes (38.68 MiB) |
| Signature | Existing certificate; APK v1, v2 and v3 verification passed |
| APK SHA-256 | `1a8d6387b5a3ca6cf75d075902ae4533c4e3315f12844034b63510b6c27f8b85` |
| Certificate SHA-256 | `d6441f18d3413a81c0e82e51f550c558c5d79213e921b02dec30bd3473d4f2b6` |
| R2 objects | `legado-arm64-v8a.apk` and `latest.json` |
| R2 metadata updated at | `2026-09-08T00:40:55Z` |

The APK contains the new reader classes, and its bundled runtime asset exactly matches the reviewed source. ZIP integrity, package identity, SDK levels and ABI contents were checked. R2 metadata, release notes and content type match the local release; a full authenticated download of the remote APK matches the local size and SHA-256.

The deliverable is `output/Legado-3.26.09080626-29813666-arm64-v8a-release.apk`. Package and remote evidence are in `output/text-reader-apk-verification.json` and `output/text-reader-r2-verification.json`; full build output remains in `output/text-reader-release-build.log`.

This completes the authorized R2 delivery. Android physical-device and vendor WebView checks remain unperformed; desktop browser results and static API compatibility checks are not device performance measurements. The setting and release notes explain first-layout/long-chapter/image costs and the option to return to native rendering.

## Image compatibility follow-up on 2026-09-08

The project and Android build tools have been fully migrated from C to D after two complete file hash comparisons and Git state verification. C paths now contain compatibility junctions. The image follow-up preserves original source `click`, inline `TEXT` images, data/SVG payloads and image preferences while keeping paragraph-rule `pclick` disabled. Native action dispatch verifies the visible document, session, source and chapter; action payloads count toward the chapter cache budget.

Release `3.26.09081010` (`29813890`) passed 506 JVM tests with 2 missing-sample skips, two browser image modes, six generated-text fixtures and six existing EPUB regression groups. Full Lint has zero new errors with the same 142 audited historical errors and 1,521 warnings. The signed arm64 APK was uploaded to R2 and downloaded in full to verify SHA-256 `a672e3ae927b595e82419302f14fdba10b3feda385ad78ec248d370b9af65560`. Physical Android/WebView validation remains unperformed.

Migration, implementation and delivery evidence are documented in [plain-text-image-compatibility.md](plain-text-image-compatibility.md). The original release records above remain intact.

## Image loading correction after the reported regression

The follow-up separates source-image work from WebView interception: each ordinary-book session owns cancellable image workers, while the interceptor serves immediate state responses or completed local bodies. Registered short paths preserve original image metadata without embedding scripts or large SVG payloads in resource URLs. Dynamic data/SVG, virtual bubbles, local/AI and MOBI images use explicit routes; actual bytes determine cached image MIME. Managed bubble conversion follows the explicit display preference and does not re-enable paragraph rules or `pclick`.

Ready-to-GET handoff protection, bounded retries, separate queue/loading deadlines, atomic cache replacement and configuration revisions address the audited races. Local ordinary-text refresh now closes the old session and keeps its character position. Visible and preloaded chapters strongly own image descriptors; the session registry does not retain discarded chapters. Publisher EPUB routing, original chapter identities and source-click validation remain separate.

The final source passed 562 JVM tests with 2 missing-sample skips, 0 failures and 0 errors. Browser coverage passed 15 asynchronous image cases, 2 image interaction modes, 6 generated-text fixtures and 6 publisher EPUB groups. The browser proof binds 51 current files; native source/proxy execution is mocked. Full Lint, signed assembly and the R2 round trip subsequently completed successfully. Physical Android/WebView validation remains unperformed.


Release `3.26.09081602` (`29814242`) is verified and delivered to R2. Full Lint reports zero new errors with the same 142 audited historical errors and 1523 warnings. The 40,611,548-byte arm64-only APK retains the existing package and certificate; its runtime matches the browser proof. A full authenticated readback of the uploaded object matches SHA-256 `7daefdf47c03c530151d9b926f3cdf65e175bea8140dec12da7e47325ff62a19` and its size. Metadata was updated at `2026-09-08T10:06:43Z`. Final evidence is in `output/text-image-fix-apk-verification.json`, `output/text-image-fix-r2-verification.json` and the linked image compatibility document; previous release records remain intact.


## Android ICU initializer correction: 3.26.09082019

Release `3.26.09081602` subsequently received a failure-to-open report with a truncated `NoClassDefFoundError`. Its actual image-source class initializer contains a regex that the desktop JDK accepts but native ICU rejects. The correction escapes its two literal closing braces; all other application inputs remain unchanged. The previous package/test/upload records are historical evidence and do not establish successful Android startup.

Corrective release `3.26.09082019` (`29814499`) passed 562 JVM tests with 2 existing sample skips, full Lint with no new errors, and signed assembly. Source and actual APK initializer patterns passed 24 native ICU cases plus the old-pattern negative control. Build inputs, test reports, build log, versioned APK, compatibility reports and upload are bound by hashes. Full R2 readback matches `ce17463e73328b0d70ad387acc2630022102c03359936e491ef2be6f43ac30ad` (40,611,561 bytes), with metadata time `2026-09-08T13:23:52Z`.

Unchanged browser evidence is explicitly reused rather than rerun. Android ART/device startup remains unverified, and the user's truncated exception does not establish that all image-free/publisher-EPUB failures share this cause. See [the image compatibility record](plain-text-image-compatibility.md) and `output/text-linkage-final-review.md` for scope and evidence.
