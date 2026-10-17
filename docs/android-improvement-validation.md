# Kết quả triển khai cải thiện Android

Ngày: 2026-10-17. Kế hoạch: [android-improvement-plan.md](android-improvement-plan.md).
Checkout: legado-R. Các bằng chứng 3.26.1016.1 bên dưới thuộc lượt kiểm thử plan; xem bản release 3.26.1017.1 ở cuối tài liệu.

## Thay đổi đã triển khai

| Mục | Kết quả và giới hạn |
| --- | --- |
| A — WebView | Release lặp không hủy lease đang reset/idle; hủy loại khỏi mọi pool, vô hiệu callback cũ; thao tác native trên Main. Giữ scope/timer riêng. |
| B — Ngôn ngữ | UI dùng ngôn ngữ ứng dụng/hệ thống, độc lập công tắc dịch truyện. Cache có language/revision; nhãn View và Compose cập nhật khi đổi ngôn ngữ. Không đổi giá trị callback nguồn. |
| C — Database | Thêm Flow mục lục và snapshot riêng cho phiên đọc; điều phối yêu cầu theo sách/revision, hủy yêu cầu cũ. Truy vấn cấu hình khởi động, đổi nguồn chương, mua chương và thời gian đọc để chia sẻ chuyển sang IO. Các đường đồng bộ công khai giữ nguyên. |
| D — EPUB | Smart trên low-RAM dùng PowerSave; Smart trên máy thường và lựa chọn thủ công giữ nguyên. Giữ đường trimMemory/lifecycle đã có để dọn tài nguyên tái tạo được. |
| E — Phân lớp | Tách registry preload EPUB, repository/coordinator chương, controller trạng thái TTS và repository Discovery. Không thay engine TTS hoặc thuật toán phân trang. |
| F — Tiếng Việt | Bổ sung 283 resource có thể dịch. 5 khóa kỹ thuật translatable=false trong đối chiếu ban đầu được giữ nguyên. Thêm kiểm tra khóa thiếu, trùng và placeholder. |
| G — Đo/kiểm chứng | Có đường cơ sở launcher, test hành trình native/Direct và kiểm chứng nguồn trên R8. Ma trận hệ điều hành, live TTS, reboot và frame chậm còn chưa hoàn tất; xem bên dưới. |

`allowMainThreadQueries()` vẫn được giữ: inventory ghi 740 tham chiếu DAO rõ ràng
trong UI, không chứng minh tất cả chạy trên Main và không bao gồm truy vấn ẩn qua helper/entity.
Chưa chuyển toàn bộ các màn hình sang IO/Flow; không đủ điều kiện bỏ cờ trên database toàn cục.
Xem [inventory](android-ui-dao-inventory.md). Không coi mục C đã hoàn tất trên toàn ứng dụng.

## Hợp đồng giữ nguyên

- Mozilla Rhino 1.8.1 và modules/rhino không đổi.
- minSdk 21, compileSdk/targetSdk 36, Java source 17, dependency và package không đổi.
- Schema Room 113; entity, migration và schema JSON không đổi.
- Không sửa lớp API HTTP/WebSocket, Provider/deep link, lớp tích hợp Rhino hay định dạng nhập/xuất.
- Đường mua chương vẫn gọi JS nguồn cũ; chỉ chuyển truy vấn chương sang IO trước khi mở dialog.
- Các thay đổi có sẵn ở từ điển/dịch, template EPUB, Gradle wrapper và công cụ phát hành được giữ lại.

## Bằng chứng kiểm thử

Thiết bị thật: Samsung SM-M515F, Android 12/API 31; app debug, giữ dữ liệu hiện có.
Fixture nguồn dùng localhost và dữ liệu riêng, dọn dữ liệu fixture và khôi phục cài đặt sau test.

