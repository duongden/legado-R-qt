# Web Service: giao diện và tài liệu ba ngôn ngữ

Web Service có thanh điều hướng chung cho Tủ sách, Khám phá, Nguồn sách,
Nguồn RSS, Gửi sách và Trợ giúp. Mục **Cài đặt** chứa ngôn ngữ và giao diện;
không đặt các thẻ chọn màu lớn trong nội dung trang. Tủ sách và Khám phá có
**Lưới / Danh sách**, dùng chung lựa chọn lưu trên trình duyệt.

## Ngôn ngữ

- UI hỗ trợ Việt (`vi`, mặc định), Trung (`zh`) và Anh (`en`). Lựa chọn dùng
  khóa `legado_ui_language_v1`, áp dụng cho cả Vue và các trang HTML độc lập.
- Ngôn ngữ UI độc lập với **Dịch Trung → Việt** của nội dung truyện. Không đổi
  tên trường, URL, script, dữ liệu nguồn hoặc định danh sách theo ngôn ngữ UI.
- `modules/web/src/config/ui-locales.json` là catalog dùng chung. Chạy
  `node modules/web/scripts/sync-ui-assets.mjs` sau khi chỉnh catalog hoặc
  `design-system.css`; quy trình `npm run build` cũng gọi bước này.
- Trợ giúp có 25 chủ đề mỗi ngôn ngữ, manifest `web/help/topics.json`. Việt và
  Anh là hướng dẫn được biên soạn lại theo chức năng hiện tại; ví dụ kỹ thuật
  giữ nguyên cú pháp, tên API và dữ liệu mẫu. Trung giữ tài liệu gốc và bổ sung
  hướng dẫn LAN mới. Không coi các mẫu code là nội dung UI cần dịch tự động.
- `Context.readLocalizedMarkdown` đọc tài liệu theo locale của ứng dụng:
  `web/help/md/{vi,en,zh}` hoặc `localized/{vi,en,zh}` cho quyền tệp, chính sách
  riêng tư, miễn trừ và nhật ký cập nhật. Locale khác dùng Anh. Giấy phép LGPL
  giữ nguyên văn bản chuẩn; tài liệu chưa có bản địa hóa fallback về asset gốc.
- Các tài liệu chính sách/quyền tệp/nhật ký cũng có bản trong trợ giúp Web.

## Hiển thị

UTF-8 được khai báo trong HTML và response văn bản của Android HttpServer.
Phông hệ thống ưu tiên chữ Latin, có fallback CJK. Tiêu đề/tên truyện dài có
thể xuống dòng; bảng và ví dụ code trong trợ giúp cuộn bên trong vùng nội dung.
Thanh điều hướng có thể cuộn ngang trên màn hình nhỏ. Cài đặt dùng popup gọn,
đóng khi chuyển trang; reader giữ phiên đồng bộ khi đổi ngôn ngữ giao diện.

## Kiểm tra

- `npm run type-check`, ESLint các component/view và module thay đổi;
  `npm run build-only`, `npm run test:progress`.
- `LocalizedMarkdownTest`: ánh xạ locale và đường dẫn tài liệu.
- `LocalizedMarkdownAndroidTest`: đọc asset thật với ba locale Android,
  kiểm tra tài liệu gốc/giấy phép fallback.
- Chrome: trợ giúp, cài đặt, tủ sách, Discovery, chi tiết, reader, quản lý
  nguồn và gửi sách trên khổ PC/mobile; đổi ngôn ngữ và lưới/danh sách.
- `DiscoveryLanTest` dùng nguồn thử cục bộ qua HttpServer thật, dọn dữ liệu thử
  sau test. Các test tiến trình vẫn phải qua sau khi sửa giao diện.
