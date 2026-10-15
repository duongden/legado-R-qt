# Khám phá trên Web Service LAN

Trong Web Service, chọn **Khám phá** bên cạnh Tủ sách. Chọn nguồn và danh mục,
hoặc tìm tên truyện/tác giả trong nguồn đang chọn. Danh sách có phân trang.
Trang chi tiết có **Thêm vào kệ** và **Thêm vào kệ và đọc**. Truyện đã có trong
kệ sẽ mở bằng nguồn và tiến trình hiện tại, không tạo bản trùng hay đặt lại vị trí.

Luồng duyệt và xem chi tiết chưa ghi sách hoặc chương vào database. Nút thêm
lấy thông tin và mục lục bằng engine Android, sau đó lưu trong một transaction.
Reader web hiện có tiếp tục đảm nhiệm nội dung và đồng bộ tiến trình LAN.
Mục lục hiển thị nhãn VIP/Đã mua từ cờ của nguồn.

## Thành phần

- `DiscoveryService`: dùng `exploreKinds()`, `WebBook.exploreBookAwait()`,
  `searchBookAwait()`, `getBookInfoAwait()` và `getChapterListAwait()`.
- `BookshelfAdder`: logic dùng chung với nút thêm ở `BookInfoViewModel`.
  Việc thêm lặp giữ nguyên sách đã có; sách tạm được giữ tiến trình khi vào kệ.
- `ExploreController` và `HttpServer`: API LAN dưới dạng `ReturnData`.
- `BookDiscovery.vue`, `DiscoveryBook.vue`: giao diện nguồn/danh mục, tìm kiếm,
  phân trang, xem chi tiết, thêm và chuyển sang reader hiện có.

## API

| Phương thức | Đường dẫn | Dữ liệu |
|---|---|---|
| GET | `/getExploreSources` | Nguồn tiểu thuyết, manga, âm thanh và phim đang bật Khám phá |
| GET | `/getExploreCategories?sourceId=...` | Danh mục của nguồn |
| GET | `/getExploreBooks?sourceId=...&categoryId=...&page=1` | Truyện theo danh mục |
| GET | `/getExploreBooks?sourceId=...&query=...&page=1` | Tìm trong nguồn |
| GET | `/getExploreBookInfo?id=...` | Chi tiết truyện chưa cần thêm vào kệ |
| GET | `/getExploreCover?id=...` | Bìa do điện thoại tải |
| POST | `/addExploreBook` | Body `{ "id": "..." }`; trả `{ reader, added }` |

Các GET metadata nhận `translate=true/false` theo tùy chọn web. Chỉ dịch bản DTO
hiển thị; dữ liệu gốc và thông tin dùng mở reader giữ nguyên.

Browser chỉ nhận metadata hiển thị và ID. URL danh mục, rules, script, header,
cookie, biến của truyện và cấu hình đăng nhập nằm trên Android. ID danh mục và
truyện là token tạm, hết hạn sau 30 phút không dùng; cache giới hạn 2.048 danh mục
và 512 truyện. Mỗi trang trả tối đa 200 truyện. Nguồn bị tắt/xóa không dùng tiếp
được bằng ID cũ. Lỗi phiên hết hạn có nút tải lại danh mục.

## Phạm vi

Hỗ trợ nguồn tiểu thuyết, manga, âm thanh và phim, danh mục URL và tìm kiếm trong một nguồn. Các nhóm danh mục được hiển thị bằng dropdown cùng giao diện, có cuộn bên trong. Bộ lọc loại nội dung giúp chọn nguồn nhanh. Các bộ
Discovery/widget tùy biến của native và các control `button/select/toggle` có
action riêng chưa được dựng lại trên browser. Các control này không được biến
thành URL hoặc chạy script từ browser; giao diện nhắc thao tác trên điện thoại.
Đăng nhập nguồn thực hiện trong app; API dùng trạng thái đăng nhập sẵn có.

Không thêm API hoặc quyền vào Relay. Không đổi schema database và không thay
reader/engine bằng một bộ lấy nội dung riêng.

## Kiểm thử

- `DiscoveryServiceTest`: 12 ca với Room: lọc nguồn, metadata, không ghi khi
  browse, thêm lặp, giữ tiến trình/nguồn cũ, TOC lỗi, nguồn tắt, hết hạn, phân
  trang/tìm kiếm, danh mục sai nguồn, loại media và sách đọc tạm.
- Hồi quy `ReadProgressSyncTest` và `npm run test:progress`.
- `npm run type-check`, ESLint các file web mới và `npm run build-only`.
- `DiscoveryLanTest`: nguồn JSON loopback thật chạy qua engine production và
  `HttpServer` trên điện thoại; duyệt, tìm, TOC/cờ VIP, thêm, nội dung, đồng bộ,
  reader native, nguồn bị tắt. Fixture được xóa sau test.
