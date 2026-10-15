# Chọn phông với @font:

`@font:` cho phép nội dung nâng cao chọn phông theo lần sử dụng, gồm tiêu đề Lottie, bong bóng SVG và usehtml. Nó không gắn mọi nội dung vào một phông toàn cục.

Chọn đúng tên hoặc tham chiếu phông được app hỗ trợ theo ví dụ. Phông không tìm thấy sẽ dùng cơ chế dự phòng của trình hiển thị. Để tiếng Việt không mất dấu, dùng phông có đủ glyph; chữ Trung cũng cần phạm vi ký tự tương ứng.
Không đổi tên thuộc tính, cú pháp SVG/HTML hoặc tham chiếu tài nguyên khi dịch tài liệu. Khi phông không tải được, kiểm tra đường dẫn, quyền đọc và URL trước.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```text
@font:字体名字
```


```text
@font:思源黑体.ttf
```


```text
思源黑体.ttf
```


```svg
font-family="@font:思源黑体.ttf"
```


```svg
font-family="reader"
```


```html
<usehtml>
<p style="font-family: @font:仓耳今楷.ttf; font-size: 18px;">
  这是 usehtml 里的仓耳今楷
</p>
</usehtml>
```


```html
<usehtml>
<font face="@font:仓耳今楷.ttf">仓耳今楷</font>
<span style="font-family: reader">跟随正文字体</span>
</usehtml>
```


```kotlin
// 解析（找不到则回退）
AppFont.typeface("@font:SourceHan.ttf")
AppFont.tryTypeface("@font:没有") // null，调用方再 fallback

// 手填 + 字体库弹窗（推荐给 UI）
AppFont.pickOrType(activity, currentRef) { ref -> /* 写入 ref */ }

// 仅字体库弹窗
AppFont.pick(fragment, currentRef) { ref -> /* 写入 ref */ }

// 阅读字体变更后清缓存
AppFont.onReaderFontChanged()
```


```js
// JSON 数组：[{ref, displayName, pathOrUri}, ...]
var fonts = JSON.parse(java.getFontList());
// ref 默认带 @font: 前缀，可直接用于 font-family / Lottie / 气泡
// java.getFontList(false) 时 ref 仅为文件名

for (var i = 0; i < fonts.length; i++) {
  // fonts[i].ref          例 "@font:仓耳今楷.ttf"
  // fonts[i].displayName  例 "仓耳今楷.ttf"
  // fonts[i].pathOrUri    本地路径或 content Uri
}
```



