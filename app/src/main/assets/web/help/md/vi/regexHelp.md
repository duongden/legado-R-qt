# Biểu thức chính quy

## Cú pháp
| Mẫu | Ý nghĩa |
|---|---|
| `.` | Một ký tự (tùy cờ dòng) |
| `[abc]`, `[^abc]` | Tập ký tự và tập phủ định |
| `*`, `+`, `?` | Lặp 0+, 1+, hoặc tùy chọn |
| `{m,n}` | Số lần lặp trong khoảng |
| `(x)`, `x|y` | Nhóm và lựa chọn |
| `^`, `$` | Đầu/cuối chuỗi hoặc dòng tùy chế độ |
| `\d`, `\s`, `\w` | Chữ số, khoảng trắng, ký tự từ |

## Nhóm và thay thế
Escape ký tự đặc biệt khi cần khớp ký tự thật. Phân biệt greedy và lazy (`*?`, `+?`). Các lookahead/lookbehind tạo điều kiện mà không nhất thiết tiêu thụ ký tự; khả năng hỗ trợ phụ thuộc engine.
Trong chuỗi JSON/JS, dấu gạch chéo ngược thường cần escape thêm một lần. Thử cả trường hợp khớp và không khớp; tránh mẫu lặp lồng nhau trênnội dung chương dài.
