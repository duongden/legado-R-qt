# Rà giao diện dịch ngày 03-10-2026

Thiết bị: Samsung SM-A546E. Hai bản ghi do người dùng thao tác: 56 giây và 171 giây. Video và khung hình đối chiếu lưu tạm tại `/private/tmp/legado-flow-review/`.

| Flow đã ghi | Nhãn còn sót trước sửa | Phần xử lý |
| --- | --- | --- |
| Tủ sách → Bố cục | Layout, View, nhãn chọn, 未读/未启用, 间距 | Tài nguyên Việt và dịch nhãn Compose, giữ nguyên giá trị lựa chọn |
| Thống kê đọc | Tiêu đề tiếng Anh; tên sách/chương tiếng Trung | Nhãn native và Compose; chỉ thay phần hiển thị |
| Tìm kiếm → menu nguồn | Menu đã dịch; Author/Latest còn tiếng Anh | Lấy nhãn định dạng từ tài nguyên Việt; giữ nguyên truy vấn nhập |
| Của tôi | Gợi ý 搜索; nhóm/nhãn RSS, giao diện, hẹn giờ, bộ nhớ đệm tiếng Anh | Tài nguyên Việt; gợi ý tìm kiếm |
| Của tôi → Giao diện ứng dụng | Tên/mô tả giao diện có sẵn tiếng Trung; Applied | Dịch nhãn thẻ; giữ nguyên ID và cấu hình giao diện |
| Cài đặt → Mạng và DNS | Tiêu đề tiếng Trung; Advanced tiếng Anh | Tiêu đề Compose và tài nguyên Việt |
| Sao lưu/khôi phục | Backup and restore, Restore trong hộp thoại chờ | Tiêu đề và WaitDialog đi qua luồng dịch |
| Danh sách RSS | I AM OVER! | Nhãn hết kết quả dùng tài nguyên Việt |
| Khám phá nguồn 六月文学网/Tấn Giang | Tên nguồn, tab thể loại/hành động tiếng Trung; Author | Dịch thanh tiêu đề/tab hiển thị; giữ nguyên chỉ số, URL, khóa và giá trị thao tác |

Bản ghi có thông báo nguồn chưa đăng nhập: phần hiển thị đã qua dịch. Không đăng nhập hay thay đổi dữ liệu tài khoản để kiểm tra.

Kiểm tra tĩnh: không trùng tên chuỗi; số tham số định dạng khớp bản mặc định; menu XML không còn nhãn fallback chứa chữ Trung. Build/cài và đối chiếu sau sửa được ghi nhận riêng sau khi hoàn tất; không coi kiểm tra tĩnh là bằng chứng runtime.

Phạm vi không bao gồm chữ nằm trong ảnh bìa và giao diện trình chọn tệp của Android. Chuỗi nhập trong ô tìm kiếm, nội dung mã nguồn và URL giữ nguyên.

Bản 3.26.100313debug: build thành công, adb cài trả Success. Kiểm tra tủ sách sau cài phát hiện thêm nhãn thời gian tương đối (3分钟前, 6天前, 1小时前); bổ sung dịch phần hiển thị số + đơn vị + trước/sau. Những màn hình khác đang chờ mở lại để đối chiếu runtime.

Đối chiếu runtime bản đầu: màn hình Của tôi đã hiện Tìm kiếm, Tác vụ hẹn giờ, Quản lý tác vụ, Công cụ và giới thiệu bằng tiếng Việt. Lối mở ReadRecordActivity riêng vẫn còn nhãn tiếng Anh nên được bổ sung cùng tài nguyên ngày/thời lượng. Trình đọc đang báo lỗi tải mục lục: sửa nhãn lỗi và trạng thái đang tải; lỗi lấy dữ liệu nguồn chưa được xác định chỉ từ ảnh màn hình.

APK cuối (cùng version 3.26.100313debug) build thành công trong thư mục kết quả riêng: `/private/tmp/legado-flow-build/`; adb install -r trả Success. Ảnh runtime sau cài xác nhận màn hình Khám phá có tên nguồn và tab thể loại bằng tiếng Việt, nhãn Tác giả/Mới nhất đã Việt hóa. Trang cập nhật nguồn trong WebView cũng hiển thị nội dung đã dịch. Chờ giữ màn hình tủ sách hoặc Lịch sử đọc để đối chiếu hai phần bổ sung cuối.

Đối chiếu cuối trên tủ sách: xác nhận các nhãn 1 phút trước, 6 ngày trước, 19 phút trước, 2 giờ trước bằng tiếng Việt. Người dùng tiếp tục chuyển sang trình đọc trước khi chụp được trang Lịch sử đọc; phần này đã build/cài nhưng chưa có ảnh runtime sau sửa.
