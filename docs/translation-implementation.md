> Ghi chú bản public: tài liệu dưới đây ghi lại quá trình triển khai trước đây.
> Bản repo này không kèm từ điển hoặc Rule.txt; xem [phạm vi phát hành](PUBLICATION.md).

# Tiến độ dịch Trung → Việt — 2026-10-02

Tham chiếu: [kế hoạch](chinese-vietnamese-translation-plan.md). Nguồn port là checkout
`../legado-qt`; chỉ legado-R được chỉnh sửa.

## Đã đưa vào code

- Lõi VietPhrase của QT, DAT memory-mapped và ba từ điển mặc định (81.269.727 byte,
  khoảng 77,5 MiB trước nén). Ba file khớp SHA-256 với nguồn QT.
- Công tắc mặc định tắt trong thiết lập khác; nhập/khôi phục riêng Names, VietPhrase,
  phiên âm. Nhập TXT giữ nghĩa đầu tiên, lọc nhiễu tiêu đề chương, chuẩn bị và kiểm tra
  trước khi chuyển file đánh dấu thế hệ đang dùng bằng `AtomicFile`.
- Rule.txt mặc định là nguyên bộ **v21 — NO NE:PN**, 633 rule; SHA-256
  `c4b06d49598d603072dc2a8f42d1cfa5928538582c6d4ae6439ab013a912dd21` khớp file
  người dùng chỉ định trong `vbookVP/TTV/Rule-TXT`. Không dùng v20 hoặc Pronouns.
  Engine Kotlin chỉ nhận `<n>`, `<y>`, `<L>`, range, nhóm thay thế/tùy chọn và
  placeholder; tham khảo `duong-den-vietphrase-ext-v15/rule-engine.js` (GPL-3.0).
- Rule chạy trên đoạn text trước tra từ điển; giữ kết quả thành khối. VietPhrase
  bằng/dài hơn đoạn khớp có quyền phủ quyết. Chuẩn hóa dấu câu sau khi khớp để
  giữ rule có dấu ngoặc/nháy; dấu phẩy mệnh đề không bị hiểu là nhóm số hàng nghìn.
  Rule không khớp tiếp tục qua lõi cũ; không bổ sung rule ngoài bộ v21.
- Công tắc **Áp dụng Rule.txt** mặc định bật, chỉ tác động khi caller yêu cầu dịch.
  Trong **Quản lý Rule.txt v21**, nhập TXT thay thế toàn bộ hoặc khôi phục mặc định.
  File nhập có bất kỳ lỗi nào bị từ chối trước khi chuyển bộ đang dùng; báo dòng lỗi.
  Parser riêng giữ `/` trong phân số; rule có nhiều nghĩa ngăn bằng `¦` bị từ chối.
  Name/VietPhrase chỉ lấy nghĩa đầu qua `/` hoặc `¦`. Snapshot/revision/cache bao gồm
  rule; đổi công tắc làm mới revision và tải lại chương như đổi từ điển.
- Nạp từ điển trên IO, dịch trên Default, cache kết quả 10 MiB theo loại dịch và
  revision từ điển. Hủy tác vụ được truyền lên caller; lỗi dịch trả văn bản gốc.
- Native dịch sau thay thế, trước tạo đoạn hiển thị. Tiêu đề dịch qua `displayTitle`.
  Direct dịch tài liệu chung của HTML và TTS; tính lại offset ảnh, giữ ID/URL/click.
- Revision dịch tham gia khóa layout, Direct và xử lý đoạn. Đổi trạng thái/từ điển
  tạm dừng TTS, hủy nội dung cũ và tải lại đầu chương hiện tại.
- Dịch bất đồng bộ tại lớp hiển thị tủ sách, tìm kiếm/khám phá, chi tiết và mục lục.
  Model/database giữ nguyên; các đường sửa nội dung và chèn/xóa ảnh dùng bản gốc.
- HTTP/WebSocket hỗ trợ yêu cầu dịch độc lập công tắc Android; web có công tắc mặc
  định tắt, bỏ dữ liệu request cũ khi đổi trạng thái và tải lại nội dung.
- Web gửi thêm `bookUrl` khi lưu tiến độ, để server dùng định danh và tiêu đề gốc.
  Không có migration database. Nội dung EPUB thật, manga và video ngoài phạm vi.

## Kiểm tra đã thực hiện

