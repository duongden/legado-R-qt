# Quy tắc xử lý đoạn

Quy tắc đoạn xử lý toàn chương sau khi phân tíchnội dung chương. Bật riêng cho sách trong trình quản lý quy tắc đoạn; không tự áp dụng cho mọi sách. Dùng với TXT vànội dung chương nguồn; không áp dụng cho EPUB.

Định nghĩa entry point theo ví dụ. Đối tượng `ctx` cung cấp văn bản/ngữ cảnh chương và biến hỗ trợ. Kết quả có thể là chuỗi, mảng đoạn hoặc bảng thay thế đoạn theo giao thức hiện có. Giữ đúng kiểu và chỉ số; không trộn chuỗi JSON với đối tượng JavaScript.
Lưu/đọc biến bằng API được cung cấp. Sau khi đổi quy tắc, làm mới chương hiện tại để kiểm tra. Luôn thử cả chương ngắn/dài và trường hợp không khớp.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```js
function process(ctx) {
  return ctx.result;
}
```


```js
{
  result: "整章正文字符串",
  paragraphs: [
    {
      index: 1,
      text: "第一段正文",
      start: 0,
      end: 5,
      separator: "\n"
    }
  ],
  book: {
    name: "书名",
    author: "作者",
    bookUrl: "书籍地址",
    origin: "书源地址",
    latestChapterTitle: "最新章节",
    durChapterTitle: "当前阅读章节",
    durChapterIndex: 0
  },
  chapter: {
    title: "章节标题",
    url: "章节地址",
    index: 0,
    isVolume: false
  },
  rule: {
    id: 1,
    name: "规则名称"
  },
  vars: {
    key: "value"
  }
}
```


```js
function process(ctx) {
  return ctx.result.replace(/旧文本/g, "新文本");
}
```


```js
function process(ctx) {
  return ctx.paragraphs.map(function(p) {
    return {
      index: p.index,
      text: p.text + "test"
    };
  });
}
```


```js
{
  index: 1,
  text: "处理后的段落正文"
}
```


```js
function process(ctx) {
  var out = [];
  ctx.paragraphs.forEach(function(p) {
    out.push({ index: p.index, text: p.text });
    if (p.index === 1) {
      out.push({ index: 10001, text: "插入的新段落" });
    }
  });
  return out;
}
```


```js
function process(ctx) {
  return {
    1: "替换第一段",
    3: "替换第三段"
  };
}
```


```js
var token = source.get("token");
source.put("token", "value");

var value = java.get("key");
java.put("key", "value");
```


```js
java.refreshParagraph();
```


```js
java.put("token", token);
java.refreshParagraph();
```


