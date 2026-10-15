# Phần mở rộng và MIME

MIME mô tả loại nội dung trong phản hồi HTTP; phần mở rộng mô tả tên tệp. Hai giá trị có thể không trùng khi máy chủ trả sai header. Kiểm tra cả phản hồi và nội dung thực.

| Nhóm | Ví dụ MIME |
|---|---|
| Văn bản | `text/plain`, `text/html`, `application/json` |
| Sách | `application/epub+zip` |
| Ảnh | `image/jpeg`, `image/png`, `image/webp`, `image/svg+xml` |
| Âm thanh | `audio/mpeg`, `audio/aac`, `audio/ogg` |
| Video | `video/mp4`, `application/vnd.apple.mpegurl` |
| Phông | `font/ttf`, `font/otf`, `font/woff2` |
Mã MIME và phần mở rộng là định danh kỹ thuật, không dịch theo ngôn ngữ giao diện.
