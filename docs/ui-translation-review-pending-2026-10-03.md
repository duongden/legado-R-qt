# Rà soát và sửa giao diện sau lượt ghi thứ ba

Bản ghi dài 180 giây, người dùng tự thao tác trên Samsung SM-A546E. Phần bảng dưới đây là bằng chứng trước sửa. Người dùng đã xác nhận toàn bộ phạm vi; kết quả triển khai được ghi ở cuối tài liệu.

| Thời điểm trong video | Flow | Hiện tượng cần xử lý |
| --- | --- | --- |
| 0–14 giây | Tủ sách | Tên sách, tác giả và thời gian tương đối đã dịch; dùng làm mốc đối chiếu |
| 16–46 giây | Thông tin sách → Đặt biến nguồn | Tiêu đề Book information, Author và lời hướng dẫn Source variables… còn tiếng Anh |
| 48–68 giây | RSS nguồn anime | Tên nguồn/tab đã qua dịch; danh sách trống cần phân biệt lỗi tải dữ liệu với lỗi hiển thị |
| 70–86 giây | Thống kê → Xếp hạng đọc | Một tên sách trong hộp thoại vẫn tiếng Trung dù các dòng khác đã dịch |
| 88–112 giây | Quản lý thanh trên | Top bar manager, Day/Night theme, mô tả, Default day top bar, Applied, More còn tiếng Anh |
| 114–132 giây | Cài đặt giao diện | Các mục quản lý thanh dưới, khám phá/đăng ký, thanh trên, chi tiết sách, bong bóng và mẫu màn hình tải còn tiếng Anh |
| 134–156 giây | Của tôi → Lịch sử đọc | Tiêu đề đã Việt hóa; nhãn nội dung còn tiếng Anh và trang có đoạn hiển thị trống trong lúc điều hướng |
| 158–180 giây | RSS nguồn kịch ngắn | Tên/tab/thông báo lỗi đã qua dịch; thông báo thiếu dữ liệu vẫn cần giữ đúng ý nghĩa |

## Ngôn ngữ Tiếng Việt độc lập hệ thống

Hiện `AppContextWrapper.getSetLocale()` chỉ có zh/tw/en; các mảng `language` và `language_value` chưa có vi. Tài nguyên values-vi đã tồn tại nhưng chưa được chọn trực tiếp trong ứng dụng.

Phạm vi dự kiến:

- Thêm Tiếng Việt vào danh sách chọn ngôn ngữ, đồng bộ số lượng/thứ tự các mảng ở mọi locale.
- Áp dụng và lưu locale vi cho ứng dụng, hoạt động sau khởi động lại và không phụ thuộc ngôn ngữ hệ thống.
- Bổ sung nhãn Việt còn thiếu trong các màn hình đã ghi; rà các tài nguyên/menu dùng chung để tránh fallback Trung/Anh.
- Giữ lựa chọn ngôn ngữ giao diện riêng với chức năng dịch nội dung truyện. Kiểm tra giao diện Việt cả khi tắt dịch truyện.
- Sửa các đường hiển thị bỏ sót trong bảng; chẩn đoán trang Lịch sử đọc trống, không tự kết luận nguyên nhân từ video.
- Build/cài bản sửa và đối chiếu thiết bị sau khi người dùng xác nhận.

JSON, URL, khóa cấu hình, nội dung ô nhập và giá trị nguồn gốc được giữ nguyên. Không chép dữ liệu tài khoản hay token trong bản ghi vào tài liệu.

## Triển khai sau xác nhận

- Thêm lựa chọn Tiếng Việt (`vi`) vào cả bảy mảng nhãn ngôn ngữ và mảng giá trị. `AppContextWrapper` áp dụng locale vi trên bản sao Configuration, tránh thay đổi configuration đầu vào. Activity tái tạo nếu locale không còn khớp lựa chọn đã lưu.
- `UiTranslation.isEnabled()` cho phép giao diện Việt độc lập công tắc dịch truyện; nối helper Compose/native, menu, toast, snackbar, dialog, BaseService notifications và WebView vào điều kiện này. Đường dịch nội dung truyện vẫn dùng công tắc riêng.
- Việt hóa nhãn quản lý thanh trên, thanh dưới, chi tiết sách, khám phá/đăng ký và EPUB loading. Mẫu tải EPUB có sẵn được dịch ở lớp hiển thị; không dịch nội dung sách EPUB.
- Thông tin sách và VariableDialog dịch tiêu đề/hướng dẫn/nút. Ô JSON, giá trị biến và callback nguồn vẫn giữ nguyên.
- Danh sách Compose dùng cache theo phiên bản từ điển và dấu chờ trong lúc dịch chữ Trung, tránh lộ chữ gốc khi dòng vừa xuất hiện. Không ghi tên đã dịch vào database.
- Nhãn native Lịch sử đọc được đăng ký sau khi sắp xếp lại thành phần.
- Lượt đối chiếu sau cài phát hiện thêm màn hình bong bóng: sửa công tắc, tên mẫu, trạng thái áp dụng, nhãn chỉnh sửa và tiêu đề trang. Popup ModernActionPopup dùng chung dịch tiêu đề hiển thị, giữ nguyên action/callback.

