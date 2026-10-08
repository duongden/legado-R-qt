# 08/10/2026 · Build 12030

Phiên bản **3.26.1008.2** · Android 5.0+

### Tải bản cập nhật

- Tải APK bằng HTTP của ứng dụng, tránh lỗi tải từ DownloadManager trên một số thiết bị.
- Kiểm tra số byte tải về và package APK trước khi mở trình cài đặt Android.
- Cho phép thử lại sau lỗi tải; giữ thông tin lỗi không chứa URL hoặc header riêng tư.
- Giữ cùng khóa ký để cài đè bản cũ và bảo toàn dữ liệu.

Nếu bản cũ báo tải xuống thất bại, tải APK từ GitHub và cài đè một lần để nhận bản sửa. Các lần cập nhật tiếp theo dùng luồng tải mới. Không gỡ ứng dụng cũ.
