# Hướng dẫn Legado

## Bắt đầu
Legado đọc tệp và lấy nội dung theo quy tắc nguồn. Ứng dụng không kèm sẵn một thư viện truyện. Bạn có thể nhập TXT/EPUB hoặc thêm nguồn tương thích.

## Nguồn và Khám phá
Nhập nguồn từ tệp hoặc liên kết trong trình quản lý nguồn. Chỉ bật những nguồn cần dùng. Nguồn có quy tắc Khám phá mới xuất hiện ở trang Khám phá. Một nguồn chỉ liệt kê hoặc xếp hạng có thể không cung cấp nội dung để đọc; hãy chọn nguồn có quy tắc chi tiết, mục lục và nội dung chương phù hợp.
Kiểm tra nguồn chỉ là phép thử: kết quả lỗi chưa đủ để kết luận mọi truyện trong nguồn đều hỏng. Nếu truyện lỗi, kiểm tra trang gốc, đăng nhập trong app, thử lại hoặc đổi nguồn.

## Web Service trong mạng LAN
1. Bật Web Service trong ứng dụng trên điện thoại.
2. Mở địa chỉ ứng dụng hiển thị bằng browser trên PC trong cùng mạng.
3. Dùng Tủ sách để đọc sách đã thêm. Dùng Khám phá để chọn nguồn/danh mục hoặc tìm theo tên trong nguồn.
4. Mở chi tiết rồi chọn **Thêm vào kệ** hoặc **Thêm vào kệ và đọc**.
5. Chờ trạng thái **Đã đồng bộ** trước khi chuyển thiết bị. Browser đọc tới chương 5 thì khi quay lại điện thoại, reader tiếp tục chương 5.
Web Service cần điện thoại vẫn chạy và có thể kết nối. Request cũ không ghi đè tiến trình mới. Khi mất mạng, giữ tab để thử lại; chưa có hàng đợi offline bền vững sau khi đóng browser. Một số bộ lọc tương tác của nguồn cần chọn trong app. Giao diện web hỗ trợ lưới/danh sách và ghi nhớ lựa chọn trên browser.

## Sách cục bộ và WebDAV
Thêm TXT/EPUB từ menu kệ sách hoặc mở tệp bằng ứng dụng. Hãy cấp quyền cho thư mục con như `Documents/Books`. Nếu mục lục TXT trống, thử quy tắc mục lục khác. Nếu chữ bị lỗi, kiểm tra mã hóa, ưu tiên UTF-8. Nhập WebDAV qua màn hình sách WebDAV với máy chủ và tài khoản của bạn.
Sao lưu WebDAV và thư viện sách WebDAV là hai chức năng khác nhau. Đừng xóa dữ liệu hoặc khôi phục đè khi chưa có bản sao lưu đã kiểm tra.

## Đọc và quản lý kệ
Nhấn sách để đọc; nhấn giữ để xem chi tiết, chỉnh thông tin, xóa khỏi kệ hoặc đổi nhóm. Dùng mục lục để chuyển chương, tìm kiếm trong sách để tìm nội dung. Chọn phông TTF/OTF và điều chỉnh cỡ chữ, dòng, đoạn, chiều rộng theo thiết bị.
Chương VIP hoặc đã mua giữ nhãn từ nguồn. Quyền đọc phụ thuộc nguồn và tài khoản đang đăng nhập. Lỗi tải nội dung không được thay bằng nội dung giả.

## Tiến trình và bản dịch
Đồng bộ LAN dùng database trên điện thoại; không cần cấu hình WebDAV cho PC đọc qua chính điện thoại đó. Đồng bộ cloud là cấu hình riêng. Nếu hai bên hiển thị bản dịch/thay thế khác nhau, chương vẫn dùng chung nhưng vị trí ký tự có thể gần đúng.
Ngôn ngữ giao diện Việt/Trung/Anh độc lập với tùy chọn dịch nội dung Trung → Việt.

## Quy tắc và tiện ích
Quy tắc thay thế xử lý văn bản; quy tắc đoạn xử lý toàn chương khi được bật cho sách. Thử trên một sách trước khi áp dụng rộng. Đọc trợ giúp tương ứng trước khi chỉnh JS, XPath, regex, TTS hoặc tác vụ tự động.