## Kiểm chứng

- Build APK cuối và lượt bổ sung thành công với JDK 17, Gradle offline, thư mục build tách biệt Android Studio; `adb install -r` trả Success. Phiên bản `3.26.100314debug`, applicationId `io.legado.app.rqt.debug`.
- XML tiếng Việt không có tên string trùng; tham số định dạng khớp tài nguyên mặc định. Cả bảy mảng nhãn khớp năm giá trị ngôn ngữ.
- Trên SM-A546E, hệ thống ưu tiên `en-US,vi-VN`; app lưu `language=vi`. Màn hình Cài đặt khác hiển thị Tiếng Việt khi `translateChineseVietnamese=false`. Đã bật lại công tắc sau phép thử, lưu `true` và giữ `vi`.
- Lịch sử đọc hiển thị các thẻ và dữ liệu bình thường lúc tái kiểm tra; tên sách từng còn Trung đã hiện Việt. Không có AndroidRuntime crash trong phần log gần đây đã đọc. Chưa tái hiện được đoạn trang trống trong video, không kết luận đã sửa nguyên nhân lỗi này.
- Chưa xác nhận runtime từng loại thông báo và mọi màn hình sau bản cuối. Build không thay thế kiểm chứng từng luồng trên thiết bị.
- RSS danh sách trống/thông báo không tải được chưa có bằng chứng là lỗi dịch. Không chỉnh quy tắc hay dữ liệu nguồn để che lỗi tải.

Bản ghi và ảnh đối chiếu nằm cục bộ trong `/private/tmp/legado-flow-review`; tài liệu không chứa JSON nguồn, tài khoản hay token từ màn hình.

### Bổ sung từ đối chiếu trực tiếp

- Bổ sung bộ tài nguyên `navigation_bar_*` và nhãn More còn thiếu, mô tả hòa thanh quản lý vào nền.
- Trang Mẫu chia sẻ trích đoạn dùng nhiều chuỗi Trung viết trực tiếp: chuyển nhãn Compose sang helper dịch, thêm bản Việt cho kiểu màu, phông chữ, tiêu đề và hướng dẫn. Dữ liệu HTML/template và các mã kiểu màu/phông không thay đổi.
- Mô tả mẫu tải đang chọn dùng tên bản Việt của mẫu có sẵn, tránh ghép tên Trung rồi dịch bằng từ điển truyện.
- `git diff --check` đạt. Sau cập nhật trước lượt bổ sung này, thiết bị vẫn giữ `language=vi` và công tắc dịch `true`.

### Quản lý bộ nhớ đệm

Ảnh đối chiếu tiếp theo hiển thị Quản lý bộ nhớ đệm, thay vì trang trích đoạn được yêu cầu giữ. Phát hiện tên sách/nguồn và nhãn Books, Local, Upload còn gốc. Bổ sung tài nguyên Việt cho nhóm cache (bao gồm hộp thoại xác nhận và trạng thái tác vụ), dùng uiString cho số lượng/thông báo và setTranslatedUiLabel cho tên sách, nguồn, tên chương và tiến trình. Không thay đổi thao tác tải lên/xóa, quy tắc lựa chọn nguồn, định danh sách hoặc dữ liệu cache.

## Đối chiếu chốt trên thiết bị

Bản bổ sung cache build thành công và cài Success. Ảnh sau sửa lúc 13:48 xác nhận các tab Sách / Âm thanh / Truyện tranh; số sách và chương đã lưu; tên sách và nguồn; trạng thái Trên máy; nút Tải lên, Xóa, Tải lên tất cả, Xóa tất cả đều qua hiển thị Việt. Chữ nằm trong ảnh bìa vẫn là ảnh gốc. Các giá trị `language=vi` và `translateChineseVietnamese=true` được giữ sau lượt cài này.

