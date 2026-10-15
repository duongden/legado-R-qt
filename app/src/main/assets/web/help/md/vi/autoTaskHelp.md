# Tác vụ tự động

Tác vụ dùng Cron năm trường để chạy JS đồng bộ theo lịch. Môi trường dùng chung khả năng nguồn, đăng nhập, cookie và cache. Giá trị trả về cuối script được phân tích thành hành động cập nhật mục lục hoặc thông báo.

## Cron
Thứ tự là phút, giờ, ngày trong tháng, tháng, ngày trong tuần. Dùng `*`, danh sách, khoảng và bước theo cú pháp của app. Chọn lịch vừa đủ; điều kiện pin/mạng/hệ thống có thể ảnh hưởng lúc chạy thực tế.

## Kết quả
Giữ đúng kiểu và khóa của giao thức trả về trong ví dụ. Tác vụ cập nhật sách cần định danh sách hợp lệ; thông báo cần nội dung tương ứng. Kiểm tra chạy thủ công và log trước khi bật lịch.
Không đặt tác vụ mạng liên tục hoặc ghi bí mật vào thông báo.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```json
[
  { "type": "refreshToc", "bookUrl": "https://..." },
  { "type": "notify", "title": "完成", "content": "全部更新完毕" }
]
```


```json
{ "type": "notify", "title": "签到", "content": "已签到 {time}" }
```


```json
{
  "actions": [
    { "type": "refreshToc", "bookUrl": "https://..." }
  ]
}
```


```json
{
  "type": "refreshToc",
  "bookUrl": "https://example.com/book/123",
  "notify": {
    "enable": true,
    "minCount": 1,
    "title": "《{book}》更新了",
    "content": "新增 {newCount} 章\n最新：{chapter}\n时间：{time}"
  },
  "cache": {
    "enable": true
  }
}
```


```json
{
  "type": "notify",
  "title": "每日签到",
  "content": "已完成\n时间：{time}",
  "level": "high",
  "id": 1
}
```


