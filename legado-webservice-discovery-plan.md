# Kế hoạch chia sẻ Discovery chỉ đọc qua Legado Web Service LAN

Tái sử dụng `WebBook.exploreBook()` hiện có và không làm thay đổi bookshelf của chủ thiết bị.

**Mục tiêu cuối cùng:**

```text
Web Browser
    │
    │ LAN
    ▼
Legado Web Service
    │
    ├── Kệ sách       ← hiện có
    │
    └── Khám phá      ← thêm mới
          │
          ▼
       BookSource
          │
          ▼
   WebBook.exploreBook()
          │
          ▼
   Website nguồn
```

Người dùng Web có thể:

```text
Khám phá
→ chọn nguồn/category
→ danh sách sách
→ Detail
→ TOC
→ Chapter
```

nhưng **không tự động thêm sách vào bookshelf Android**, không thay progress và không được đọc cấu hình/credential của BookSource.

### Các phase triển khai

1. **Tách Discovery engine khỏi UI.** Hiện `ExploreShowViewModel` gọi trực tiếp `WebBook.exploreBook(source, exploreUrl, page)`. Tạo một service/controller dùng chung, ví dụ `ExploreController`, để UI Android và Web API đều có thể gọi cùng engine. Không viết lại `WebBook.exploreBook()`.

2. **API lấy danh sách Discovery.** Thêm endpoint read-only như `GET /getExploreSources`. Nó chỉ trả các source có Discovery đang bật và thông tin hiển thị cần thiết. Không trả nguyên `BookSource`; đặc biệt không trả cookie, header, login, JS/rules hoặc credential. Category nên được biểu diễn bằng ID/descriptor an toàn thay vì cho client tùy ý truyền `exploreUrl`.

3. **API lấy danh sách sách.** Thêm `GET /getExploreBooks?sourceId=...&categoryId=...&page=1`. Android resolve ID → `BookSource + exploreUrl`, rồi gọi chính `WebBook.exploreBook(...)`. Giữ pagination giống native Discovery.

4. **Detail cho sách Discovery.** Đây là phần cần làm riêng vì `SearchBook` chưa chắc đã nằm trong bookshelf. Thêm endpoint read-only để lấy BookInfo từ `SearchBook + BookSource` mà **không gọi `saveBook`** và không tạo sách thật trong kệ.

5. **TOC read-only.** Tương tự, thêm API lấy chapter list trực tiếp từ book đang browse trong Discovery. Không phụ thuộc `appDb.bookDao.getBook(bookUrl)` như `/getChapterList` hiện tại.

6. **Chapter read-only.** Cho phép lấy chapter content từ Discovery session/context bằng engine `WebBook`, áp dụng `ContentProcessor` tương tự luồng đọc hiện tại. Không ghi progress về Android.

7. **Opaque IDs/session.** Không để browser tự gửi `BookSource`, `exploreUrl`, rule hoặc object tùy ý. Android giữ mapping ngắn hạn kiểu `sourceId → source`, `bookId → SearchBook`, `chapterId → chapter`. Đây vừa giảm rò thông tin source vừa ngăn client điều khiển trực tiếp URL/rules.

8. **Nối vào `HttpServer.kt`.** Sau khi controller hoạt động, thêm các GET route Discovery vào Web Service. API cũ `/getBookshelf`, `/getChapterList`, `/getBookContent` giữ nguyên để tránh regression.

9. **Test API trước, chưa sửa frontend.** Dùng `curl` trên LAN để kiểm tra lần lượt Sources → Categories → Books page 1/2 → Detail → TOC → Chapter. Test source bình thường, source cần WebView, pagination, lỗi network, source bị disable và book không tồn tại.

10. **Thêm Discovery vào Web UI.** Sau khi backend ổn mới sửa `assets/web/vue`: thêm `Kệ sách | Khám phá`, source/category selector, book grid/list, pagination/infinite scroll, detail, TOC và reader. Tận dụng reader hiện có thay vì tạo reader thứ hai.

11. **Giữ ranh giới quyền.** Discovery Web chỉ GET/read. Không expose `saveBookSource`, `getBookSource`, login/cookie, debug/search WebSocket, replace rules, `saveBookProgress`, delete/import/upload. Nếu sau này muốn nút “Thêm vào kệ Android” thì làm thành permission riêng, không trộn vào Discovery read-only.

12. **Regression và APK.** Chạy compile/tests, kiểm tra Web Service bookshelf cũ vẫn hoạt động, Android native Discovery vẫn hoạt động, sau đó build APK và test thật trên điện thoại.

### API mình đề xuất

Có thể thu gọn thành 5 endpoint:

```text
GET /getExplore
GET /getExploreBooks?sourceId=...&categoryId=...&page=1
GET /getExploreBookInfo?id=...
GET /getExploreChapterList?id=...
GET /getExploreBookContent?id=...&chapterId=...
```

`/getExplore` có thể trả cả source + category tree, không nhất thiết cần `/getExploreSources` riêng.

Response tổng quát:

```json
{
  "isSuccess": true,
  "data": {}
}
```

để đồng nhất với `ReturnData` hiện tại.

### Những file dự kiến sửa

Phía Android chủ yếu:

```text
app/src/main/java/io/legado/app/api/controller/
    ExploreController.kt                 NEW

app/src/main/java/io/legado/app/web/
    HttpServer.kt                        MODIFY

app/src/main/assets/web/vue/
    ...                                  MODIFY/REBUILD
```

Có thể cần refactor nhẹ:

```text
ui/book/explore/ExploreShowViewModel.kt
```

nhưng **không nên sửa engine**:

```text
model/webBook/WebBook
BookSource parsing/rules
```

trừ khi phát hiện API dùng chung thực sự thiếu abstraction.

### Sau này đưa qua Relay

Chỉ khi Web Service LAN hoàn chỉnh mới làm Phase 2:

```text
Browser Internet
       ↓
Cloudflare Worker
       ↓
Durable Object
       ↓
WSS
       ↓
RelayReadDispatcher
       ↓
ExploreController
```

Lúc đó bổ sung 5 endpoint trên vào Android `RelayReadAllowlist` + Worker allowlist. Không implement Discovery engine trong Cloudflare.

**Thứ tự quan trọng là:** `ExploreController → API LAN → test bằng curl → Web UI → APK test → Relay`. Đừng làm frontend hoặc Cloudflare trước khi API Discovery read-only chạy ổn trên Web Service.

Kế hoạch gồm hai giai đoạn độc lập: backend/API và kiểm thử trước, Web UI sau. Ranh giới này giúp phân biệt lỗi engine/API với lỗi frontend.

