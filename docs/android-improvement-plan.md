# Cải thiện Android của legado-R, giữ nguyên API và Rhino

Ngày lập: 2026-10-17. Phạm vi: Android của checkout legado-R hiện tại.

## Mục tiêu và ràng buộc

Cải thiện độ ổn định, hiệu năng và khả năng bảo trì; giữ dịch Trung–Việt,
EPUB Direct, mẫu trang và AI/TTS. Các phát hiện ban đầu dựa trên mã nguồn;
hiệu năng và hành vi nền cần được xác minh trên thiết bị.

- Giữ Mozilla Rhino 1.8.1, lớp tích hợp và cách tương tác Java–JavaScript.
- Giữ minSdk 21, compileSdk 36, targetSdk 36, Java 17 và dependency hiện tại.
- Giữ tên hàm, tham số, kiểu trả về, hành vi của java/source/book/cache.
- Giữ HTTP/WebSocket API, Content Provider, deep link và định dạng nhập/xuất.
- Không đổi package, schema database, dữ liệu sách hoặc định dạng sao lưu.
- Bảo toàn thay đổi chưa commit; không chuyển sang QuickJS, HtmlUnit Rhino hoặc KMP.

## Các vấn đề và thay đổi

### A. Vòng đời WebView — ưu tiên cao

`release()` lặp sau reset có thể để lại instance đã hủy trong idle pool.
Làm release idempotent; loại instance khỏi mọi danh sách trước khi hủy;
chỉ nhận callback đúng lượt reset; thao tác WebView trên Main.
Giữ pool Discovery/RSS, timeout và timer riêng từng instance.

### B. Ngôn ngữ giao diện — ưu tiên cao

Việt hóa hiện phụ thuộc cả công tắc dịch truyện. Chỉ dùng ngôn ngữ UI hiệu lực:
lựa chọn ứng dụng hoặc hệ thống khi chọn theo hệ thống. Cache phân biệt ngôn ngữ
và revision từ điển; đổi ngôn ngữ cập nhật nhãn; callback nguồn nhận giá trị gốc.

### C. Database trên luồng UI — ưu tiên cao

Rà soát DAO trong Activity/Fragment/Compose/callback. Chuyển truy vấn sang IO,
suspend hoặc Flow; dùng snapshot chương của phiên đọc; bỏ kết quả cũ khi đổi sách.
Giữ đường đồng bộ mà Rhino/API dùng trên worker. Chỉ bỏ allowMainThreadQueries
khi đã chuyển hết điểm gọi UI phụ thuộc; không đổi schema.

### D. Ngân sách EPUB low-RAM — ưu tiên cao

Smart hiện chọn Smooth cả trên low-RAM (4 WebView preload, 9 frame).
Đo đường cơ sở; Smart trên low-RAM dùng PowerSave hiện có; giữ Smart trên máy khác
và lựa chọn thủ công. Giải phóng preload/frame tái tạo được khi rời phiên đọc
hoặc giảm bộ nhớ; giữ vị trí, phân trang, nội dung và mẫu.

### E. Tách trách nhiệm — ưu tiên trung bình

Theo thứ tự: quản lý WebView/preload/cache EPUB; tải chương/phiên đọc/vị trí;
trạng thái và điều khiển TTS; tải dữ liệu Discovery. Giữ các lớp cũ làm điểm
điều phối, giữ điểm gọi bên ngoài, refactor từng nhóm với kiểm thử hồi quy.

### F. Resource tiếng Việt — ưu tiên trung bình

Đối chiếu ban đầu có 288 khóa chứa chữ Trung chưa có resource tiếng Việt.
Hoàn thiện Android resources, ưu tiên đọc/cài đặt/nguồn/từ điển/TTS; giữ placeholder,
markup và giá trị kỹ thuật. Nhãn ứng dụng dùng resource; nhãn nguồn chỉ dịch hiển thị.
Bổ sung kiểm tra khóa thiếu và placeholder.

### G. Hiệu năng và dịch vụ nền — ưu tiên trung bình

Hành trình cố định: khởi động, tủ sách, TXT/EPUB, chuyển chương, dịch và TTS.
Đo thời gian, frame chậm và bộ nhớ trên cùng thiết bị/dữ liệu. Kiểm tra lịch sau
reboot, vào nền, dừng dịch vụ và timeout; sửa lỗi tái hiện được. Dùng hạ tầng hiện có;
không thay scheduler hàng loạt hay thêm Baseline Profile plugin trong đợt này.

## Tiếp thu từ hai repo

| Nguồn | Nội dung | Áp dụng |
| --- | --- | --- |
| legado | Tương thích nguồn | Tình huống JSONPath, Java objects, overload, crypto, HTTP trên Rhino hiện tại |
| legado | Vòng đời WebView | Release idempotent, callback đúng lượt, giữ scope của R |
| legado | Policy native | Tách điều phối chương, cache và TTS khỏi Activity |
| legado | Backup/tác vụ nền | Tình huống kiểm thử phù hợp dữ liệu và scheduler hiện tại |
| legado-2 | Phân lớp | Tách dữ liệu/nghiệp vụ/UI trong Android, chưa chuyển KMP |
| legado-2 | Khả năng Android | Interface nội bộ cho quyền, lifecycle và dịch vụ khi cần |
| legado-2 | Đo hiệu năng | Hành trình tủ sách/đọc/sửa nguồn, thêm TXT dịch, Direct và TTS |
| legado-2 | Baseline Profile | Đợt sau khi có số đo và tooling tương thích |

PDF mới, highlight/ghi chú khác, cache âm thanh mới và MCP ngoài đợt này.

## Thứ tự triển khai và nghiệm thu

1. Sửa WebView, ngôn ngữ, cache nhãn và kiểm thử hồi quy.
2. Đo cơ sở, giảm DAO trên UI, điều chỉnh EPUB low-RAM.
3. Tách trách nhiệm EPUB/phiên đọc/TTS/Discovery và hoàn thiện resource.
4. Kiểm thử nguồn, dữ liệu, dịch vụ nền và đối chiếu số đo.

- WebView: release lặp trước/sau reset, callback muộn, timeout, mở lại scope.
- Ngôn ngữ: Việt/Anh/Trung/theo hệ thống × bật/tắt dịch truyện.
- Rhino: tìm kiếm/chi tiết/mục lục/nội dung/đăng nhập/crypto/callback.
- Đọc: TXT native/Direct, EPUB, đổi sách nhanh, giữ vị trí.
- TTS: phát/tạm dừng/chuyển chương/vào nền giữ đúng phiên.
- Dữ liệu: schema 113, từ điển, mẫu, backup/restore.
- Thiết bị: API 21, 23/24, 36 và thiết bị vật lý low-RAM cho hiệu năng.
- Build debug và release tối ưu; kiểm tra Rhino trong release.
- Ghi rõ kiểm tra chưa chạy; không coi JVM hoặc build là bằng chứng thiết bị.

Kết thúc ở build đã kiểm chứng; không bao gồm commit/push/phát hành.
Tiến độ và bằng chứng ghi riêng trong `android-improvement-validation.md`.
