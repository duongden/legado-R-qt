# Đồng bộ tiến trình đọc qua Web Service LAN

Điện thoại giữ tiến trình chuẩn. Web lấy vị trí từ điện thoại khi mở reader,
reload, lấy lại focus hoặc kết nối mạng, và kiểm tra lại mỗi 5 giây khi tab hiện.
Web gửi tiến trình sau khi tải thành công chương được chọn, khi cuộn (nghỉ 700 ms,
tối đa 2 giây), khi rời reader và khi ẩn/đóng trang. Nạp trước chương không ghi tiến trình.

## API

- `GET /getReadProgress?url=<bookUrl>` trả `ReturnData<Snapshot>`.
- `POST /saveReadProgress` nhận `bookUrl`, `baseRevision`, `durChapterIndex`,
  `durChapterPos`; trả `ReturnData<{ accepted, snapshot }>`.
- `Snapshot` gồm `bookUrl` và `progress` (các trường `BookProgress` gốc).

`baseRevision` là `progress.durChapterTime` nhận từ điện thoại. Server tự cấp
phiên bản tăng dần, không dùng giờ trên browser. Kiểm tra phiên bản và cập nhật
diễn ra trong một transaction Room. Request cũ trả `accepted: false` cùng tiến
trình hiện tại; web nhận vị trí này và báo cho người đọc. Gửi lại cùng vị trí
không tạo thêm phiên bản. Đọc lùi được phép khi dùng phiên bản hiện tại.

Các lần lưu từ web được xếp hàng và phải nhận xác nhận trước khi dùng phiên bản
cho request tiếp theo. UI hiển thị trạng thái đồng bộ và nút thử lại. Request nhỏ
dùng `fetch(..., keepalive: true)` để có thể hoàn thành khi đóng trang.

Reader Android giữ tiến trình web chờ áp dụng khi resume. Trong lúc đó, các lần
lưu nền bị bỏ qua; snapshot native đã xếp hàng từ trước cũng không được ghi đè
phiên bản mới hơn trong database. Nhận diện bằng `bookUrl`.

## Kiểm chứng

- `cd modules/web && npm run test:progress`: 10 ca về ghi tuần tự, nhận tiến trình
  điện thoại, xung đột, đọc lùi, mất mạng/thử lại và GET trả về trễ.
- `npm run type-check` và `npm run build-only`.
- `./gradlew :app:testAppDebugUnitTest --tests io.legado.app.model.ReadProgressSyncTest`:
  8 ca với Room, gồm chương 3 → 5, request cũ, snapshot nền và full-row update,
  request lặp, timestamp, sách khác và dữ liệu không hợp lệ.
- `ReadProgressLanTest` chạy trên điện thoại debug thật: HTTP qua `HttpServer`,
  Room, reader xuống nền/resume, native đọc tiếp, từ chối request cũ và đọc lùi.
  Fixture dùng sách/cache tạm và được dọn sau test.
- Chrome với HTTP fixture: mở chương 3, đọc tới chương 5, nhận chương 6/vị trí
  mới từ server khi focus trở lại, reload và đọc lùi.

## Phạm vi

Các route mới chỉ nối vào Web Service LAN. Không thay đổi dispatcher/allowlist
Relay, Discovery hay schema database. Lưu LAN không phụ thuộc cấu hình cloud.

Hai bên chia sẻ chương và offset ký tự hiện có. Khi hai bên hiển thị nội dung
khác nhau do dịch/thay thế, vị trí trong chương có thể chỉ gần đúng; chưa có
ánh xạ đoạn văn giữa hai bản nội dung. Cần Web Service đang chạy và kết nối tới
điện thoại để nhận xác nhận. Khi mất mạng, request đang chờ được giữ trong bộ
nhớ tab để thử lại; chưa có hàng đợi offline bền vững sau khi đóng browser.
