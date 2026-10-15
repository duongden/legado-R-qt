# Nút đọc tùy chỉnh

Nút tùy chỉnh thêm hành động vào menunội dung chương. Phần JS của nút phải định nghĩa `function run()` cho nhấn ngắn. Hành động nhấn giữ và đăng nhập theo trường được hỗ trợ trong ví dụ.

`loginUi` mô tả trường nhập/điều khiển; login URL và JS dùng trạng thái nguồn theo ngữ cảnh. Giữ nguyên tên trường và kiểu trả về. Thử trên sách hiện tại trước khi dùng trên nguồn khác. Nút chạy mã của nguồn nên chỉ nhập cấu hình bạn tin cậy.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```js
@js:
JSON.stringify([
  {name:"bookName", type:"text", viewName:"'书名'", default:book ? book.name : ""},
  {name:"chapterTitle", type:"text", viewName:"'章节名'", default:chapter ? chapter.title : ""},
  {name:"submit", type:"button", viewName:"'提交'", action:"java.toast(JSON.stringify(result))"}
])
```


```js
function run() {
  java.toast(
    "书名：" + (book ? book.name : "book=null") +
    "\n章节：" + (chapter ? chapter.title : "chapter=null") +
    "\n正文长度：" + ((content || "").length)
  );
  return true;
}
```


```js
function login() {
  java.toast(
    "登录提交\n书名：" + (book ? book.name : "book=null") +
    "\n章节：" + (chapter ? chapter.title : "chapter=null") +
    "\n表单：" + JSON.stringify(result)
  );
  return true;
}
```


