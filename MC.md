# MC — Tiến độ cập nhật Legado-R-qt

Cập nhật: 2026-11-18.

## Phạm vi và quyết định

- Người dùng yêu cầu cập nhật lần lượt các sửa lỗi và tính năng đã phân tích từ ba repo tham khảo.
- Giữ giao diện và kiến trúc hiện tại của R-qt; không chuyển sang MD3.
- Giữ Mozilla Rhino 1.8.1, API nguồn truyện hiện có, minSdk 21 và Java 17 trong các thay đổi này.
- Bảo toàn các sửa đổi có sẵn trong workspace. Những file dirty ngoài danh sách bên dưới không mặc định thuộc đợt cập nhật này.
- Ngày 18/11/2026: người dùng đã duyệt changelog và cho phép commit/push/tích hợp main/phát hành. Trạng thái mới nhất xem đợt 12 bên dưới.

## Mốc đối chiếu

| Repo | Commit local lúc khảo sát |
| --- | --- |
| Legado-R-qt (project hiện tại) | `4f7a9cbf2` — 2026-10-17 |
| `/Users/anhdao/Documents/GitHub/legado` | `330e606c4` — 2026-11-01 |
| `/Users/anhdao/Documents/GitHub/legado-2` | `52519722d` — 2026-11-10 |
| `/Users/anhdao/Documents/GitHub/legado-with-MD3` | `fe2a770ef` — 2026-11-16 |