- **35 kiểm thử JVM độc lập đã đạt:** khớp cụm dài nhất, ưu tiên Names, nghĩa đầu tiên, phiên âm/chữ lạ,
  token bỏ qua, dấu câu, tiêu đề chương, HTML/thuộc tính/script, hủy quét/build,
  DAT round-trip/lỗi/asset thật, offset ảnh và parser TXT.
- Các kiểm thử rule nạp đủ 633 mục, kiểm tra parser/cancel/phân số/ưu tiên/phủ quyết,
  số có dấu phẩy và mệnh đề kế tiếp, HTML/ảnh và tiêu đề chương. Fixture tham khảo
  đối chiếu **770 vị trí** từ corpus `test-risk-rules-random.txt` và các ca bổ sung,
  với engine extension và từ điển rỗng. Bộ v21 không phủ mọi biểu thức số: ví dụ
  `一千零八十万` và `3.5亿` đứng riêng không có rule khớp, nên dùng flow từ điển cũ.
- Harness JVM tạm dùng nguồn loader/DictManager/TranslateUtils thật, bộ từ điển thật,
  nhưng Context/ContentResolver/AtomicFile là test doubles dựa trên filesystem:
  đã kiểm tra nhập hợp lệ, nhập lỗi giữ pointer/snapshot/cache cũ, bật/tắt rule,
  revision/cache, khôi phục 633 rule và dấu `/`. Đây không phải runtime Android.
- Web: Vite production build và `vue-tsc`; tài nguyên web được đồng bộ vào app.
- Lõi, loader và quản lý từ điển được kiểm tra biên dịch riêng với Android API và
  stub cho interface của ứng dụng. Đây không phải kiểm tra biên dịch toàn app.

Lệnh JVM không cần Android SDK:

```sh
JAVA_HOME=/path/to/jdk17 python3 tools/check_translation_jvm.py
```

Script tải Kotlin compiler/JUnit vào cache tạm. Kiểm tra Android khi môi trường sẵn sàng:

```sh
./gradlew :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppDebug
```

## Chưa nghiệm thu

- Gradle compile/tests/lint/APK: wrapper đang bị tiến trình khác khóa tải Gradle;
  tải bản riêng vào thư mục tạm bị timeout. SDK hiện có Platform 37 và Build-Tools 36,
  nhưng còn thiếu Platform 36 mà project yêu cầu. Chưa có APK để đo kích thước.
- Runtime Android: nhập/khôi phục từ điển, lỗi và thay từ điển đồng thời, native/Direct,
  ảnh/click, TTS, đổi sách nhanh, hiệu năng chương dài và hồi quy EPUB thật.
- API trên server chạy thực và kiểm tra giao diện web trong trình duyệt.

Code hiện là bản triển khai cần tiếp tục nghiệm thu, chưa phải bản đã xác nhận trên thiết bị.

## Sửa lỗi nhập TXT trên Android — 2026-10-02

- Log từ Samsung SM-M515F / Android 12 cho thấy `TranslationRules` khởi tạo lỗi:
  ICU của Android từ chối regex placeholder có dấu `}` chưa escape. Vì loader luôn
  nạp Rule.txt cùng snapshot, lỗi này cũng làm nhập VietPhrase thất bại.
- Đã escape cả hai dấu ngoặc của placeholder. Kiểm tra bằng ICU native 78 trên macOS
  tái hiện mẫu cũ bị từ chối và mẫu mới được chấp nhận; 35 kiểm thử JVM vẫn đạt.
- Thêm `TranslationRulesAndroidTest` kiểm tra khởi tạo engine, thay placeholder,
  phân số và nạp đủ 633 rule bằng regex Android. Chưa chạy instrumentation vì adb
  không có thiết bị kết nối. Kiểm tra ICU trên macOS không thay thế kiểm tra thiết bị.
- Đã thử `compileAppDebugKotlin` offline, gồm lần chỉ định heap Kotlin 3 GB; compiler
  bị OutOfMemoryError khi sinh mã Compose ở `AiToolPreviewDialog`. Chưa xác nhận
  biên dịch toàn app và chưa tạo APK mới trong lượt sửa lỗi này.
- Lỗi cập nhật dữ liệu dịch nay được lưu vào AppLog và hiện hộp thoại có tên lỗi,
  thông báo và nguyên nhân lồng nhau, thay cho toast ngắn.
- Build/cài cập nhật bằng cùng application ID và cùng keystore của APK đã cài,
  rồi thử nhập lại VietPhrase. Không cần xóa dữ liệu để cập nhật bản sửa lỗi.