Trang Mẫu chia sẻ trích đoạn đã sửa và build/cài, nhưng chưa có ảnh đối chiếu đúng trang sau sửa: lúc trả lời đã mở, thiết bị đang ở Quản lý bộ nhớ đệm. Không coi ảnh cache là bằng chứng runtime của trang trích đoạn.

## Lỗi số và chữ dính nhau – lượt rà tiếp theo

Ảnh màn hình chi tiết sách ban đầu hiện “Thời lượng đọc: 1 phút57 giây”, cùng các nhãn Trung ở ba ô Nguồn/Mục lục/Thư viện ảnh. Lỗi thời lượng này do sáu formatter nối trực tiếp các phần ngày/giờ/phút/giây, không phải bằng chứng rằng VietPhrase làm mất khoảng trắng.

- Sửa formatter ở BookInfoComposeActivity, BookInfoActivity, ReadBookActivity, ReadRecordActivity, ReadRecordFragment và ReadGoalWidgetProvider: lọc phần rỗng, trim và ghép bằng một dấu cách. Giữ nguyên cách chọn đơn vị và điều kiện hiển thị giây.
- UiTextSpacing chuẩn hóa các chuỗi số–đơn vị đã dịch tại lớp UiTranslation, gồm thời gian, số chương/mục và dung lượng. Chỉ nhận dạng các đơn vị đã biết; bảo vệ URL, email và code span.
- Nối nhãn/alias hiển thị, đơn vị chương, các preview và menu chi tiết sách vào helper dịch. Các nhãn Nguồn, Mục lục, Thư viện ảnh dùng tài nguyên thay cho chuỗi Trung viết trực tiếp.
- UiTextSpacingTest đạt 4/4: ví dụ 21 phút15 giây; nhiều đơn vị dính nhau; số thập phân/xuống dòng; URL/email có ký tự Việt; code, mã thiết bị và phiên bản; tính ổn định khi chạy lại.
- Build APK cuối 3.26.100315debug thành công với JDK 17 và thư mục build tách biệt. Tài nguyên Việt không trùng tên, tham số định dạng khớp bản mặc định; git diff --check đạt.
- Cài APK bằng adb install -r trả Success; thiết bị báo phiên bản 3.26.100315debug, cập nhật lúc 14:16:29. Không thấy AndroidRuntime crash trong phần log gần đây đã đọc.
- Ảnh sau cài spacing-after.png thực tế là Lịch sử đọc: xác nhận trực tiếp các thời lượng “5 phút 12 giây”, “1 phút 57 giây”, “14 phút 50 giây”, “8 phút 4 giây” và tổng “49 phút 54 giây” đã tách đúng. Chưa có ảnh sau cài đúng trang chi tiết sách để xác nhận ba nhãn Nguồn/Mục lục/Thư viện ảnh; không dùng ảnh Lịch sử đọc thay cho bằng chứng của trang đó.

## Lượt ghi Bộ đệm ngoại tuyến

- Đã ghi thêm khoảng 92 giây vào extra-review.mp4 trong thư mục tạm riêng. Các khung hình lấy mỗi 5 giây chỉ ghi nhận trang Bộ đệm ngoại tuyến; không suy diễn đã kiểm tra màn hình/menu khác.
- Ảnh extra-current.png xác nhận tên sách, tác giả và tên nhóm “全部” chưa qua dịch. Thông báo yêu cầu đăng nhập trong đoạn ghi đã được dịch sang Việt.
- CacheAdapter dùng setTranslatedUiLabel cho tên sách, tác giả và thông báo xuất; bộ đếm tải dùng uiString. CacheActivity dịch tên nhóm ở thanh phụ, cập nhật khi cấu hình dịch thay đổi.
- Không thay đổi dữ liệu sách/tác giả, tên nhóm trong cơ sở dữ liệu, định danh nhóm, đường dẫn hoặc tác vụ tải/xuất.

- Lượt cache build thành công và cài Success lúc 14:36:56. Ảnh sau cài thực tế là tủ sách rồi Quản lý thẻ; chưa dùng các ảnh này để xác nhận runtime CacheActivity.
- Ảnh extra-menu.png ghi nhận hộp thoại Thêm nhãn còn tiếng Trung. Bổ sung 27 tài nguyên bookshelf_tag_* còn thiếu và chuyển màn hình Compose sang translatedUiString, dịch các tên nhóm/nhãn/sách ở lớp hiển thị. Giữ nguyên query, nội dung nhập và callback gắn nhãn. XML không trùng tên, placeholder khớp bản mặc định.