- Test có tham số `browserSeconds` để giữ fixture trong thời gian PC Chrome
  tương tác qua cùng Web Service. Marker `cache/lan-discovery-browser.json`
  chứa port, tên nguồn và tên truyện thử; file `.done` kết thúc bước browser.

## Manga, âm thanh và phim

`Reader.type` và `Item.type` giữ BookType của engine Android. Sách đa phương tiện mở `/media`, văn bản mở `/chapter`; loại nguồn dùng BookSourceType riêng (0/1/2/4). Không diễn giải BookSourceType thành bit BookType.

- `/getWebMediaChapter?url=...&index=...`: chỉ sách đã thêm vào kệ, nguồn còn bật, chương hợp lệ; gọi `WebBook.getContentAwait`, trả tiêu đề/loại và ID tài nguyên tạm.
- `/webMediaImage?id=...`: ảnh manga qua `ImageProvider` native (header, cache và xử lý ảnh của nguồn), tối đa chiều rộng 1600px.
- `/webMediaResource?id=...`: chuyển tiếp stream âm thanh/phim, header của nguồn giữ trên điện thoại; hỗ trợ HTTP Range/206, Content-Range. Playlist HLS con, segment và URI key được đổi thành ID cùng API. Không có proxy nhận URL tùy ý từ browser.
- Bìa qua `/getBookCover?url=...` hoặc `/getExploreCover?id=...` tải ở 420×630 thay vì thumbnail 84×112. Chất lượng còn phụ thuộc ảnh nguồn.

Browser có điều khiển phát/tua, chọn tập và xem từng trang manga; tiến trình dùng API revision hiện có, vị trí âm thanh/phim tính bằng mili giây, manga bằng trang bắt đầu từ 0. Reader bỏ qua tiêu đề mùa/tuyến; lưu tiến trình phim cập nhật cả `durVolumeIndex` và `chapterInVolumeIndex` trong cùng transaction để native mở lại đúng tập. Mở lại native đọc từ dữ liệu đã lưu; một phiên phát native đang chạy vẫn có thể ghi tiến trình mới của chính nó.

Phát trực tiếp MP3/WAV/MP4/WebM tùy codec trình duyệt; HLS dùng native khi có, hoặc hls.js được đóng gói trong app, không tải từ CDN. Xem [hls.js](https://github.com/video-dev/hls.js) cho phạm vi codec/MSE. Không triển khai DASH/MPD, cấu hình DRM hay giao diện đăng nhập/captcha riêng của từng nguồn trên web. Nguồn cần thao tác đó phải hoàn tất trên điện thoại; khi không thể phát, UI báo lỗi thay vì hiển thị như văn bản. Việc thêm hỗ trợ loại nguồn không đảm bảo mọi nguồn bên ngoài hoạt động.

Các control Discovery có action JS riêng vẫn thao tác trong app; browser chỉ gom các danh mục URL theo tiêu đề nhóm. Không gửi action/script/cookie tới browser.

`WebMediaLanTest` dùng nguồn JSON thật, video WebM tạo riêng, WAV và manga sinh trong fixture để kiểm tra browse/add/type, ảnh, stream có header, Range và HLS lồng/key rewriting; các tài nguyên và source được xóa sau test. Đây là kiểm thử fixture, không phải chứng nhận toàn bộ nguồn ngoài.

### Xác minh bản APK ngày 15/10/2026

- 22 JVM tests (10 tiến trình, 12 Discovery) và 10 JavaScript progress tests đạt.
- Trên điện thoại: `DiscoveryLanTest` (2 ca), `ReadProgressLanTest` và `WebMediaLanTest` đạt. Kiểm tra gồm cache/rate/prefetch, TXT/EPUB, API media có header/Range và playlist HLS lồng.
- Chrome tải trực tiếp UI từ APK qua ADB forward: grid/list với nền xám, vi/en/zh, chiều rộng 390/1280/2048px; đo khối tiêu đề/tác giả/tiến trình nằm trong thẻ. Bìa trả 420px, dropdown 70 thể loại trong viewport. Tệp EPUB tải thật đủ 7 chương, CRC hợp lệ.
- Browser phát/tua WAV và WebM, đổi trang manga; phim có tiêu đề mùa mở tập đầu và chuyển tập. Database lưu chỉ số chương 2, mùa 0, tập trong mùa 1, vị trí 2407ms đúng như native sử dụng. Đây là dữ liệu fixture, không kiểm chứng toàn bộ nguồn bên ngoài.

## Bố cục và đoạn giới thiệu

DTO intro chuyển HTML thành văn bản an toàn nhưng giữ newline, br và ranh giới đoạn; loại script/style. UI chi tiết dùng pre-wrap. Discovery dùng hai dropdown phụ thuộc nhau (nhóm → thể loại), gỡ ký tự trang trí ở nhãn nhóm mà không đổi URL/ID. Các thẻ lưới dùng cùng padding/gap với tủ sách và vùng cố định cho tiêu đề/tác giả/metadata/giới thiệu/footer để căn hàng ổn định.
