# legado-R-qt

Ứng dụng đọc sách Android dựa trên Legado, có giao diện web và mã xử lý dịch
Trung–Việt. Dự án phục vụ học tập và nghiên cứu, phát hành theo **GPL-3.0**.

## Tải ứng dụng

**[Tải APK release](https://github.com/duongden/legado-R-qt/releases/latest)**

APK phát hành được đặt trong GitHub Releases. Kho mã nguồn chỉ duy trì nhánh `main`;
không có bot tự tạo nhánh cập nhật dependency.

## Phạm vi bản này

- Gồm mã nguồn Android và web; Worker relay là dự án riêng.
- Không kèm Names.dat, VietPhrase.dat, ChinesePhienAmWords.txt hoặc Rule.txt.
  Bạn có thể nhập dữ liệu TXT của mình trong phần quản lý từ điển.
- Không kèm ba bộ theme Asuka, Doraemon và Minecraft. Theme mặc định là
  “诡秘之主 · 灰雾之上”; các icon và tài nguyên còn lại có ghi nhận nguồn.
- Không công khai keystore, mật khẩu ký, cấu hình máy hoặc lịch sử Git cũ.

## Build

Cần JDK 17 và Android SDK Platform 36. Thiết lập `ANDROID_HOME` trên máy của bạn:

```sh
./gradlew :app:assembleAppRelease
```

Nếu chưa cấu hình khóa ký riêng, lệnh trên tạo APK release chưa ký. Không đưa
keystore hoặc mật khẩu vào Git. Xem [hướng dẫn build và phạm vi phát hành](docs/PUBLICATION.md).

## Nguồn gốc và giấy phép

Cảm ơn [gedoor/legado](https://github.com/gedoor/legado),
[Luoyacheng/legado](https://github.com/Luoyacheng/legado),
[Rimchars/legado](https://github.com/Rimchars/legado) và các tác giả thành phần.
Phần dịch kế thừa mã từ [legado-qt](https://github.com/duongden/legado-qt)
và [VietPhrase translator](https://github.com/duongden/duongden-vietphrase-translator).

Giữ nguyên [GPL-3.0](LICENSE), [ghi nhận thành phần](app/src/main/assets/LICENSE.md)
và các giấy phép đi kèm. Mục đích học tập không thay đổi quyền và nghĩa vụ theo
các giấy phép này. [README upstream](docs/UPSTREAM_README.md) được giữ để đối chiếu
nguồn gốc; những bản phát hành được nhắc trong đó thuộc dự án upstream.
