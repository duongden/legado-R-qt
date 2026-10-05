# Legado-R-qt

[Tiếng Việt](README.md) · [English](English.md)

<p align="center"><img width="125" height="125" src="docs/archive_icon.svg" alt="Reading Archive"></p>

Legado-R-qt kế thừa [Legado-R / Reading Archive](https://github.com/duongden/legado-R), được phát triển từ nhánh Legado do Lyc duy trì trên nền tảng [Legado](https://github.com/gedoor/legado). Ứng dụng hỗ trợ đọc truyện chữ và EPUB, tùy chỉnh giao diện, nghe sách, AI và tác vụ định kỳ.

Nhánh qt bổ sung giao diện tiếng Việt và dịch Trung → Việt ngoại tuyến bằng từ điển cho truyện chữ. Bản dịch được xử lý khi hiển thị, giữ nguyên dữ liệu sách gốc. Chất lượng dịch phụ thuộc từ điển được nhập; việc hoàn thiện Việt hóa giao diện vẫn đang tiếp tục.

Ứng dụng không cung cấp sẵn nội dung sách. Bạn có thể tự thêm nguồn sách hoặc nhập sách TXT, EPUB trên thiết bị.

## Tải xuống và cập nhật

- [GitHub Releases — Legado-R-qt](https://github.com/duongden/legado-R-qt/releases): APK và ghi chú phát hành của nhánh này.
- [Mã nguồn Legado-R-qt](https://github.com/duongden/legado-R-qt).
- [Reading Archive upstream](https://github.com/Rimchars/legado): dự án kế thừa và các bản phát hành gốc.

Thông tin phiên bản và thay đổi tương ứng được ghi tại từng bản phát hành. APK của Reading Archive upstream được phát hành độc lập với Legado-R-qt.

## Điểm bổ sung của nhánh qt

- Việt hóa giao diện Android, các hộp thoại và giao diện Web.
- Dịch Trung → Việt bằng Names, VietPhrase, từ điển phiên âm và Rule.txt; hỗ trợ nhập dữ liệu TXT qua phần quản lý từ điển.
- Tích hợp dịch vào luồng đọc văn bản native và Direct, cùng các phần hiển thị thông tin sách đã hỗ trợ dịch.
- Lựa chọn dịch trên Web hoạt động độc lập với công tắc dịch của Android.

Dịch từ điển ngoại tuyến là chức năng riêng với dịch vụ AI. Xem [tài liệu triển khai dịch](docs/translation-implementation.md) để biết chi tiết và giới hạn của từng luồng đọc.

## Tổng quan chức năng

| Nhóm | Khả năng chính |
| --- | --- |
| Đọc sách | Nguồn sách và sách cục bộ, trình bày native và EPUB, hiệu ứng lật trang, kiểu đọc, dấu trang và tiến độ |
| Tủ sách và thông tin sách | Danh sách và lưới, nhóm, nhãn, quản lý hàng loạt, trang chi tiết, mục lục và cập nhật định kỳ |
| Đánh dấu và mẫu trang | Quy tắc RED cục bộ, đánh dấu bằng hình ảnh, chọn phông chữ, mẫu HTML/CSS/JavaScript, chữ ngang và dọc |
| Tài nguyên và chủ đề | Thư viện ảnh/phông chữ dùng chung, chế độ ngày/đêm, nền, tiêu đề nâng cao, đầu/chân trang và bong bóng bình luận |
| Nghe sách và đa phương tiện | TTS hệ thống và qua mạng, theo dõi văn bản đang đọc, điều khiển nổi, truyện tranh và video |
| AI | Cấu hình dịch vụ AI, tìm nguồn sách, đọc sách và chương, truy vấn lịch sử đọc và công cụ mạng |
| Tự động hóa và dữ liệu | Tác vụ định kỳ, bộ đệm, sao lưu/khôi phục, WebDAV, lưu trữ đối tượng và quản lý vùng lưu trữ |
| Mạng | DNS/DoH, định tuyến theo chức năng, ngoại lệ tên miền, cấu hình dịch vụ và đo tốc độ |

Các chức năng nền tảng được kế thừa từ Legado / Reading Archive. Xem [mô tả chi tiết và khác biệt giữa các chế độ](docs/features.md).

## Tài liệu sử dụng

- [Mẫu trang đọc: sử dụng, chia sẻ và biên soạn](docs/reader-templates.md)
- [Mạng và DNS](docs/doh-network.md)
- [Gói tài nguyên giao diện và tiêu đề nâng cao](docs/visual-resource-packages.md)
- [Nhập quy tắc đoạn và gói bình luận](docs/online-package-import.md)
- [Web và Content Provider API](api.md)
- [Trợ giúp Legado](https://www.yuque.com/legado/wiki)

Quy tắc đánh dấu được nhập từ tệp `.red` cục bộ. Hiệu ứng hình ảnh và CSS phức tạp sử dụng trình đọc EPUB. Trong chế độ EPUB dành cho truyện chữ, quy tắc đoạn và `pclick` hiện không được bật; thao tác `click` của ảnh gốc và quy tắc thay thế/làm sạch vẫn được hỗ trợ. Thư viện mẫu trang có chức năng sao lưu riêng.

Khi báo lỗi, vui lòng cung cấp phiên bản ứng dụng, chế độ đọc và các bước tái hiện. Ghi chú kiểm thử trong tài liệu có phạm vi và thời điểm riêng, không thay thế việc kiểm tra trên thiết bị đang sử dụng.

## Mã nguồn mở và ghi nhận

README này được biên soạn dựa trên [README gốc của duongden/legado-R](https://github.com/duongden/legado-R/blob/main/README.md). Cảm ơn [gedoor/legado](https://github.com/gedoor/legado), [Luoyacheng/legado](https://github.com/Luoyacheng/legado), [Rimchars/legado](https://github.com/Rimchars/legado) và các tác giả đóng góp. Chức năng tác vụ định kỳ có sự đóng góp và hỗ trợ của 明月.

Phần dịch kế thừa mã từ [legado-qt](https://github.com/duongden/legado-qt) và [VietPhrase translator](https://github.com/duongden/duongden-vietphrase-translator).

Dự án sử dụng Rhino, Jsoup, OkHttp, Glide, Miuix, Paged.js và các thành phần mã nguồn mở khác. Giấy phép dự án nằm trong [LICENSE](LICENSE); các thành phần giữ giấy phép riêng được ghi trong [danh sách giấy phép](app/src/main/assets/LICENSE.md).

Lịch sử upstream được lưu trong [CHANGELOG](CHANGELOG.md), [ghi chú tháng 7/2026](docs/changelog/2026-07.md) và [lịch sử Legado](docs/changelog/upstream-2022.md).
