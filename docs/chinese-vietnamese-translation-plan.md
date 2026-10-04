> Ghi chú bản public: tài liệu dưới đây ghi lại quá trình triển khai trước đây.
> Bản repo này không kèm từ điển hoặc Rule.txt; xem [phạm vi phát hành](PUBLICATION.md).

# Thêm dịch Trung → Việt vào legado-R

## 1. Mục tiêu và phạm vi

Tích hợp dịch offline bằng từ điển giống `legado-qt`: dịch tên sách, tác giả, giới thiệu, thể loại, mục lục và nội dung truyện; hỗ trợ nhập từ điển tùy chỉnh.

- Áp dụng cho truyện nguồn và TXT, ở cả giao diện đọc native và chế độ phân trang Direct/EPUB của văn bản.
- Không dịch nội dung file EPUB thật, ảnh truyện tranh hoặc video.
- Dùng một công tắc chung, mặc định **tắt**.
- Bao gồm Android, TTS và Web/API.
- Chỉ chuyển bộ dịch Trung → Việt; không đưa ONNX, AI Search hoặc giao diện của QT sang R.

## 2. Bộ dịch và từ điển

- Chuyển lõi `TranslateUtils`, `TranslationLoader`, `DoubleArrayTrie`, interface từ điển và `DictManager` từ QT; điều chỉnh dependency để biên dịch trong R.
- Đóng gói ba bộ mặc định của QT: `Names.dat`, `VietPhrase.dat`, `ChinesePhienAmWords.txt`, tổng khoảng 77 MiB trước nén.
- Giữ thuật toán hiện tại: khớp cụm dài nhất trong Names/VietPhrase → tra Names trước VietPhrase → lấy nghĩa đầu tiên → phiên âm → giữ chữ chưa biết. Giữ xử lý dấu câu, viết hoa, bỏ token riêng `的/了/著` và chuyển số trong tiêu đề chương.
- Từ điển tùy chỉnh thay thế bộ mặc định cùng loại. Giữ cách lọc file nhập của QT; hiển thị thông báo rằng chỉ dùng nghĩa đầu tiên.
- Nạp file trên `Dispatchers.IO`, dịch trên `Dispatchers.Default`; kiểm tra hủy giữa các đoạn và trong vòng quét văn bản.
- Cache kết quả giới hạn 10 MiB, an toàn khi truy cập đồng thời. Khóa cache gồm nội dung, loại dịch và phiên bản từ điển.
- Khi nạp hoặc dịch lỗi, giữ văn bản gốc và ghi log; tín hiệu hủy tiếp tục truyền lên caller.

## 3. Tích hợp Android và đường đọc

**Thiết lập và metadata**

- Thêm “Dịch Trung → Việt” và “Quản lý từ điển dịch” vào màn hình thiết lập hiện có, theo phong cách giao diện R.
- Quản lý riêng Names, VietPhrase và phiên âm: nhập TXT, hiển thị trạng thái, khôi phục mặc định. Chuẩn bị file mới thành công rồi mới thay dữ liệu đang dùng.
- Dịch metadata bất đồng bộ tại lớp hiển thị; kiểm tra danh tính item trước khi cập nhật view tái sử dụng.
- Giữ nguyên dữ liệu sách, tên gốc, URL, rule nguồn và khóa tìm kiếm trong database.

**Nội dung native**

- Trong `ContentProcessor`, dịch sau bước loại tiêu đề lặp và thay thế nội dung, trước xử lý đoạn và phân trang.
- Trả tiêu đề đã dịch qua `BookContent.displayTitle`, kể cả khi caller dùng `includeTitle=false`.
- Bảo vệ thẻ HTML và thuộc tính; chỉ dịch phần văn bản.

**Nội dung Direct**

- Tích hợp tại `prepareDirectContent()` để cả màn hình Direct, TTS và API dùng chung kết quả.
- Dịch tiêu đề và các phần chữ của `TextReaderDocument` sau xử lý thay thế.
- Với ảnh nằm giữa đoạn, dịch từng phần chữ giữa các ảnh rồi tính lại offset; giữ nguyên định danh ảnh, URL và hành động `click`.
- Tạo HTML, phân trang và văn bản TTS từ cùng tài liệu đã dịch.

**Đổi trạng thái dịch**

- Thêm trạng thái bật dịch và phiên bản từ điển vào khóa nội dung, layout và `chapterRevisionProvider`.
- Khi bật/tắt hoặc thay từ điển: hủy tác vụ cũ, xóa kết quả liên quan, dựng lại chương và các chương đã tải trước. Kết quả thuộc phiên bản cũ không được cập nhật giao diện.
- Giữ chương hiện tại và trở về đầu đoạn đang đọc nếu xác định được; nếu không thì về đầu chương. Tạm dừng TTS, người dùng chủ động tiếp tục.
- Không ghi bản dịch vào cache chương gốc hoặc file TXT. Bản đầu không cam kết chuyển đổi chính xác từng ký tự của bookmark giữa hai ngôn ngữ.

## 4. Web/API và tương thích

- Thêm query `translate=true|false` cho tủ sách, tìm kiếm, mục lục/refresh mục lục và nội dung.
- Không truyền tham số hoặc truyền `false`: trả dữ liệu gốc như hiện tại. `true`: yêu cầu bản dịch kể cả khi công tắc Android đang tắt.
- Tách lựa chọn dịch của caller khỏi công tắc chung trong bộ dịch; Android dùng công tắc, API dùng tham số.
- Dịch trên bản sao dữ liệu trả về; giữ nguyên cấu trúc response, URL và chỉ số chương.
- Thêm công tắc dịch trong web, mặc định tắt, truyền lựa chọn vào các request liên quan.
- Không cần migration database; bổ sung preference và thư mục từ điển trong vùng lưu trữ ứng dụng.

## 5. Kiểm thử và nghiệm thu

- **Lõi dịch:** cụm dài nhất, ưu tiên Names, nhiều nghĩa, phiên âm, chữ chưa biết, token bị bỏ, dấu câu, tiêu đề chương và HTML.
- **Từ điển:** bộ mặc định, nhập/khôi phục từng loại, file lỗi, cache nhị phân lỗi và thay từ điển khi đang dịch.
- **Reader:** native và Direct dùng cùng bản dịch; ảnh giữa đoạn giữ vị trí/hành động; TTS theo đúng chữ hiển thị; bật/tắt và đổi sách nhanh không nhận kết quả cũ.
- **API:** mặc định vẫn trả gốc; `translate=true` hoạt động độc lập công tắc Android; không sửa dữ liệu lưu.
- Chạy JVM tests liên quan, hồi quy reader/TTS/template, lint và build APK debug; kiểm tra tài nguyên từ điển trong APK và mức tăng kích thước.
- Nghiệm thu trên Android với truyện nguồn, TXT, chương dài, chương có ảnh, TTS và đổi từ điển. Kiểm tra file EPUB thật vẫn đọc bình thường.
- Báo cáo riêng kết quả static/JVM, build, web và thiết bị; chưa có thiết bị thì ghi rõ phần chưa nghiệm thu.

Triển khai theo thứ tự: **lõi dịch → quản lý từ điển → native/Direct và cache → metadata → Web/API → kiểm thử thiết bị**. Chỉ sửa `legado-R`; dùng `legado-qt` làm nguồn tham chiếu.
