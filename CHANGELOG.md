# Lịch sử thay đổi R-qt


## 3.26.1008.2 — 08/10/2026 · Build 12030

Phiên bản **3.26.1008.2** · Android 5.0+

### Tải bản cập nhật

- Tải APK bằng HTTP của ứng dụng, tránh lỗi tải từ DownloadManager trên một số thiết bị.
- Kiểm tra số byte tải về và package APK trước khi mở trình cài đặt Android.
- Cho phép thử lại sau lỗi tải; giữ thông tin lỗi không chứa URL hoặc header riêng tư.
- Giữ cùng khóa ký để cài đè bản cũ và bảo toàn dữ liệu.

Nếu bản cũ báo tải xuống thất bại, tải APK từ GitHub và cài đè một lần để nhận bản sửa. Các lần cập nhật tiếp theo dùng luồng tải mới. Không gỡ ứng dụng cũ.

## 3.26.1008.1 — 08/10/2026 · Build 12029

### Video và dịch giao diện

- Giữ loại video/âm thanh/truyện tranh do script hoặc người dùng xác định khi tải danh sách tập, tránh mở video bằng màn hình đọc chữ.
- Đưa tên phim, tên tập, thông tin nguồn và giới thiệu HTML/Markdown qua luồng dịch Trung–Việt; giữ nguyên dữ liệu nguồn và URL phát.
- Dịch metadata thời lượng trong mục lục và Việt hóa nhãn điều khiển, hộp chọn tập của trình phát.

### Độ ổn định và dữ liệu

- Chặn callback tủ sách truy cập view đã bị hủy; đưa xử lý đoạn chọn Ask AI ra luồng nền và giữ trạng thái yêu cầu AI nhất quán khi hủy.
- Lưu crash log vào bộ nhớ nội bộ trước, hiển thị trong màn hình nhật ký và giới hạn số báo cáo được giữ lại.
- Đổi tên sách/tác giả cập nhật dấu trang trong cùng giao dịch; tìm dấu trang không lẫn kết quả của sách khác.
- Kiểm tra nguồn HTTPS dùng cổng 443; bỏ qua kiểm tra host cho định danh không phải HTTP(S) và tiếp tục kiểm tra quy tắc nguồn.
- Nhập danh sách sách báo riêng số thành công, thất bại và bỏ qua; tìm kiếm và lưu sách chạy ở luồng nền.

### Build

- Bản debug dùng khóa debug Android, không phụ thuộc mật khẩu ký release.
- Build RTMP JNI từ mã nguồn với căn chỉnh 16 KB; bổ sung công cụ kiểm tra ELF/RELRO và căn chỉnh ZIP trong APK.

## 3.26.1007.1 — 07/10/2026 · Build 12028

### Ask AI và trợ lý AI

- Cải thiện lấy nguyên văn của đoạn được chọn khi hỏi AI trong truyện đã bật dịch.
- Bổ sung chỉ dẫn ngôn ngữ trả lời theo ngôn ngữ giao diện Việt, Trung hoặc Anh.
- Điều chỉnh vị trí và thao tác kéo cửa sổ Ask AI sau khi phóng to, thu nhỏ.
- Cho phép hiển thị tên truyện đã dịch ở phụ đề Ask AI khi bật dịch truyện.
- Bổ sung nhãn tiếng Việt trong cài đặt, menu, công cụ và các màn hình con của trợ lý AI.
- Thêm ba phiên bản kỹ năng tích hợp: tiếng Trung, tiếng Việt và tiếng Anh để người dùng lựa chọn.

### Giao diện đọc sách

- Thêm bóng chữ trên thông tin chi tiết sách để tăng độ dễ đọc.
- Các ô thao tác tự chia đều chiều rộng, tối đa ba ô mỗi hàng; ô tiếp theo xuống hàng mới.
- Sửa nhãn Sáng/Tối tương ứng với biểu tượng chuyển chế độ.
- Việt hóa tên các nguồn tra cứu trong hộp thoại Từ điển.

## 3.26.1005.1 — 05/10/2026

Bản cập nhật tập trung vào giao diện tiếng Việt, Web Service LAN và kênh cập nhật riêng của R-qt.

### Thêm mới

- Trang Giới thiệu có liên kết mã nguồn và các bản phát hành của R-qt.
- Kiểm tra cập nhật từ GitHub `duongden/legado-R-qt`; hỗ trợ tải APK từ hộp thoại cập nhật. Có tùy chọn kiểm tra khi mở ứng dụng, tối đa một lần mỗi 6 giờ.
- Tùy chỉnh riêng màu nền, chữ và màu nhấn cho chế độ Sáng/Tối trên web; lưu trong trình duyệt, có khôi phục mặc định và kiểm tra độ tương phản trước khi áp dụng.

### Cải thiện

- Gộp màn hình mở đầu Web Service vào Tủ sách. Thanh điều hướng chung dẫn đến Nguồn sách, Gửi sách, Nguồn RSS và Trợ giúp.
- Thống nhất màu, font giao diện, viền, bo góc, trạng thái tương tác và bóng đổ giữa các màn hình web. Giữ tùy chọn font riêng của nội dung đọc.
- Bổ sung tiếng Việt cho các màn hình con trong Cài đặt AI: nhà cung cấp, cấu hình ảnh, thư viện ảnh, Sách thế giới và nhãn công cụ.
- Điều chỉnh trình sửa nguồn sách/RSS cho màn hình hẹp và làm gọn trang gửi sách.

### Sửa lỗi

- Bổ sung dịch nội dung hiển thị trên thẻ sách Discovery ở bố cục Waterfall và lưới ba cột khi bật dịch Trung–Việt.
- Sửa màu chữ, icon và trạng thái hover không theo theme ở các trang web; tăng độ phân biệt giữa viền và nền.
- Căn thanh tìm kiếm theo chiều rộng danh sách sách và căn giữa icon với chữ nhập.
- Chuyển kiểm tra cập nhật chính thức sang repo R-qt, loại bản nháp/bản thử nghiệm và APK không thuộc R-qt; so sánh phiên bản theo số.

### Cập nhật ứng dụng

Cài APK của bản này một lần để chuyển sang kênh cập nhật R-qt. Sau đó vào **Của tôi → Giới thiệu → Kiểm tra cập nhật** để kiểm tra thủ công. Khi bật tự kiểm tra, ứng dụng kiểm tra lúc mở; người dùng quyết định tải và xác nhận cài đặt bằng trình cài đặt Android.

## Lịch sử upstream

- [Các bản Archive 14–15](docs/changelog/upstream-archive-2026.md)
- [Các bản phát hành gốc](https://github.com/Rimchars/legado/releases)