## Khi gặp lỗi
Giữ thông tin tên nguồn, URL truyện, chương và thông báo lỗi để tự kiểm tra. Giảm số luồng nếu tìm kiếm làm máy chậm. Kiểm tra kết nối, quyền thư mục và trang gốc trước. Sao lưu dữ liệu trước khi nâng cấp, sửa nguồn hoặc khôi phục.


## Tải chương, xuất sách và e-ink trên Web Service
Trong **Tải và xuất**, chọn sách trong tủ và nhập khoảng chương (đánh số từ 1), hoặc tải toàn bộ. Chương tải được nằm trong cache trên điện thoại; mở lại sách trong app để đọc bằng cùng dữ liệu. Trang này có tiến độ, hủy và thử lại. Rời trang không hủy tác vụ; giữ app và Web Service hoạt động.

**Xuất TXT / Xuất EPUB** dùng bộ xuất của điện thoại và tải những chương còn thiếu trước khi xuất. Nếu nguồn báo lỗi hoặc chưa có quyền đọc chương thì xuất bị chặn; không thay chương lỗi bằng nội dung giả. Khi hoàn tất, bấm **Tải tệp** để lưu vào máy đang mở browser. Tệp tạm hết hạn sau một giờ hoặc khi danh sách tác vụ cần dọn; hãy tải về ngay khi sẵn sàng. TXT là văn bản, không kèm thư mục ảnh rời; EPUB dùng bộ đóng gói của app. Xuất web không tự gửi tệp lên WebDAV.

Trong **Cài đặt → Cài đặt tải**, chọn số chương tải trước (0–100, 0 tắt tải trước trên web) và khoảng nghỉ giữa các chương (0–60000 ms). Số chương dùng chung với cài đặt điện thoại. Khoảng nghỉ chỉ áp dụng cho Web Service, gồm đọc chương chưa có cache, tải trước và tải theo khoảng. Giới hạn riêng của nguồn vẫn áp dụng; một chương nhiều trang có thể cần nhiều yêu cầu HTTP. Chương đã cache không phải đợi giới hạn này.

Trong **Cài đặt → Giao diện**, chọn **E-ink** để dùng đen trắng, chữ tương phản cao, bỏ chuyển động/bóng và tắt cuộn chương liên tục. Dùng nút chương trước/sau hoặc phím mũi tên để đọc. Chế độ này áp dụng cả tủ sách, Discovery, reader, gửi sách và trợ giúp. Làm tươi phần cứng/full refresh vẫn do trình duyệt và thiết bị e-ink quản lý.

## Manga, âm thanh và phim trên Web Service

Trong Khám phá, chọn **Loại nội dung** để lọc nguồn tiểu thuyết, manga, âm thanh hoặc phim. Các nhóm thể loại dùng dropdown có tìm/chọn và cuộn gọn trong khung. Thêm vào kệ rồi mở để đọc manga theo trang hoặc phát âm thanh/phim với điều khiển tua và chọn tập. Tiến trình manga lưu theo trang; âm thanh/phim lưu theo mili giây và dùng chung dữ liệu với app.

Web hỗ trợ tệp phát trực tiếp và HLS khi trình duyệt hỗ trợ codec. Nguồn cần đăng nhập, CAPTCHA, DASH/MPD hoặc DRM riêng có thể cần mở trên điện thoại. Các bộ lọc nguồn có action JS riêng vẫn thao tác trong app. Tải chương hỗ trợ văn bản và manga; manga xuất EPUB kèm ảnh. Bìa lớn hơn giúp tránh phóng thumbnail nhỏ; độ nét vẫn phụ thuộc ảnh nguồn.

## Tải trước và xuất manga trên web

Tải chương và xuất EPUB hỗ trợ cả truyện văn bản lẫn manga trong tủ sách. Manga có nút Tải / Xuất ở thẻ sách và màn hình đọc. Tải một chương manga sẽ lưu đủ ảnh gốc đã xử lý bằng engine của app; bộ đếm chỉ ghi nhận chương đủ ảnh hợp lệ. Xuất EPUB tải trước các chương/ảnh còn thiếu và báo lỗi nếu thiếu trang. TXT dành cho văn bản; manga dùng EPUB để giữ ảnh. Khám phá dùng hai dropdown Nhóm danh mục và Thể loại; giới thiệu giữ xuống dòng và đoạn văn.