| Kiểm tra | Kết quả |
| --- | --- |
| Toàn bộ JVM | 1.580 test, 0 failure, 0 error, 3 skipped. |
| Build thường | assembleAppDebug, assembleAppDebugAndroidTest, assembleAppRelease, testAppDebugUnitTest thành công. |
| Resource | 2.887 default / 2.402 Vietnamese; không lỗi thiếu khóa chữ Trung có thể dịch, trùng hoặc placeholder. |
| Native 16 KB | 12 thư viện arm64-v8a/x86_64 qua kiểm tra; 0 lỗi. |
| Android hồi quy | 20/20 (lượt cuối chạy chung với EPUB: 21/21): WebView 4, UI language 8, Rhino 2, source entry 2, Room 1, dịch 2, migration tác vụ 1. |
| EPUB tài nguyên | 1/1: ảnh, background, CSS import lồng nhau, font đường dẫn Unicode; hai chương và dọn snapshot handoff lỗi. |
| Hành trình native/Direct | Đã qua mỗi engine một test mở sách, chờ nội dung, chuyển chương tới/lui và lưu tiến độ; số đo ghi riêng bên dưới. |
| Rhino sau R8 | 2/2 qua BookSource.evalJS: bindings Java/source/baseUrl, JS object, crypto AES UTF-8. |
| Bình luận / mở lại reader | Chưa đạt fixture hiện có: hai test timeout khi chờ STABILITY_BODY trong document.body. Lượt đối chiếu một test trên APK đường cơ sở cũng timeout cùng điều kiện. Cả hai APK đã mở đúng sách, 3 chương, Direct visible và 15 trang; chưa chạy tới thao tác bình luận. |

Cấu hình release của repo hiện không bật minify, được giữ nguyên. Kiểm chứng R8
dùng init script ngoài repo cho debug và giữ Kotlin/coroutines để test APK dùng runtime
của target. Không thay ProGuard production. Đây là kiểm chứng có phạm vi, không phải
bằng chứng toàn bộ release tối ưu tương thích mọi nguồn.

Lần thử gọi trực tiếp `RhinoScriptEngine.eval(String)` từ test APK gặp NoSuchMethodError
vì R8 loại overload không được production sử dụng. Hai test qua entry point nguồn
production ở trên vẫn qua. Không sửa engine hoặc mở rộng keep rules để che kết quả này.

Fixture EPUB được sửa để đọc `location.href`: trên WebView của thiết bị,
`getUrl()` có thể là about:blank khi loadDataWithBaseURL dùng historyUrl=null.
Giữ nguyên các assertion tài nguyên/render. Những lần chạy lúc máy khóa/mất ADB
không được tính là test thành công.

## Số đo quan sát

Số liệu đầy đủ: [android-improvement-metrics.json](android-improvement-metrics.json).
Mỗi bộ launcher có 3 lần force-stop + am start -W, cùng debug package, không xóa dữ liệu.

| Bộ đo | Median TotalTime | PSS sau bộ đo |
| --- | ---: | ---: |
| Baseline ban đầu | 4.781 ms | 349.082 KB |
| Bản mới ban đầu | 5.064 ms | 352.352 KB |
| Baseline đối chiếu lại | 5.030 ms | 354.826 KB |
| Bản mới đối chiếu lại | 5.115 ms | 354.129 KB |

Lượt đối chiếu lại chênh +85 ms (khoảng 1,7%); các lượt riêng biến động từ
4.729 đến 5.677 ms. Chưa có bằng chứng tăng tốc hoặc đủ mẫu để kết luận hồi quy.
PSS là snapshot sau launcher, không phải peak bộ nhớ của reader. Bản kiểm chứng
R8 có kết quả riêng nhưng khác cấu hình, không dùng để khẳng định lợi ích của patch.

Một mẫu nguồn thử: native mở/chuyển chương 2.358/813 ms; Direct 4.045/1.071 ms.
Thời gian gồm chờ readiness trong fixture, không phải số đo frame hoặc benchmark
so sánh hai engine. Chưa có mẫu trước/sau reader tương ứng.

## Chưa nghiệm thu đầy đủ

- API 21, 23/24, 36 và thiết bị vật lý low-RAM chưa có bằng chứng chạy.
- Policy low-RAM đã có JVM test; chưa đo bộ nhớ/độ mượt trên máy low-RAM thật.
- Live TTS phát/tạm dừng/chuyển chương/vào nền và lịch sau reboot chưa chạy.
  Controller có JVM test; migration tác vụ có Android test, không thay thế các kịch bản này.
- Chưa có số đo frame chậm, thống kê hiệu năng reader trước/sau cùng fixture,
  backup/restore trọn bộ hoặc đăng nhập nguồn có xác thực. Không dùng tài khoản/token cá nhân để thử.
- Chưa bỏ allowMainThreadQueries; các truy vấn UI ngoài nhóm reader/discovery đã sửa cần tiếp tục rà soát.
- Fixture TextReaderCommentReentryTest cần hỗ trợ đường template hiện tại: nội dung ở
  iframe được cách ly, không nằm trong document.body của host. Không bỏ assertion hoặc
  thay lớp tích hợp template chỉ để làm test qua; giữ chẩn đoán và cờ giữ màn hình sáng
  trong fixture. Chưa có bằng chứng hành trình bình luận qua reader này.