- Build đầu của lượt nhãn thiếu R.jar tại dexBuilderAppDebug. Build lại trong /private/tmp/legado-extra-final-build với 76 tác vụ tạo mới đạt BUILD SUCCESSFUL; adb install -r trả Success.
- Đối chiếu trực tiếp sau cài: extra-final-cache.png lúc 14:50 xác nhận Bộ đệm ngoại tuyến hiện Tất cả, tên sách/tác giả đã dịch và bộ đếm tải đúng. extra-final-tags.png lúc 14:52 xác nhận mô tả/số sách/số nhãn. extra-final-tag-dialog.png lúc 14:53 xác nhận Thêm nhãn, Nhãn mới, số lượng và hướng dẫn đã bằng tiếng Việt. Không nhập/lưu nhãn mới hoặc thực hiện xóa/tải/xuất trong lượt đối chiếu.

## Lượt ghi quy tắc thay thế và chương TXT

- next-review.mp4 ghi khoảng 31 giây; đã xem khung hình mỗi giây. Hai trang xuất hiện là Thay thế và lọc và Quy tắc chương TXT. Không có menu/hộp thoại/thông báo mở trong bản ghi này.
- ReplaceRuleScreen dịch tên quy tắc/tên nhóm hiển thị bằng translatedUiText. TxtTocRuleScreen dịch tên và ví dụ minh họa. Hai trang dùng translatedUiString cho nhãn tài nguyên.
- Giữ nguyên biểu thức, tên/ví dụ đã lưu, định danh, thứ tự, trạng thái bật/tắt, query và callback chỉnh sửa. Chỉ thay đổi chuỗi hiển thị danh sách.
- Build trong /private/tmp/legado-next-build đạt BUILD SUCCESSFUL (76 tác vụ, 2 phút 15 giây); git diff --check đạt. Cài adb install -r trả Success; thiết bị báo 3.26.100316debug, cập nhật 15:06:34.
- next-after.png lúc 15:07 xác nhận trực tiếp trang Quy tắc chương TXT đã dịch tên và ví dụ. Một số từ điển thuật ngữ chưa tự nhiên; chưa có ảnh đối chiếu sau cài đúng trang Thay thế và lọc. Không coi kiểm chứng TXT là bằng chứng runtime của trang thay thế.
- Đối chiếu bổ sung next-confirmed.png lúc 15:08: đúng trang Thay thế và lọc, tên quy tắc đang thấy đã được dịch. Hai trang của lượt ghi này đều có bằng chứng trực tiếp sau cài.

## Quy tắc từ điển và dấu ngoặc bản dịch

- DictRuleScreen còn hiển thị trực tiếp rule.name. Chuyển nhãn danh sách sang translatedUiText và nhãn tài nguyên sang translatedUiString; giữ nguyên khóa lựa chọn/tên đã lưu và callback quy tắc.
- Bộ dịch dùng ngoặc ASCII [] thay cho cặp 【】, kể cả ngoặc trong nghĩa từ điển. UiTranslation cũng chuẩn hóa chuỗi tài nguyên/nhãn được dịch. Chuỗi không có chữ Hán vẫn đổi 【】 ở đoạn văn bản; TranslationMarkup giữ nguyên thẻ/thuộc tính, comment, script và style.
- Đổi revisionKey của luồng dịch để tránh tái sử dụng kết quả cũ. Không chỉnh dữ liệu quy tắc hoặc nội dung gốc.
- TranslationEngineTest đạt 16/16; bao gồm ngoặc nguồn, nghĩa từ điển, tiêu đề chương và bảo toàn HTML/script. APK build thành công với 83 tác vụ trong 2 phút 10 giây; git diff --check đạt.
- Cài APK trả Success; thiết bị báo cập nhật 15:15:44, phiên bản 3.26.100316debug. dict-confirmed.png lúc 15:20 xác nhận các tên quy tắc từ điển đã dịch. dict-after-start.png lúc 15:17 xác nhận ngoặc [] trong phần mô tả sách đã dịch trên tủ sách. Không dùng ảnh Giao diện ứng dụng lúc điều hướng để chứng minh trang từ điển.
