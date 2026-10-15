# Tải chương, xuất sách và e-ink trên Web Service LAN

Chọn **Tải và xuất** trên thanh điều hướng, nút **Tải / Xuất** của sách trong
Tủ sách hoặc công cụ reader. Chọn khoảng chương, tải toàn bộ, xuất TXT/EPUB,
theo dõi tác vụ, hủy/thử lại và bấm **Tải tệp** sau khi xuất thành công.

## Hành vi

- Cache chương dùng `WebBook.getContentAwait` và `BookHelp`, đọc được bằng app.
  Chương đã có nội dung không tải lại. Bulk download cập nhật cache manifest.
- Xuất dùng `ExportBookService` và cài đặt nội dung của điện thoại. Chương còn
  thiếu được tải trước; nếu có chương lỗi thì không cung cấp tệp xuất. TXT chỉ
  kèm văn bản, EPUB dùng bộ đóng gói native. Không tự upload WebDAV khi xuất web.
- File nằm trong thư mục cache riêng, tên vật lý cố định; API chỉ nhận ID tác
  vụ, không nhận đường dẫn. Gửi bằng stream, Content-Disposition UTF-8 và no-store.
- Có tối đa hai tác vụ LAN hoạt động, một tác vụ mỗi sách; tác vụ thủ công ưu
  tiên hơn tải trước. Lịch sử tối đa 32 tác vụ. File/lịch sử hết hạn sau một giờ,
  hoặc có thể bị dọn sớm khi đạt giới hạn. Không có hàng đợi bền sau khi app bị dừng.
- Số chương tải trước dùng `AppConfig.preDownloadNum` (web nhận 0–100). Khoảng
  nghỉ `webChapterIntervalMs` (0–60000 ms, mặc định 1000 ms) áp dụng riêng cho
  Web Service: đọc chưa cache, tải trước, bulk và chuẩn bị xuất. Cổng giới hạn
  dùng đồng hồ monotonic; source engine vẫn áp dụng giới hạn riêng của nguồn.
  Đây là khoảng nghỉ giữa chương, không phải giới hạn từng HTTP request của
  chương nhiều trang. Cache hit không cần chờ. Không đổi tốc độ tải native.
- Hủy một tác vụ web không dừng các tác vụ xuất native khác. Xuất từ điện thoại
  tiếp tục dùng đường dẫn, tên và các lựa chọn xuất trước đây.
- Thêm preset `eink` dùng chung mọi trang, lưu trên browser. Đen trắng, không
  animation/shadow, ảnh grayscale, reader không nối chương liên tục và cuộn
  bằng phím có thời lượng 0. Không giả lập API làm tươi màn hình phần cứng.

## API LAN

| Method | Path | Body/query |
|---|---|---|
| GET | `/getWebDownloadSettings` | số chương và khoảng nghỉ |
| POST | `/saveWebDownloadSettings` | `{preDownloadNum, chapterIntervalMs}` |
| GET | `/getWebBookCache` | `bookUrl` |
| POST | `/startWebBookTransfer` | `{bookUrl,start,end,format?}`; start/end từ 0; format `txt` hoặc `epub` xuất cả sách |
| POST | `/preloadWebChapters` | `{bookUrl,index}`; index chương đang đọc từ 0 |
| GET | `/getWebBookTransfers` | `bookUrl` |
| GET | `/getWebBookTransfer` | `id` |
| POST | `/cancelWebBookTransfer` | `{id}` |
| GET | `/downloadWebBookFile` | `id`; chỉ tác vụ xuất hoàn tất |

Sách văn bản và manga đã có trong kệ được tạo tác vụ. Manga chỉ xuất EPUB.
Nguồn/quyền đọc vẫn do engine
và phiên đăng nhập trên điện thoại quyết định. Không mở thêm API qua Relay.

## Kiểm chứng

`DiscoveryLanTest#cachePrefetchRateLimitAndNativeFileExports` dùng nguồn JSON
loopback thật: kiểm tra cửa sổ tải trước, khoảng cách request, cache hit,
TXT và cấu trúc EPUB qua HTTP, chương lỗi chặn xuất, hủy, validation và tắt
prefetch. Test khôi phục cài đặt và dọn sách/nguồn/file thử. Hồi quy Discovery,
Room progress và JS progress; type-check/lint/build web. Chrome kiểm tra thao
tác tải file, chọn số chương/tốc độ, ba ngôn ngữ và e-ink desktop/mobile.

## Manga

Web tải chương manga kèm toàn bộ ảnh qua ImageProvider/native source, gồm giải mã ảnh của nguồn. Bộ đếm và manifest chỉ ghi chương đủ ảnh hợp lệ. Ảnh tải lỗi chặn xuất; thử lại tải bù ảnh thiếu/hỏng. EPUB dùng ExportBookService hiện có và nhúng ảnh trong tệp. Không xuất manga sang TXT vì định dạng này không giữ ảnh. Nút Tải / Xuất có trên thẻ manga và reader. Luồng prefetch/rate limit cũng áp dụng khi tải chương manga.