## Tái lập

```sh
./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:assembleAppRelease :app:testAppDebugUnitTest --offline
python3 tools/check_android_locales.py
python3 tools/check_apk_16kb.py app/build/outputs/apk/app/release/legado-R-qt_app_3.26.1016.1.apk
python3 tools/audit_android_ui_queries.py --output docs/android-ui-dao-inventory.md
python3 tools/measure_android_startup.py --adb /path/to/adb --runs 3 --output /tmp/startup.json
```

Gradle chạy bằng JBR 21 trên Mac này, không đổi Java source compatibility 17.
Android instrumentation dùng runner `io.legado.app.rqt.debug.test/androidx.test.runner.AndroidJUnitRunner`;
chỉ định class và giữ điện thoại mở khóa/sáng màn hình. Hành trình fixture dùng
`DiscoveryLanTest#discoveryToBookshelfReaderAndProgress`, `readerEngine=native|epub`.

## Artifact cục bộ

- Debug sau sửa TXT: `app/build/outputs/apk/app/debug/legado-R-qt_app_3.26.1016.1.apk`; SHA-256 `a44b0f154a0467eb5e3ff3c2eba7bc1f59de00983e3a5e55654e69961151fc80`.
- Release: `app/build/outputs/apk/app/release/legado-R-qt_app_3.26.1016.1.apk`; SHA-256 `8dc99f164c6c759c837275e71e1b5c91ceefcdc597e41e10218d046fd4929af3`.

Log kiểm chứng trong `/private/tmp/legado-android-*.log`; các file tạm này không được commit.

## Sửa lỗi mở TXT local (2026-10-17)

- Samsung hiển thị `LoadTocError: missing ; before statement` khi tự tạo mục lục. Quy tắc đã nhập có replacement bắt đầu bằng `@js:`; `TextFile` đưa nguyên tiền tố vào Rhino.
- Chuẩn hóa tiền tố `@js:` trước khi chạy replacement, giữ nguyên script không có tiền tố và các bindings/API/Rhino 1.8.1. Khi tự dò, bỏ qua ứng viên có lỗi script và tiếp tục quy tắc khác; quy tắc người dùng chọn trực tiếp vẫn báo lỗi nếu script sai.
- Build debug thành công; 2 JVM tests qua. Trên Samsung SM-M515F/API 31, 2 Android tests qua: nhập TXT, chạy replacement có tiền tố, tạo mục lục và mở Direct reader; quy tắc tự dò hỏng không chặn quy tắc hợp lệ. Fixture được dọn và thiết lập engine được khôi phục.
- Đã cài APK debug sửa lỗi trên Samsung. Lần mở lại sách người dùng trả về `Status: ok`, nhưng màn hình chụp sau đó ở launcher nên chưa đủ bằng chứng xác nhận nội dung của sách đó hiển thị. Không phát hành hoặc commit/push trong bước này.

## Release 3.26.1017.1 (12033)

Changelog: `fix bugs`. Tất cả updateLog.md đóng gói chỉ chứa bản mới nhất.
Release được dựng từ HEAD và các thay đổi Android đã chọn trong thư mục riêng,
không đóng gói từ điển riêng hoặc artwork Asuka/Doraemon/Minecraft.
Các thay đổi local phụ thuộc các tài nguyên riêng, CI, relay và Gradle wrapper
không nằm trong commit này. Các bản test thiết bị nêu trên dùng checkout local;
không coi đó là kiểm chứng runtime đầy đủ cho APK release public.

Kết quả bản source sạch:

- `testAppDebugUnitTest` và `assembleAppRelease` (gồm lintVital): thành công.
- JVM: 1.582 test, 0 failure/error, 12 skipped (dữ liệu tùy chọn và live DNS).
- Package `io.legado.app.rqt.Archive`, version 3.26.1017.1 / 12033, minSdk 21, targetSdk 36; không debuggable.
- Chữ ký khớp APK public v3.26.1016.1 đã tải và đối chiếu checksum.
- 12 thư viện native qua kiểm tra 16 KB. Payload không chứa dữ liệu từ điển hay artwork riêng; cả 7 updateLog chỉ có bản mới nhất.
- APK: `app/build/outputs/apk/app/release/legado-R-qt_app_3.26.1017.1.apk`.
- SHA-256: `858e3d935ab093bb40300631e760589bd6b11ed4f7cc7ae25e9a9962e1fa5431`.
- Lượt này build release và tích hợp source lên main; không cài APK release lên thiết bị hoặc tạo GitHub Release.