Đã khảo sát issue/PR tại [LegadoTeam/legado](https://github.com/LegadoTeam/legado/issues) và [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3/issues). Issue đóng do stale không được coi là bằng chứng đã sửa. Các đề xuất chưa triển khai vẫn cần đối chiếu lại mã và tái hiện trên R-qt.

## Đã sửa mã nguồn — đợt 1

### 1. Nhập quy tắc mục lục TXT cùng ID

- So sánh toàn bộ nội dung lưu trữ để nhận biết cập nhật; giữ phép so sánh entity theo ID cho trạng thái chọn dòng.
- Thêm `app/src/main/java/io/legado/app/ui/association/TxtTocRuleImportComparison.kt`.
- Áp dụng tại `ImportTxtTocRuleDialog.kt` và `ImportTxtTocRuleViewModel.kt` cùng thư mục.
- Thêm `app/src/test/java/io/legado/app/ui/association/TxtTocRuleImportComparisonTest.kt`.
- Tham khảo [PR #1407](https://github.com/LegadoTeam/legado/pull/1407).

### 2. Giới hạn xóa âm thanh khi TTS phát lỗi

- Chỉ xóa file nằm trực tiếp trong thư mục cache tổng hợp TTS sau khi chuẩn hóa canonical path.
- Chỉ xử lý media URI dạng `file`; giữ file tải xuống ngoài thư mục này và file trong thư mục con.
- Thêm `app/src/main/java/io/legado/app/service/TtsCacheFiles.kt` và nối vào `HttpReadAloudService.kt`.
- Thêm `app/src/test/java/io/legado/app/service/TtsCacheFilesTest.kt`.
- Tham khảo commit MD3 `c0f3799d7`.

### 3. Header khi Cronet redirect sang origin khác

- Bỏ `Authorization` và `Cookie` khi scheme, host hoặc port thay đổi; giữ credential cho redirect cùng origin.
- Sửa `app/src/main/java/io/legado/app/lib/cronet/AbsCallBack.kt`.
- Thêm `app/src/test/java/io/legado/app/lib/cronet/CronetRedirectTest.kt`.
- Đã kiểm tra hàm tạo redirect request bằng JVM; chưa xác minh chuỗi redirect qua Cronet native/WebDAV thực.
- Liên quan [MD3 #2382](https://github.com/HapeLee/legado-with-MD3/issues/2382) và [Legado PR #1452](https://github.com/LegadoTeam/legado/pull/1452).

### 4. Hủy tác vụ header nguồn truyện

- Cho `CancellationException` truyền lên thay vì bị bắt và ghi thành lỗi thực thi header.
- Sửa `app/src/main/java/io/legado/app/data/entities/BaseSource.kt`.
- `RhinoInterruptError` hiện kế thừa `Error`, không thuộc nhánh `catch (Exception)` vừa sửa.
- Tham khảo [PR #1421](https://github.com/LegadoTeam/legado/pull/1421).

### 5. WebView nền tránh instance từng attached

- Theo dõi việc WebView từng gắn vào cửa sổ; request nền chỉ tái sử dụng instance chưa từng attached hoặc tạo mới.
- Giữ cơ chế pool theo phạm vi và chạy thao tác WebView trên Main của R-qt.
- Sửa `help/webView/WebViewPool.kt`, `help/http/BackstageWebView.kt`, `ui/rss/read/VisibleWebView.kt` dưới `app/src/main/java/io/legado/app/`.
- Bổ sung ca kiểm thử vào `app/src/androidTest/java/io/legado/app/help/webView/WebViewPoolLifecycleTest.kt`.
- Tham khảo [PR #1408](https://github.com/LegadoTeam/legado/pull/1408).

## Kiểm chứng đã thực hiện

- Kiểm thử mục tiêu TXT import, xóa cache TTS và Cronet redirect: Gradle báo `BUILD SUCCESSFUL`.
- Bộ JVM đầy đủ: **1.591 tests, 0 failures, 0 errors, 3 skipped** (1.588 ca không bị bỏ qua).
- `:app:compileAppDebugAndroidTestKotlin`: đạt.
- Đóng gói APK debug và APK AndroidTest trong luồng connected test: đạt; chưa phải APK release.
- `git diff --check` trên các file tracked do đợt này sửa: đạt. Kiểm tra toàn workspace có cảnh báo whitespace từ thay đổi `gradlew.bat` có sẵn, chưa chỉnh sửa file đó.
- Log cũ còn tồn tại đã được sao chép vào `artifacts/session-2026-11-18/logs/` ở đợt 4; không xóa bản gốc. Log kiểm tra tại thời điểm thực hiện: `/private/tmp/rqt-update-tests.log`, `/private/tmp/rqt-update-regression.log`, `/private/tmp/rqt-webview-device.log`. Đây là log tạm, có thể bị dọn.

### Kiểm thử thiết bị còn bị chặn

- Phát hiện thiết bị ADB và package `io.legado.app.rqt.debug`.
- Lần chạy offline thiếu dependency UTP; đã chạy lại có mạng và tiến tới bước cài APK.
- Thiết bị từ chối cài: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
- **Chưa chạy được kiểm thử lifecycle WebView trên thiết bị; chưa xác nhận runtime/UI cho các bản sửa.** Không coi lỗi cài đặt này là kiểm thử hành vi thất bại.
- Khi tiếp tục: kiểm tra lại thiết bị/package, chỉ chạy lại cài đặt khi người dùng đã cho phép; không gỡ ứng dụng hoặc xóa dữ liệu để vượt qua hạn chế cài.

## Đợt 2 — Import JSON theo luồng (2026-11-18)

- Thêm `BookSourceImportReader.kt`: đọc từng nguồn trong mảng bằng `JsonReader`, hỗ trợ nguồn đơn và descriptor `sourceUrls`, kiểm tra hủy trong lúc đọc và giữa các mục.
- Luồng URI đọc trực tiếp từ Reader UTF-8; dữ liệu dán vẫn xuất phát từ chuỗi đã có sẵn. Hỗ trợ BOM.
- Luồng HTTP dùng `useStreamingImport`, giữ giới hạn 4 MiB sau giải nén. Helper import dùng chung trước đó vẫn giữ nguyên để không đổi hành vi RSS/TTS.
- Chỉ đưa danh sách vào preview khi toàn bộ input và các URL đăng ký đã đọc thành công. Các nguồn được giữ trong RAM để phục vụ preview; đây không phải import có mức dùng RAM cố định. Một nguồn/script cực lớn vẫn có thể tiêu tốn nhiều RAM.
- Từ chối phần tử null, nguồn thiếu URL và dữ liệu thừa sau JSON. Không xử lý đăng ký URL lồng nhau.
- Bước so sánh dùng một snapshot `allPart` của DAO thay cho truy vấn từng nguồn.
- Sửa `ImportBookSourceViewModel.kt`, `ImportResponseLimits.kt`; thêm `BookSourceImportReaderTest.kt` và mở rộng `ImportResponseLimitsTest.kt`.
- Kiểm thử mục tiêu đạt. Bộ JVM đầy đủ sau thay đổi: **1.600 tests, 0 failures, 0 errors, 3 skipped**; gồm 6 ca parser và 6 ca response limits (3 ca mới). Gradle báo `BUILD SUCCESSFUL`; toàn bộ main/test Kotlin được biên dịch trong tác vụ này.
- Kiểm tra whitespace các file tracked của đợt 2 đạt. Log: `/private/tmp/rqt-import-tests.log`, `/private/tmp/rqt-import-regression.log` (file tạm).
- Chưa đo RAM/import file lớn trên thiết bị, chưa nghiệm thu preview/lưu nguồn qua UI; chưa build/cài APK mới hoặc phát hành cho đợt 2.
- Đính chính cache: `BookHelp` đã dùng tên ổn định cho sách online và đọc/migrate cache tên cũ. Giữ mục này ở bước rà soát/kiểm thử, không triển khai lại từ đầu.

## Đợt 3 — Rhino API, cache, SVG và đoạn TTS rỗng (2026-11-18)

### Đã sửa/triển khai

- **API nguồn Rhino:** thêm `java.post(url, ByteArray, headers[, timeout])`, chuyển byte qua ISO-8859-1 rồi ghi cùng charset để giữ nguyên dữ liệu. Giữ overload chuỗi, rate limiter, DNS scope, cookie jar và kiểm tra hủy hiện có. Thêm `java.strResponse(url[, body])` để tạo `StrResponse` cho script kiểm tra đăng nhập/giải mã. Cập nhật tài liệu JS mặc định và vi/en/zh.
- Kiểm thử API qua chính Mozilla Rhino và HTTP server local: đủ 256 giá trị byte, overload 3/4 tham số, POST chuỗi Unicode và response URL/body/status. Không đổi engine hoặc dependency Jsoup.
- **Remap cache khi đổi TOC:** thêm `ChapterCacheRemapPolicy`; chỉ khớp định danh/tiêu đề không mơ hồ ở cả danh sách cũ và mới, bỏ volume, ưu tiên URL, tránh lấy một payload cũ cho nhiều chương mới hoặc lấy payload đã dành cho URL khác. Không remap dòng không có cả URL lẫn tiêu đề. Khi migrate, ưu tiên file ổn định trước file legacy.
- Thêm kiểm thử TOC chèn/đổi tên, URL trùng, URL rỗng, POST khác body, tiêu đề trùng, volume và thư mục cache khác khi bookUrl đổi. Chưa thay schema/định dạng tên cache hiện có; chưa giải quyết trường hợp đổi nguồn nhưng vẫn dùng cùng bookUrl hoặc URL phụ thuộc biến JS ngoài request.
- **SVG:** suy ra chiều thiếu bằng tỷ lệ viewBox thay vì ghép hai chiều khác tỷ lệ; từ chối SVG không đủ kích thước hợp lệ; dùng kích thước đã giải để tạo viewBox khi cần. `ImageProvider` thêm fallback SVG cho ảnh data URL ở cả đo kích thước và giải mã bitmap; coi một chiều raster lỗi là cần fallback.
- Thêm 2 kiểm thử chính sách kích thước JVM và 2 AndroidTest kiểm tra parser/render SVG. Chưa chạy AndroidTest trên thiết bị, chưa xác nhận hết lỗi phân trang lần đầu ở native/Direct.
- **TTS:** regex đoạn không đọc được nay nhận cả chuỗi rỗng; tác dụng tới bỏ qua cue, queue hệ thống và nhánh âm thanh im lặng HTTP đang dùng chung regex. Thêm hồi quy đoạn rỗng, phần còn lại rỗng sau offset, khoảng trắng/ký hiệu/emoji và giữ nội dung Việt/Trung/Ả Rập/số. Chưa kết luận sửa xong chương chỉ tiêu đề, streaming hoặc highlight xuyên trang.

### Rà soát JSON đồng thời

- Thêm `RhinoConcurrentJsonTest`: 800 lượt parse/stringify trên 8 luồng với scope độc lập; thêm 1.600 lượt trên 20 prototype dùng chung giữa các scope nguồn (8 luồng), bao gồm truy cập JSON lần đầu.
- Giữ nguyên Mozilla Rhino 1.8.1. Chỉ coi lỗi là chưa tái hiện trong các ca kiểm thử này; không khẳng định hết mọi race hoặc thay engine bằng patch upstream khác.

### Kiểm chứng

- Các ca mục tiêu API, JSON độc lập, remap cache và kích thước SVG đã đạt ở lượt đầu.
- Hồi quy cuối: **1,618 tests, 0 failures, 0 errors, 3 skipped**; `:app:testAppDebugUnitTest` và `:app:compileAppDebugAndroidTestKotlin` báo `BUILD SUCCESSFUL`. Log: `/private/tmp/rqt-phase3-regression.log`.
- Không cài APK, không chạy connected test, không commit/push/phát hành ở đợt này. Các tính năng lớn bên dưới vẫn chưa triển khai trừ API Rhino vừa bổ sung.

## Checklist tiếp tục

### Hoàn tất sửa lỗi nền tảng

- [ ] Xác minh runtime các bản sửa đợt 1, nhất là WebView và TTS.
- [x] Đã triển khai parser import nguồn JSON theo luồng và kiểm tra hủy; runtime import vẫn cần nghiệm thu. Tham khảo [MD3 PR #2625](https://github.com/HapeLee/legado-with-MD3/pull/2625).
- [x] Đã kiểm tra JSON đồng thời ở scope độc lập và prototype chung bằng Mozilla Rhino hiện tại; chưa tái hiện lỗi. Phạm vi bằng chứng JVM ở đợt 3; không chép patch dành cho engine khác.
- [ ] Rà soát và kiểm thử định danh cache đã có: `contentCacheIdentity()`/`contentCacheFileName()`, đọc cache cũ và remap khi mục lục đổi; xét URL trùng/rỗng/POST/đổi nguồn. Nhận định trước rằng R-qt chưa có cache ổn định là chưa đúng. Liên quan [Legado #1425](https://github.com/LegadoTeam/legado/issues/1425).
- [ ] Xác minh/sửa kích thước SVG lần phân trang đầu, cả native và Direct; tham khảo [MD3 #2607](https://github.com/HapeLee/legado-with-MD3/issues/2607).
- [ ] TTS: đoạn thuần ký hiệu, chương chỉ có tiêu đề, lỗi tổng hợp/streaming và highlight xuyên trang; liên quan [MD3 #2467](https://github.com/HapeLee/legado-with-MD3/issues/2467), [Legado #1416](https://github.com/LegadoTeam/legado/issues/1416).

### Tính năng đã đề xuất, chưa triển khai

- [x] Có quản lý cache trong R-qt từ trước; đợt 4 sửa đếm theo TOC/file hợp lệ và thêm kiểm tra/khôi phục chỉ mục, giữ payload. Cách ly định danh nguồn và nghiệm thu UI vẫn còn riêng. [MD3 PR #2571](https://github.com/HapeLee/legado-with-MD3/pull/2571).
- [x] Đợt 4 bổ sung tìm theo hướng/phạm vi và nhớ lựa chọn; snapshot lựa chọn cho mỗi request, chống kết quả cũ trộn vào request mới. Ánh xạ vị trí khi dịch bật/tắt và Direct vẫn cần nghiệm thu UI. [Legado PR #1409](https://github.com/LegadoTeam/legado/pull/1409).
- [x] Đợt 4 thêm WebDAV giữ N backup theo tên thiết bị chính xác, mặc định 0/không giới hạn; chỉ dọn sau upload thành công. Không đụng file chung/không có tên thiết bị hoặc không đúng mẫu ngày. Cần tên thiết bị riêng để phân biệt các máy. [Legado PR #1420](https://github.com/LegadoTeam/legado/pull/1420).
- [x] Đợt 4 thêm tùy chọn tự upload sách local sau import (mặc định tắt), hàng đợi foreground có trạng thái/lỗi/hủy, staging/hash SHA-256 và PUT If-None-Match: * để tránh ghi đè. Chưa thử trên tài khoản WebDAV thật. [Legado PR #1418](https://github.com/LegadoTeam/legado/pull/1418).
- [x] Đợt 5 thêm đọc theo đoạn cho sách văn bản, mặc định tắt, dùng native list và giữ tọa độ chương/TTS. EPUB/PDF/sách ảnh giữ renderer riêng; nghe TTS và khôi phục dài hạn vẫn cần nghiệm thu. [Legado PR #1440](https://github.com/LegadoTeam/legado/pull/1440), [#1445](https://github.com/LegadoTeam/legado/pull/1445).
- [ ] Cải thiện TalkBack/vùng bấm trên UI R-qt hiện có. [MD3 #2558](https://github.com/HapeLee/legado-with-MD3/issues/2558).
- [x] Đợt 5 thêm import .md/.markdown thành bản EPUB nội bộ, giữ nguyên nguồn, có TOC/nội dung/ảnh raster/Unicode và resolver SAF; mã HTML gốc được escape. Đã mở file mẫu trên điện thoại. SAF/ảnh thực tế và native/Direct cần nghiệm thu riêng. [MD3 PR #2576](https://github.com/HapeLee/legado-with-MD3/pull/2576).
- [ ] Chọn xuyên trang: native đã có mã từ trước, đợt 5 bổ sung ca thực thi qua 3 trang rồi đổi chương (đạt JVM/Robolectric). Direct vẫn chặn lật khi có vùng chọn, chưa triển khai nối range qua trang.
- [x] Đợt 4 thêm lựa chọn màu highlighter riêng ngày/đêm, giữ fallback cho rule cũ; hỗ trợ import/store và renderer native/Direct/template. Rule CSS tùy chỉnh vẫn ưu tiên CSS hiện có. Chưa nghiệm thu UI chuyển theme. [MD3 PR #2601](https://github.com/HapeLee/legado-with-MD3/pull/2601).
- [x] API Rhino body nhị phân và response thay thế: đã bổ sung, có kiểm thử HTTP qua Rhino và giữ API cũ; tham khảo legado-2 `8174cc335`, `976621d1b`. Chi tiết đợt 3.
- [ ] Đo RAM và nghiên cứu ngân sách ảnh/tái sử dụng ảnh bìa từ legado-2 trước khi chọn phần port.

## Những phần cần bảo toàn hoặc chưa đủ cơ sở sửa

- R-qt đã bỏ qua ảnh ảo `dp:`/`bubble://paragraph` trong nhiều đường cache/download: giữ làm ca hồi quy, chưa kết luận có lỗi như MD3 #2593.
- `BookHelp.hasImageContent()` của R-qt kiểm tra từng chương/ảnh, khác lỗi bỏ qua cả sách của legado-2: không chép bản sửa chỉ dựa tên hàm.
- Giữ engine EPUB Direct và hệ mẫu trang riêng của R-qt; dùng cải tiến rich text MD3 làm nguồn ý tưởng/ca thử.
- Không chép migration theo số phiên bản: database 113 của R-qt và upstream khác schema.
- Tối ưu vẽ gộp chữ, hiệu ứng kính và thay UI/engine toàn bộ chưa thuộc phần triển khai hiện tại. Người dùng đã loại việc chuyển sang MD3.
- Chưa build release, ký/kiểm tra APK release, commit/push hoặc đăng GitHub Release cho các thay đổi này.


## Đợt 4 — Quản lý cache, tìm kiếm, WebDAV, màu và TTS (2026-11-18)

### Lưu hồ sơ tại project

- Hồ sơ mới: `artifacts/session-2026-11-18/`; gồm log, baseline trạng thái dirty, APK checksum và XML kết quả JVM. Mã kiểm thử ở `app/src/test`/`app/src/androidTest`, APK và báo cáo build ở `app/build` trong cùng repo.
- Đã sao chép log `rqt-*.log` cũ còn tồn tại từ `/private/tmp` vào thư mục hồ sơ. Những lượt build ban đầu dùng chung tên log phase4 nên đã ghi đè log lượt trước; diễn biến được ghi ở ghi chú kiểm chứng, không giả lập lại log gốc đã mất.
- File phiên chat của Codex và cache SDK/Gradle chung do ứng dụng chủ quản lý, không di chuyển được bằng sửa repo. Tác vụ này không xuất tài khoản/cookie/traffic cá nhân vào hồ sơ.

### Mã nguồn đã triển khai

1. **Cache:** màn quản lý vốn đã có trong R-qt. Nay đếm chương dựa TOC thật, bỏ volume và file rỗng/thư mục, tránh đếm file legacy/orphan thành chương mới. Thêm menu kiểm tra/khôi phục chỉ mục cho loại sách đang chọn; đối chiếu ảnh manga, bỏ qua tác vụ audio đang chạy/tạm dừng và tác vụ cloud đang chạy. Chỉ cập nhật manifest, không xóa payload chương để “sửa cache”. Chưa thay định danh cache.
2. **Tìm kiếm:** toàn sách/chương hiện tại/từ chương hiện tại đến cuối/từ đầu đến chương hiện tại; hướng xuôi/ngược ở cả chương và kết quả trong chương. Lưu lựa chọn, snapshot regex/replace/hướng/phạm vi cho mỗi request, đưa kết quả lên Main, chống lẫn request cũ và truyền cancellation. Chưa thay cơ chế ánh xạ vị trí dịch/Direct.
3. **WebDAV giữ N:** lựa chọn 0/1/3/5/10/20/30/50, mặc định 0 (giữ nguyên hành vi không dọn). Chỉ tên `backupYYYY-MM-DD-<đúng tên thiết bị>.zip`, ngày hợp lệ, không phải thư mục/file rỗng; chỉ dọn khi listing có file vừa upload. Giữ bản vừa upload khi đồng hồ lệch, bỏ file chung và thiết bị khác. Kiểm tra cấu hình không đổi trước mỗi lần xóa. Các thiết bị phải đặt tên riêng; không tự suy đoán chủ sở hữu file cũ.
4. **Tự upload sách local:** mặc định tắt, dùng queue foreground và bảng tác vụ đã có; stream vào staging cùng lúc hash SHA-256, upload bằng tên `sha256-<hash>.<ext>` với `If-None-Match: *`, 412 giữ file cũ, lỗi hiện trong tác vụ/AppLog và không biến import thành thất bại. Giữ bookUrl/origin của sách local. Queue chưa bền qua việc tiến trình bị hệ thống giết; có thể nhập lại để retry, không ghi đè bản đã có. Có thể cần dung lượng staging bằng kích thước sách.
5. **TalkBack/vùng bấm bước đầu:** các nút tìm kiếm đầu/cuối, thông tin tìm kiếm và stop đạt vùng 48dp trở lên, có focus/description/live-region. Chưa coi đây là hoàn tất audit TalkBack toàn reader.
6. **Highlighter:** thêm `styleColorTypeNight` tùy chọn; rule cũ dùng palette ngày/đêm cũ khi chưa chọn màu đêm. Có UI chọn màu từng chế độ, import, lưu và render chung cho native/Direct; template nhận đúng trạng thái night. CSS tùy chỉnh giữ ưu tiên trước cấu hình màu đơn giản.
7. **TTS:** gửi tiến độ ở mọi onRangeStart, bắt kịp nhiều trang khi callback nhảy xa và nhận đúng biên trang. HTTP streaming từ chối status lỗi/body rỗng/type JSON, đóng response trên lỗi/cancellation trước khi retry, chuyển ownership sang player khi hợp lệ. Chưa nghiệm thu nghe thật, chương chỉ tiêu đề và highlight native/Direct.
8. **Ảnh bìa:** coalescing cùng key, giới hạn 8 công việc đang chờ/chạy; tạo bitmap thumbnail nhỏ có quyền sở hữu riêng trước khi queue, không giữ bitmap Glide gốc tới khi I/O chạy. Ghi file tạm rồi rename sau khi encode xong. Chưa coi là hoàn tất tối ưu toàn bộ ảnh hoặc đo PSS lâu dài khi cuộn thư viện.

### Kiểm chứng và trạng thái

- Lượt đầu cache/tìm kiếm: build JVM + AndroidTest compile đạt. Lượt thêm enum upload gặp lỗi when chưa exhaustive tại PackageSyncTaskDialog; đã bổ sung nhánh hiển thị tác vụ local book rồi chạy lại đạt.
- Bộ JVM sau cùng, bao gồm hardening khởi động foreground: **1.632 tests, 0 failures, 0 errors, 3 skipped**, gồm scope tìm kiếm, retention tên thiết bị, HTTP PUT precondition và byte, ownership response TTS, gate ảnh bìa và tương thích màu đêm. XML trong `evidence/jvm-final-xml/`.
- APK debug và APK kiểm thử build đạt; checksum trong `evidence/apk-checksums.json`. Người dùng đã cho phép cài đè bản debug giữ dữ liệu để kiểm chứng Android. Cài ứng dụng debug đã thành công, gồm bản cuối sau hardening; `am start -W` trả `Status: ok`. Đây chỉ là bằng chứng cài/khởi động, chưa nghiệm thu giao diện.
- APK kiểm thử bị Android từ chối cả lượt đầu và lượt thử lại sau khi người dùng xác nhận điện thoại sẵn sàng: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. Không chạy được instrumentation. Log riêng `device-install-tests.log` và `device-install-tests-retry.log`; không gỡ/xóa dữ liệu hoặc thay cấu hình bảo mật để vượt hạn chế.
- Đã tạo cấu hình emulator riêng trong `emulator/` tại hồ sơ; SDK Android 37 hiện chỉ còn metadata/kernel, thiếu system.img/ramdisk/userdata. Emulator báo `No initial system image for this configuration!`, không boot được. Chưa tải thêm system image. Đây là lỗi thiếu môi trường kiểm thử, không phải test hành vi thất bại.
- Các ca AndroidTest mới đã biên dịch nhưng **chưa chạy**: migration file cache/POST, SVG parser/render, import 10.000 nguồn (>10 MiB) và ownership thumbnail sau recycle bitmap gốc. Ca WebView lifecycle cũng chưa chạy.
- Không commit/push hoặc phát hành release. Không chuyển sang MD3.

### Chưa hoàn tất sau đợt 4

- Chế độ đọc theo đoạn hoàn chỉnh (#1440/#1445): cần tích hợp reader/TTS/prefetch/khôi phục vị trí, không đồng nhất với việc tắt “TTS theo trang” vốn đã có.
- Markdown: cần thiết kế đường import/TOC/encoding/SAF/ảnh và giữ đồng nhất native/Direct; chưa triển khai.
- Chọn văn bản xuyên trang: chưa triển khai mô hình vùng chọn ở cấp chương và UI của cả native/Direct.
- TalkBack toàn bộ reader và ngân sách ảnh toàn ứng dụng còn audit/đo tiếp; đợt này mới sửa control tìm kiếm và hàng đợi thumbnail.
- Đổi nguồn giữ nguyên bookUrl, URL có biến JS: vẫn cần cách ly/định danh cache có migration an toàn. Không đánh dấu hoàn tất chỉ vì ca URL/POST đã đạt.
- Nghiệm thu SVG phân trang đầu native/Direct, TTS nghe thật/chương chỉ tiêu đề/streaming/highlight, UI import JSON lớn và UI tìm kiếm khi dịch bật/tắt còn theo từng bằng chứng thiết bị.


## Đợt 5 — Đọc theo đoạn, Markdown và hồi quy chọn native (2026-11-18)

### Đọc theo đoạn cho sách văn bản

- Thêm lựa chọn “Đọc theo đoạn hoàn chỉnh (sách văn bản)” trong cài đặt khác của reader; mặc định tắt. Khi bật, sách văn bản dùng danh sách native với mỗi đoạn hoàn chỉnh là một mục, có tiêu đề heading, ảnh, nút chương trước/sau và play/pause ≥48dp. EPUB/PDF/sách ảnh/audio/video giữ đường đọc riêng.
- Tham khảo các model/list từ repo `legado` local (PR #1440/#1445); không chuyển giao diện sang MD3 và không chép service/migration của upstream.
- Gom các dòng của cùng đoạn qua nhiều trang từ layout đã hoàn tất; ánh xạ UTF-16 theo từng range để giữ tọa độ chương, seek và TTS. Có kiểm thử đoạn bị ngắt trang, range không liên tục, ảnh và tiêu đề.
- Mode paragraph dùng cue không cắt theo trang; chỉnh cả service và panel để giữ cùng cách phân đoạn. Highlight scoped book/chapter, follow có tính đến điều hướng thủ công, viewport có thể lưu/khôi phục khi Activity recreate. Phím lật trang cuộn danh sách trong chế độ này.
- Không thay schema database hoặc dữ liệu tùy chọn engine cũ. Khi tắt chế độ, reader dùng lại lựa chọn engine hiện có.

### Markdown qua bản EPUB nội bộ

- Nhận `.md`, `.markdown`, MIME `text/markdown` và `text/x-markdown` trong luồng import/file association; archive scanner cũng nhận đuôi mới.
- Đọc theo stream, BOM UTF-8/UTF-16LE/BE và encoding detector hiện có; TOC tách ở `#`/`##` ngoài fenced code. Dùng CommonMark/GFM tables/strikethrough đã có trong dependency, không thêm engine.
- Tạo EPUB 2 có mimetype STORED đầu tiên, container/OPF/NCX/XHTML, sau đó đi qua parser và renderer EPUB hiện có. Ghi staging rồi rename, reimport cùng URI thay bản nội bộ; không sửa/xóa Markdown gốc.
- Ảnh PNG/JPEG/GIF/WebP tương đối được nhúng và dedupe SHA-256, có ảnh data URI base64. Resolver file kiểm tra canonical parent/symlink; SAF dùng sibling document ID trong quyền được cấp. Nếu không có quyền hoặc không hỗ trợ ảnh, giữ alt/placeholder và cảnh báo tổng quát. Chưa hỗ trợ nhúng SVG local vào bản Markdown chuyển đổi.
- URL ảnh/link giới hạn http(s), không credentials hoặc cú pháp thực thi nguồn; raw HTML được escape. Không đưa URL/query ảnh vào log lỗi. Giới hạn: 32 Mi ký tự toàn tài liệu, 512 Ki ký tự mỗi section/dòng, 8 MiB/ảnh, 32 MiB tổng ảnh nhúng, tối đa 256 ảnh và 10.000 chương. Đoạn dài tách ở dòng trống sau khoảng 64 Ki ký tự.
- Kiểm thử dùng cả parser EPUB thực tế của R-qt để xác nhận TOC đọc được, không chỉ xác minh ZIP/XML.

### Chọn xuyên trang — đính chính và kiểm chứng

- `SelectionEdgeAutoPager`, `SelectionEdgeTurnState`, `ContentTextView` và `ReadView` đã giữ anchor theo chương và tự lật mép trong native từ trước đợt này. Nhận định “chưa triển khai toàn bộ” trong MC cũ không chính xác cho native.
- Thêm `NativeCrossPageSelectionTest`: tạo 3 trang, chọn từ trang 1 đến trang 3, kiểm tra newline/emoji, chuyển trang hiển thị vẫn giữ cùng nội dung và đổi chương thì hủy vùng chọn. Đạt JVM/Robolectric. Lượt đầu fixture dùng Activity thường không đáp ứng AppCompat callback; sửa fixture rồi test đạt.
- Direct vẫn có `selectionPageLock` và Kotlin setPage xóa selection; chưa thay guard hoặc khẳng định đã hỗ trợ kéo range xuyên trang. Cần triển khai/hồi quy riêng với renderer handoff, reflow và selection handles thực.

### Kiểm chứng

- Bộ JVM: **1.671 tests, 0 failures, 0 errors, 3 skipped** sau thay đổi; 39 ca tăng so với đợt 4. AndroidTest compile đạt, APK debug build đạt. Log từng lượt tại `artifacts/session-2026-11-18/phase5/logs/`.
- Cài đè debug trên điện thoại hiện tại thành công, giữ dữ liệu. Fixture do tác vụ tạo nằm trong `phase5/fixtures/`; trên điện thoại chỉ tạo fixture riêng trong files/ của bản debug. Không dùng sách cá nhân để thay thế ca mẫu.
- Import/open Markdown qua FileAssociationActivity thực đã hiển thị chữ đậm/nghiêng/Việt/Trung. Phát hiện tiêu đề h1 bị chồng dòng ở lượt đầu, đã thêm style heading và build/cài/reimport lại. Ảnh chương hai sau sửa cho thấy heading và nội dung hiển thị bình thường. Ảnh/log trong `phase5/evidence/`; đây chưa phải nghiệm thu mọi định dạng/style/ảnh.
- Điện thoại có lần khóa giữa lượt thử; người dùng đã mở lại. Phím Menu của ADB mở đa nhiệm của hệ thống, chạm giữa màn hình chưa mở được menu đọc trong cấu hình hiện tại. Người dùng đã bật paragraph bằng UI; cây UI cho thấy từng đoạn là TextView và ba nút Chương trước/Đọc thành tiếng/Chương sau. Người dùng đã mở và cuộn tới Đoạn 2; ảnh `paragraph-user-scroll.png` thấy đoạn 2 và các nút điều khiển hiển thị bình thường. Chưa xác minh khôi phục pixel sau thoát/recreate. Không tự sửa preference XML hay cấu hình bảo mật của máy.
- Chưa chạy được instrumentation APK (bị chặn cài ở đợt trước); các ca mới ở AndroidTest vẫn ở mức compile. Không coi kết quả JVM hoặc mở reader là bằng chứng đã nghe TTS, test TalkBack hay đo RAM dài hạn.

### Còn tiếp tục

- Đã thấy UI đọc theo đoạn bật và cuộn tới đoạn 2 trên sách mẫu; còn nghiệm thu đổi chương, xoay màn hình, bật/tắt mode, nghe TTS và TalkBack trên danh sách mới. Danh sách đoạn hiện ưu tiên văn bản/ảnh và heading, chưa giữ toàn bộ span/CSS trang trí của dòng native cũ.
- Nghiệm thu ảnh/SAF và nhiều kiểu Markdown thực tế; kiểm tra render native/Direct riêng. Kiểm tra phục hồi sách Markdown nhập từ archive sau khi bản chuyển đổi bị mất.
- Triển khai range xuyên trang cho Direct; native cần nghiệm thu kéo handles thực trên máy ngoài ca JVM đã đạt.
- Các mục cache đổi nguồn/biến JS, SVG phân trang đầu, TTS streaming/highlight, WebView/import JSON lớn và ngân sách ảnh dài hạn ở đợt trước vẫn giữ trạng thái theo bằng chứng, chưa tự đánh dấu hoàn tất.

- Bổ sung cuối đợt 5: nút play trong paragraph mode dùng resume nếu đang pause và chưa có điều hướng thủ công, thay vì luôn phát lại từ đầu đoạn. Hồi quy/build sau chỉnh sửa đạt. Có snapshot `dumpsys meminfo` riêng package debug; số đo đơn lẻ chỉ để khảo sát, không chứng minh tối ưu hoặc giảm RAM.

- Kết quả UI cuối đợt 5: người dùng bật chế độ paragraph và cuộn sách mẫu tới Đoạn 2. Ảnh xác nhận đoạn văn cùng nút Chương trước/Đọc thành tiếng/Chương sau hiển thị bình thường; không coi đây là kiểm thử nghe TTS hoặc khôi phục pixel khi mở lại. Hai sách mẫu vẫn được giữ trên giá sách debug để người dùng có thể kiểm tra tiếp. Bản debug cuối (resume + redacted warning) build đạt và được cài cập nhật theo quyền đã cấp; log cài đặt tại phase5/logs/device-install.log.


## Đợt 6 — Danh sách không ảnh bìa (2026-11-18)

- Thêm “Danh sách không ảnh bìa” trong Bố cục tủ sách → Danh sách/Danh sách gọn → Kiểu danh sách. Áp dụng chung cho hai kiểu nhóm kệ sách hiện có; giữ tên, tác giả, tiến độ/trạng thái, chương mới và số dòng giới thiệu đã chọn.
- Discovery có lựa chọn riêng cùng tên trong cài đặt Bố cục trang khám phá, không phụ thuộc lựa chọn ẩn bìa của kệ sách. Áp dụng danh sách hiện đại, trang kết quả khi mở danh mục/nguồn và Discovery Suite: widget ngẫu nhiên/xếp so le chuyển thành danh sách chữ, widget ngang giữ cuộn ngang với thẻ chữ, bảng xếp hạng giữ số hạng nhưng bỏ bìa.
- Không dựng composable ảnh hoặc chừa cột ảnh trong chế độ mới; bỏ chiều cao tối thiểu phụ thuộc ảnh, giữ vùng bấm ít nhất 48dp. Chạm mở sách, nhấn giữ xem trước/menu vẫn dùng callback hiện có. Mặc định và các bố cục có ảnh không đổi; không chuyển sang MD3.
- Bản dịch tên lựa chọn: Việt/Anh/Trung giản thể. Log build và hồ sơ của đợt này ở `artifacts/session-2026-11-18/phase6/`.
- `:app:assembleAppDebug --offline`: BUILD SUCCESSFUL (2m 47s); kiểm tra XML tài nguyên mới và whitespace các file thay đổi đạt. APK/checksum và log trong hồ sơ đợt 6. Chưa chạy lại bộ JVM cho thay đổi hiển thị này; con số 1.671 tests ở đợt 5 không phải lượt chạy mới của đợt 6.
- Cài đè `io.legado.app.rqt.debug` giữ dữ liệu: thành công (`device-install-approved.log`: Success). Lượt trong sandbox không mở được ADB socket, đã chạy lại bằng quyền được cấp. Chưa nghiệm thu trực quan bố cục mới sau khi bật trên điện thoại.


### Bổ sung đợt 6 — Chọn ẩn bìa ngay trong danh mục nguồn cổ điển

- Yêu cầu làm rõ: tại Khám phá cổ điển → nguồn → một danh mục → danh sách truyện, cần tự chọn ẩn bìa ngay tại màn hình đó.
- Thêm mục checkable “Ẩn ảnh bìa” trong menu ⋮ của `ExploreShowActivity`. Bật/tắt cập nhật Compose tại chỗ, không gọi tải lại nguồn và không chủ động cuộn về đầu.
- Lưu lựa chọn riêng `exploreShowCover` cho danh sách danh mục nguồn; khi chưa có lựa chọn riêng thì kế thừa ẩn/hiện từ bố cục Discovery trước đó. Sau khi chọn, không thay bố cục kệ sách, trang chính Discovery hoặc chế độ cổ điển. Nhấn giữ khi đang ẩn bìa không dùng tọa độ bìa cũ làm điểm xuất phát preview.
- Log/bằng chứng: `artifacts/session-2026-11-18/phase6/classic-category/`. Build debug đạt (`BUILD SUCCESSFUL in 1m 45s`), XML tài nguyên và whitespace đạt. Cài cập nhật `io.legado.app.rqt.debug` giữ dữ liệu thành công (`device-install.log`: Success). Chưa nghiệm thu trực quan nút mới trên điện thoại; không chạy lại bộ JVM cho thay đổi hiển thị này.


## Đợt 7 — Bố cục chi tiết sách Phẳng (18/11/2026)

- Thêm lựa chọn Phẳng bên cạnh Cổ điển và Toàn cảnh trong Quản lý chi tiết sách; lưu cấu hình và định tuyến tới activity/view model chi tiết có sẵn.
- Tham khảo ảnh người dùng về cách sắp xếp: bìa nhỏ + metadata, lưới thao tác 4 cột, thư viện ảnh, cập nhật, chương và giới thiệu; hai nút vào/xóa khỏi kệ và đọc cố định bên dưới. Chỉ nối các chức năng hiện có.
- Dùng palette giao diện ngày/đêm, không dùng backdrop bìa; giữ callback nhấn/nhấn giữ bìa/tên/tác giả và renderer giới thiệu Markdown/HTML/WebView. Hai kiểu cũ giữ đường xử lý riêng.
- Hồ sơ: `artifacts/session-2026-11-18/phase7-flat-book-info/`. Build và kiểm chứng thiết bị đang thực hiện; không coi build là bằng chứng UI.

- Kết quả đợt 7: build debug đạt (4m 50s), XML/tham chiếu string/icon và scoped whitespace đạt. Cài đè giữ dữ liệu thành công, APK/checksum lưu trong hồ sơ đợt 7. Chưa chạy lại bộ JVM cho thay đổi bố cục. Chưa nghiệm thu trực quan: màn hình quản lý nội bộ không mở được bằng ADB; đang chờ người dùng chọn Phẳng và mở chi tiết sách.

- Nghiệm thu đợt 7 trên điện thoại: người dùng chọn Phẳng, mở sách và cuộn về đầu. Hai ảnh trong `phase7-flat-book-info/evidence/` xác nhận bìa/metadata, lưới nút, thư viện ảnh, công tắc, chương, giới thiệu và thanh Đọc hiển thị không chồng nhau ở giao diện sáng. ADB vuốt không đổi màn hình, thao tác chọn/cuộn do người dùng thực hiện. Chưa kiểm chứng từng callback, chế độ đêm, xoay màn hình hoặc nút riêng nguồn.
- Bổ sung bản dịch Việt cho nhãn tác vụ cập nhật còn tiếng Anh trong ảnh; build cuối đạt (18s), cập nhật APK/checksum lưu cùng hồ sơ. Ảnh chụp trước thay đổi nhãn, mã bố cục giữ nguyên.
- Bản debug cuối sau sửa nhãn đã cài đè giữ dữ liệu thành công (`device-install-corrected.log`: Success).


## Đợt 8 — Bổ sung giao diện tiếng Việt (18/11/2026)

- Nguyên nhân: 470 string thiếu bản Việt nên fallback tiếng Anh; checker cũ chỉ bắt thiếu chuỗi gốc chữ Hán.
- Bổ sung 470 mục string, 8 mảng nhãn, Video cuối mảng loại sách; sửa 3 nhãn Regex/User agent/CookieJar. Tách hai tiêu đề code view/result sang tài nguyên EN/VI/ZH. Bảo toàn ID, mảng giá trị cấu hình, mã, URL, tham số định dạng và biến mẫu.
- Sửa checker để bắt thiếu bản dịch cả nguồn tiếng Anh, kiểm tra mảng nhãn và độ dài. 8 regression tests đạt; kiểm tra 2.915 string mặc định và 2.901 string Việt không có string cần dịch bị thiếu. Không coi đây là nghiệm thu toàn bộ văn bản động/nguồn/tài nguyên web.
- Build cuối riêng trong repo đạt (2m 23s, 99 tác vụ thực thi); giữ nguyên cache KSP chung bị hỏng ở lượt trước. 472 key mới/bổ sung xác nhận có cấu hình vi trong APK cuối bằng aapt2. Scoped whitespace đạt, không chạy lại toàn bộ JVM/Android instrumentation cho thay đổi nhãn.
- Hồ sơ: `artifacts/session-2026-11-18/phase8-vietnamese/` gồm APK cuối/checksum, kiểm kê trước/sau, log build/tests/resource APK. Tắt bước chép APK ra thư mục cha khi build riêng; bản copy từ build đầu đã được xác nhận checksum và chuyển vào hồ sơ trong repo.
- Chưa cài/kiểm chứng UI cuối: ADB reset đã khởi động lại nhưng vẫn không thấy thiết bị; người dùng báo cắm USB, macOS chưa nhận Android; địa chỉ không dây cũ No route to host. Chờ cáp dữ liệu/Gỡ lỗi USB hoặc địa chỉ ADB mới. Không thay bảo mật hay dữ liệu ứng dụng.

## Đợt 9 — Chẩn đoán lỗi ghép đôi ADB

- Làm rõ lỗi người dùng gặp nằm ở `adb pair`; 8 lượt `adb devices` thành công chỉ chứng minh giao tiếp client/máy chủ, không chứng minh ghép đôi hoạt động.
- ADB SDK và Homebrew cùng bản 37.0.1, trùng checksum. Log ghép đôi mới nhất ghi `No route to host` ở network/pairing_client trước bước kiểm tra mã. Cổng ghép đôi mới có lúc nhận TCP qua `nc`; cổng cũ sau đó đóng. Chưa kết luận lỗi do phiên bản hay quyền mạng.
- Kiểm tra trực tiếp System Settings: Terminal và Android Studio đều bật Local Network; không tự thay quyền. Không xóa khóa ghép đôi hay dữ liệu điện thoại.
- Tải ADB 36.0.2 chính thức từ Google vào `artifacts/session-2026-11-18/phase9-adb/tools/`, giữ nguyên SDK/Homebrew/PATH. Máy chủ riêng 5040 khởi động được, danh sách thiết bị còn trống.
- Chuẩn bị `phase9-adb/pair-rqt.command`: chỉ khởi động lại máy chủ thử nghiệm 5040 từ Terminal người dùng, hỏi địa chỉ hiện tại, để ADB nhận mã trực tiếp và không lưu mã vào script/log/tham số lệnh. Kiểm tra cú pháp `bash -n` đạt. Đang chờ người dùng chạy bản thử và xác nhận kết quả; chưa khôi phục được ghép đôi, chưa cài APK đợt 8.
- Log chẩn đoán đã lọc thông tin trong hồ sơ đợt 9; không lưu mã ghép đôi đã gửi trong hội thoại.

## Đợt 10 — Kiểm chứng Việt hóa qua ADB

- Người dùng báo đã kết nối được; xác nhận máy chủ mặc định thấy Xiaomi amethyst ở trạng thái device. Không quy kết nguyên nhân lỗi ghép đôi hay khẳng định bản ADB riêng đã sửa được.
- Xác nhận APK đang cài trùng bản build 16:30 và đủ 472 key Việt hóa, tránh cài lùi bản đợt 8. Ảnh thực tế xác nhận Cài đặt giao diện, Quản lý chi tiết sách, Của tôi và Quản lý bong bóng có nhãn Việt.
- Sửa thêm nhãn biểu tượng `iconMain` thành Mặc định/Biểu tượng 1…7; chỉ đổi mảng nhãn, giữ mảng giá trị cấu hình. Bổ sung kiểm tra hồi quy; 9 tests checker đạt, tài nguyên Việt không thiếu chuỗi cần dịch.
- Build debug riêng đạt (3m 32s), cài cập nhật giữ dữ liệu thành công. Chưa chụp bộ chọn biểu tượng sau cài trước khi người dùng chuyển sang yêu cầu tăng chữ; bản đợt 11 tiếp tục chứa sửa đổi này.
- Hồ sơ: `artifacts/session-2026-11-18/phase10-vietnamese-device/`. Ảnh màn hình chờ không được tính là bằng chứng UI. Helper đánh thức đã dừng, không thay cài đặt khóa điện thoại.

## Đợt 11 — Làm nổi bật tiêu đề ở kệ sách và khám phá

- Mục tiêu người dùng: tên truyện nổi bật hơn description; đồng thời tăng nhẹ tiêu đề trang, nhóm và danh mục, áp dụng các kiểu giao diện hiện có.
- Tên truyện danh sách 17–18sp, đậm vừa, tối đa hai dòng; lưới bìa 14–15sp; thẻ so le 16sp; tên truyện trong xếp hạng bộ khám phá 17sp. Mô tả/tác giả/thông tin chương giữ nguyên cỡ chữ.
- Tăng tiêu đề trang, nhãn nhóm/nguồn/danh mục ở kệ thư mục, kệ thẻ và khám phá cổ điển/hiện đại/bộ khám phá. Giữ lựa chọn ẩn tên có sẵn ở bố cục chỉ bìa.
- Thẻ so le cho phép giãn theo nội dung, hàng xếp hạng/nút danh mục nới chiều cao; thanh nhóm/tiêu đề tính thêm không gian theo font. Nhãn danh mục cổ điển tăng 2sp sau style nguồn và reset cơ sở khi tái dùng view để không tăng cộng dồn.
- Hồ sơ: `artifacts/session-2026-11-18/phase11-title-emphasis/`. XML và scoped whitespace đạt. Build đầu thiếu R.jar; chạy lại bước tài nguyên đạt và đã sinh R.jar, đang build tiếp. Chưa cài/kiểm chứng UI đợt 11.
- Build tăng tiêu đề ban đầu đạt (7m 39s), cài debug giữ dữ liệu thành công. Người dùng xác nhận các phần còn lại đã ổn, yêu cầu chỉ trả font danh sách nguồn Khám phá về cũ.
- Đã hoàn nguyên toàn bộ thay đổi kích thước/độ đậm trên tên nguồn trong `item_find_book.xml`; file khớp nguyên trạng Git. Giữ các phần còn lại theo xác nhận của người dùng, gồm tên truyện lớn và đậm vừa.
- Build bản hiệu chỉnh cuối đạt (1m 50s) sau tạo lại tài nguyên; `aapt2` xác nhận APK không còn thuộc tính textSize/textStyle bổ sung ở danh sách nguồn, đồng thời vẫn chứa kiểu tiêu đề truyện mới. Cài đè giữ dữ liệu thành công (`install-source-restored.log`: Success), cập nhật APK/checksum trong hồ sơ đợt 11.
- Chưa nghiệm thu trực quan mọi biến thể giao diện; có xác nhận người dùng về phần đã thấy ổn và bằng chứng tài nguyên APK cho danh sách nguồn đã trả về cũ. Helper đánh thức đã dừng, không thay cài đặt khóa thiết bị.

## Đợt 12 — Phát hành 3.26.1118.1 (12034)

- Người dùng duyệt changelog ngắn, yêu cầu commit/push/merge và phát hành APK cập nhật.
- Changelog ghi “Cập nhật UI”, “Sửa lỗi”; không ghi riêng thay đổi font. Đã cập nhật CHANGELOG.md và log mới nhất trong ứng dụng bằng Việt/Anh/Trung.
- Main local và origin/main cùng commit 4f7a9cbf, không có PR mở. Tích hợp trực tiếp main, không tạo thêm nhánh/PR.
- Build từ snapshot source công khai riêng; giữ nguyên những sửa đổi cũ về từ điển/theme/CI/relay ở workspace, không kèm dữ liệu hoặc tài nguyên cá nhân trong bản phát hành.
- Hồ sơ build, APK, checksum và kiểm chứng lưu tại artifacts/session-2026-11-18/phase12-release/.
- Kiểm tra tài nguyên Việt và 9 ca hồi quy checker đạt. Build offline đầu thiếu dependency release; lượt online biên dịch hai biến thể bị hết heap compiler. Build release riêng với một worker, compiler in-process và heap 12 GB đã đạt (6m 44s), bao gồm lint-vital; chỉ đổi tham số lệnh build.
- APK đúng package io.legado.app.rqt.Archive, phiên bản 3.26.1118.1 (12034), minSdk 21, targetSdk 36 và không debuggable. Chữ ký khớp bản trước; 12 thư viện native đạt căn chỉnh 16 KB. Kiểm tra APK không có các từ điển/theme cá nhân bị loại khỏi bản công khai.
- Bộ JVM riêng trên cùng source phát hành đạt (4m 11s): 1.671 tests, 0 failures, 0 errors, 12 skipped do dữ liệu tùy chọn không có trong source công khai. Tài nguyên APK xác nhận danh sách nguồn giữ font gốc. APK/checksum và verification.json đã lưu trong hồ sơ đợt 12.
- Các mục nghiệm thu runtime còn lại ở các đợt trước vẫn giữ nguyên; không coi build/JVM là nghiệm thu đầy đủ trên thiết bị.
- Đã commit bản cập nhật tại `0e0a4e76a1eba6d79c49fa21469beda095816460` và push trực tiếp lên origin/main. Không có PR cần merge; mã đã được tích hợp vào main.
- Đã phát hành [v3.26.1118.1](https://github.com/duongden/legado-R-qt/releases/tag/v3.26.1118.1), là latest, không draft/prerelease, tag trỏ đúng commit phát hành. Nội dung changelog khớp bản đã duyệt.
- GitHub có đúng APK release và `SHA256SUMS-3.26.1118.1`; digest và kích thước cả hai asset khớp file local. SHA-256 APK: `506312266c5c8dcb0f860362716a37080df08e076d6299a19969521f937b713f`. Chưa tải lại toàn bộ APK từ GitHub hoặc cài release mới lên điện thoại; không phát hành APK debug.
- Bảo toàn 20 file tracked dirty ngoài phạm vi cùng các thư mục/file untracked không liên quan. Metadata GitHub sau phát hành lưu trong publication.json và latest-release.json tại hồ sơ đợt 12.

## Rà soát sao lưu tài khoản/phiên nguồn — 2026-11-20

- Chỉ rà soát code theo yêu cầu, chưa sửa hành vi ứng dụng. Báo cáo và hướng triển khai: `artifacts/session-2026-11-20/backup-review/README.md`.
- Đã có backup/restore loginInfo, sourceVariable và source.put; có tùy chọn bỏ qua áp dụng cả khi tạo và khôi phục backup. Chưa đọc cài đặt thực trên thiết bị.
- Chưa có backup loginHeader động, CookieStore, cookie phiên RAM và dữ liệu WebView; cache/file do script tự quản không được bao phủ đầy đủ.
- sourceRuntime hiện là JSON thường trong ZIP; cần mã hóa dữ liệu bí mật khi mở rộng. Cần giữ deadline cache và kiểm tra kết quả mã hóa lại tài khoản trên máy mới.
- Có thể mở rộng để giữ phần lớn cấu hình và phiên còn hiệu lực; không bảo đảm chuyển được token ràng buộc thiết bị hoặc đã hết hạn. Chưa kiểm chứng chuyển hai thiết bị, chưa commit/push/release lượt này.

## So sánh giao diện đọc EPUB — 2026-11-26

- Đã đọc checkout local legado-with-MD3 (222978c5d), legado-2 (52519722d), legado (330e606c4) và source R-qt hiện tại; không fetch, sửa code hoặc cài app.
- Báo cáo kèm điểm vào code: `artifacts/session-2026-11-26/epub-ui-comparison/README.md`.
- R-qt có Direct giữ XHTML/CSS và mẫu trang riêng; MD3 dùng Compose Canvas với lớp giữ định dạng ngữ nghĩa có chọn lọc; legado/legado-2 chủ yếu xử lý EPUB thành nội dung cho reader của app.
- Nên tham khảo cây mục lục EPUB nhiều cấp của legado, các hiệu ứng lật trang bổ sung của MD3, lớp presentation cho chế độ text và cách neo lại vị trí bookmark/marking. Không cần chuyển giao diện sang MD3.
- Chỉ kết luận từ code; chưa nghiệm thu hình ảnh, hiệu năng hoặc hành vi trên cùng sách/thiết bị. Không coi test có trong repo là test đã chạy.

## Đợt 13 — Mục lục và mẫu trang EPUB local (2026-11-26)

- Người dùng yêu cầu áp dụng các phần phù hợp, không chuyển sang MD3; đối chiếu thêm `/Users/anhdao/Documents/GitHub/legado-R`. Người dùng xác nhận cho chọn mẫu trang cả với file EPUB nhập từ thiết bị.
- Đã thêm metadata NAV/NCX và UI mục lục nhiều cấp, thu gọn nhánh, mở các cấp cha của chương hiện tại và tìm kiếm có ngữ cảnh. Sách nhập trước được bổ sung metadata hiển thị qua URL khớp chính xác, không ghi đè chỉ số chương hay nội dung; tăng version cache cấu trúc EPUB để lấy metadata mới.
- Đã thêm Fade và Parallax cho Direct, gồm lựa chọn hiệu ứng riêng của mẫu trang; giữ các giá trị hiệu ứng cũ và bộ render hiện tại.
- EPUB local có lựa chọn mẫu riêng tại `Book.ReadConfig.epubReaderTemplateId`; null/rỗng giữ bố cục gốc. Menu mẫu có nút về bố cục gốc. Lựa chọn không áp đặt cho sách khác; không tự chuyển người đang chọn engine text sang Direct.
- Publication adapter giữ nội dung/ảnh/liên kết và biên chương trong mẫu; không sửa archive gốc, không chuyển EPUB thật sang tọa độ/TTS của truyện chữ. EPUB có script/tương tác báo chưa hỗ trợ và cho chọn lại bố cục gốc.
- Bổ sung thư viện mẫu vào gói backup, gồm snapshot mẫu được profile hoặc từng sách tham chiếu. Lựa chọn từng sách đi theo bookshelf JSON; không đổi schema DB. Phần tài khoản/cookie nguồn được rà soát trước đó vẫn là hạng mục riêng, chưa triển khai trong đợt UI này.
- Nền: MD3, legado và legado-R trong Documents có cùng 14 ảnh nền với workspace; 6 file mẫu EPUB ở repo legado-R tham khảo cũng trùng byte. Không có nền mới cần chép. Preset MD3 chỉ khác một số màu nhấn, không khác nền. Không sửa template store/asset cá nhân đang dirty.
- Kiểm chứng: 68 test JVM tích hợp + 1 test khôi phục thư viện mẫu đạt, 0 lỗi; debug APK và AndroidTest APK build đạt. Cài debug giữ dữ liệu thành công. Android chặn APK test lần đầu; người dùng cho phép và cài lại thành công.
- Test thiết bị luồng EPUB local đạt (20,101s): content URI → mục lục/parser thật → lựa chọn theo sách → mở mẫu/ảnh → Fade/Parallax → mở TOC → trở về gốc; file EPUB không đổi byte. Đã xem ảnh nền/mẫu, cây mục lục và reader gốc. Người dùng cũng tự chọn và xác nhận mẫu Quỷ Bí chạy trên EPUB mẫu.
- Test menu tự động mở rộng chưa hoàn tất: bị thao tác chọn mẫu xen vào, sau đó ADB mất kết nối. Người dùng yêu cầu chốt bản hiện tại và kiểm tra menu tự động sau. Không ghi phần này là test đã qua.
- Chưa triển khai iOS curl/Google curl, chuyển renderer sang semantic Canvas của MD3 hoặc kéo chọn xuyên trang Direct. Đổi mẫu có thể đổi phân trang; chưa cam kết giữ chính xác từng câu khi chuyển layout. Các mục runtime/TTS trước đây chưa được coi là đã hoàn tất.
- Hồ sơ, ảnh, APK, fixture, log, test results và patch nằm trong `artifacts/session-2026-11-26/epub-ui-update/` và các thư mục source/build của chính repo. Đã kiểm tra 20 file dirty ngoài phạm vi vẫn trùng snapshot cũ. Chưa commit/push/release đợt này.


## Dọn dữ liệu sinh — 2026-11-26

Đã dọn build/cache trong repo, các bản APK cũ/trùng, screenshot và file tạm/audit cũ; giải phóng khoảng 3.26 GiB kích thước logic. Giữ APK release gần nhất và APK debug/test EPUB mới nhất, mã nguồn, test/fixture, khóa ký và ghi chú/kết quả kiểm thử hiện tại. Các đường dẫn ảnh/build tạm trong ghi chú lịch sử có thể không còn tồn tại. Chi tiết: `artifacts/CLEANUP-2026-11-26.md`.


## Đợt 14 — Hoàn tất kiểm tra menu EPUB local (2026-11-26)

- Tiếp tục phần menu đã hoãn: xác minh đúng APK debug/test trên điện thoại. Menu Mẫu trang thực tế hiển thị; cách tìm text qua provider accessibility trong bài test không nhận diện được các node Compose.
- Đổi test sang duyệt cây accessibility, giữ nhãn theo ngôn ngữ Activity và thao tác click thật, không dùng tọa độ hoặc bỏ assertion. Lượt thiết bị sau sửa đạt 18,97s.
- Sửa callback đổi hiệu ứng trong ReaderTemplateDialog: so ID mẫu của sách qua EpubBookTemplateSelection thay vì chỉ ID của profile chung. EPUB local dùng lựa chọn riêng từng sách nên đường cập nhật ngay được gọi đúng.
- Build debug/test cuối đạt; 13 JVM liên quan đạt, 0 lỗi/bỏ qua. Đã cài debug giữ dữ liệu, checksum app/test trên máy khớp build cuối.
- Có một lượt kiểm tra iframe quá sớm thất bại; chạy lại cùng APK đạt. Đổi bài test sang chờ DOM iframe thay vì lấy mẫu một lần ngay sau khi WebView visible. Lượt cuối đạt 15,781s: nhập EPUB local, mẫu riêng/DB, Fade/Parallax, mục lục, trở về gốc, mở menu mẫu, thấy lựa chọn bố cục gốc và file EPUB giữ nguyên byte.
- Chưa nghiệm thu riêng mọi thao tác đổi hiệu ứng cuộn trong menu mẫu, mọi mẫu/preview hoặc TTS/TalkBack. Các mục Direct selection, backup tài khoản/cookie và tồn đọng trước đó giữ trạng thái cũ.
- Hồ sơ: `artifacts/session-2026-11-26/epub-ui-followup/`. APK mới nhất ở `epub-ui-update/`, đã thay bản debug/test cũ; không tạo thêm APK lịch sử. 20 file dirty có từ trước vẫn nguyên byte. Chưa commit/push/release.


### Dọn sau đợt 14

Đã dọn 25,257 file build/cache/screenshot/log/XML/audit và patch tạm (758.0 MiB). Giữ mã nguồn, test/fixture, khóa ký, ghi chú/kết quả tóm tắt và APK release + debug/test mới nhất. Các đường dẫn log/ảnh/patch cũ không còn tồn tại sau đợt dọn. Chi tiết: `artifacts/CLEANUP-2026-11-26.md`.


## Bổ sung mẫu tích hợp Hồng đăng — 2026-11-26

- Đăng ký `builtin.hong_dang` (Hồng đăng · Mộng Trường An) vào thư viện tích hợp; giữ mẫu mặc định và thứ tự năm mẫu cũ. Dùng chung bộ chọn mẫu cho truyện chữ và EPUB local trong Direct. Không đổi nội dung mẫu do người dùng thêm.
- Đồng bộ danh sách mong đợi của các test thư viện/export. Kiểm tra CSS trong test chấp nhận khoảng trắng tùy chọn để không từ chối CSS rút gọn hợp lệ.
- Kiểm tra tĩnh đạt: 6 ID duy nhất, asset JSON khớp ID, metadata hợp lệ và một vùng body trên mỗi loại trang Hồng đăng. Chưa chạy Gradle/JVM hoặc build/cài APK trong lượt này; chưa nghiệm thu hình ảnh mẫu trên thiết bị.


## Dọn debug cuối và chuẩn bị changelog — 2026-11-26

- Đã gỡ `io.legado.app.rqt.debug.test` và `io.legado.app.rqt.debug` trên điện thoại, xác minh không còn package và thư mục external debug. Xóa fixture `/data/local/tmp/rqt-phase5-ui.xml`, giữ sách cá nhân trong Download/Legado.
- Dọn 11 file debug/sinh còn lại trên Mac (84.1 MiB), gồm APK debug/test, metadata/config test tạm và marker Kotlin trong tmp. Giữ APK release gần nhất, source, test/fixture và khóa ký.
- Changelog nháp: `docs/changelog-draft.md`. Chưa commit/push hoặc phát hành bản mới; Hồng đăng vẫn mới kiểm tra tĩnh, chưa build/kiểm thử renderer của mẫu này.


## Đợt 15 — Phát hành 3.26.1126.1 (12035)

- Người dùng duyệt changelog và yêu cầu commit/push/merge, build APK chính thức. Changelog Việt/Anh/Trung trong app chỉ giữ mục mới nhất; CHANGELOG.md giữ lịch sử.
- Build source công khai riêng trong `artifacts/release-3.26.1126.1/source/`: cập nhật EPUB local, mục lục nhiều cấp, Fade/Parallax, backup mẫu và Hồng đăng. Giữ Lord of Mysteries làm mặc định bản công khai; không đưa lại từ điển, Asuka/Doraemon/Minecraft hoặc CI/Worker/thay đổi riêng.
- Build JVM + release đạt (10m4s), bao gồm lint-vital. 1.691 tests, 0 failures, 0 errors, 12 skipped do dữ liệu tùy chọn không có trong bản công khai. Bộ kiểm tra locale và 9 test của checker đạt.
- APK `io.legado.app.rqt.Archive`, 3.26.1126.1 (12035), minSdk21/targetSdk36, không debuggable, chữ ký SHA-256 khớp bản trước. Native đạt căn chỉnh 16KB. Có asset Hồng đăng đúng byte và không kèm asset cá nhân bị loại.
- APK: `artifacts/release-3.26.1126.1/legado-R-qt_app_3.26.1126.1.apk`, 48.685.430 byte; SHA-256 `62ea7339c54f8fdb4592f99856cfce6ad58584a86bc26ab7f29b4bc9b5c32e35`. Chi tiết ở verification.json cùng thư mục.
- Xác minh 20 file dirty riêng nguyên byte so với đầu lượt phát hành. Catalog/test/PUBLICATION công khai được chuẩn bị riêng; không ghi đè workspace cá nhân.
- Repo không có PR mở; chuẩn bị tích hợp trực tiếp main. Chưa coi APK release là đã nghiệm thu thiết bị; app debug/test vẫn đã gỡ theo yêu cầu, chưa cài release hoặc nghiệm thu Hồng đăng trên điện thoại.

- Đã commit bản phát hành tại `d90101b50c3923270f1bef444d044c48d7156c23`, push lên origin/main và gắn tag `v3.26.1126.1` đúng commit. Không có PR cần merge riêng; mã đã tích hợp trực tiếp main.
- Đã phát hành [v3.26.1126.1](https://github.com/duongden/legado-R-qt/releases/tag/v3.26.1126.1), latest, không draft/prerelease. APK và SHA256SUMS trên GitHub khớp digest/kích thước local; đã tải lại đầy đủ cả hai file và đối chiếu SHA-256 đạt.
- Đã dọn snapshot source/build/cache/test temp và bản tải lại; xóa APK release cũ sau khi bản mới được xác minh. Giữ APK chính thức mới, checksum và hồ sơ kiểm chứng gọn. Changelog nháp đã hợp nhất vào CHANGELOG.md và log app, file nháp đã dọn.
- Không cài lại app debug/test hoặc cài APK release trên điện thoại trong đợt phát hành này. Nghiệm thu Hồng đăng trên thiết bị vẫn chưa được thực hiện.


## Đợt 16 — EPUB đầy đủ sáu mẫu và sửa Hồng Đăng (2026-11-26)

- Người dùng xác nhận “Đưa đủ 6 mẫu vào bản chính thức”; phạm vi này thay thế quy tắc loại ba mẫu Minecraft/Asuka/Doraemon cũ. Chỉ khôi phục các JSON mẫu và ghi nhận nguồn/notice có sẵn; dữ liệu từ điển, mã riêng và thông tin cá nhân vẫn không phát hành. Giữ Lord of Mysteries làm mặc định bản chính thức, không reset lựa chọn từng sách.
- Phân trang: tái hiện lỗi với nguồn EPUB lồng nhau có whitespace. Nhánh Paged bỏ các whitespace tại ranh giới trang, làm guard toàn vẹn báo thiếu/đổi thứ tự. Giữ lại đúng source Range theo break token Paged đã đo và tiếp tục kiểm tra overflow/toàn vẹn, không bỏ guard hoặc bỏ chữ.
- Sáng/tối: truyền readerTemplateNightMode tới mọi lượt render, đưa light/dark vào khóa template/frame cache, host và root iframe nhận cùng preference. Hồng Đăng ưu tiên data-reader-color-scheme, vẫn fallback prefers-color-scheme khi host cũ không gửi preference; không phụ thuộc theme Presentation riêng của renderer ảnh chuyển trang.
- Kiểm tra: 31/31 browser source-map regressions đạt, gồm mất chữ/ảnh vẫn bị từ chối; sáu mẫu đều bảo toàn 30.539 ký tự ở fixture formatted. Hai ca sáng/tối ngược môi trường đều giữ đúng màu ở trang đầu/trang sau.
- Đúng sách báo lỗi: adapter sản xuất xuất 285 phần có chữ và một trang bìa. Browser kiểm tra toàn bộ 286 phần bằng Hồng Đăng đạt, không lệch nội dung; fixture sách riêng bị Git bỏ qua, không đưa vào test/source công khai.
- Debug + AndroidTest build đạt; 25 JVM liên quan đạt. Cài debug giữ dữ liệu. Native luồng EPUB formatted đạt 21,535s; lượt dùng bản sao Hồng Đăng thật đạt 29,628s, gồm Fade/Parallax và đổi ngay trong cùng Activity từ mẫu sang bố cục gốc qua nút UI rồi sang mẫu dọc qua cùng sự kiện UP_CONFIG. Ảnh cho thấy trang đầu và trang sau Hồng Đăng đều tối. Test chỉ tạo/xóa fixture và bản sao mẫu riêng.
- Chuẩn bị APK chính thức 3.26.1126.2 (12036) từ source công khai tách biệt; chưa coi đó là APK đã cài/kiểm thử trên thiết bị. Hồ sơ sửa lỗi: artifacts/fix-epub-20261126; hồ sơ phát hành: artifacts/release-3.26.1126.2.

- Người dùng đã thử bản debug sửa lỗi với sách trong ảnh, lật trang Hồng Đăng và đổi mẫu, xác nhận “Đã thử, hết lỗi”.

- Build biến thể release và toàn bộ testAppReleaseUnitTest đạt (6m22s), bao gồm lint-vital: 1.692 tests, 0 lỗi, 12 skipped do dữ liệu tùy chọn. APK đúng io.legado.app.rqt.Archive, 3.26.1126.2 (12036), min21/target36, không debuggable; chữ ký khớp bản trước và 12 thư viện native đạt 16KB.
- Đã xác minh đủ sáu mẫu tích hợp và asset JSON tương ứng, runtime/Hồng Đăng trong APK đúng byte đã kiểm thử; dữ liệu từ điển bị loại. APK 48.991.484 byte, SHA-256 `02bfab5734ea2053555c5be91364309ef0d48a8680e206976a03cd65d2279e32`. Chuẩn bị commit/push/tag/phát hành hotfix; chưa cài APK release lên điện thoại.
