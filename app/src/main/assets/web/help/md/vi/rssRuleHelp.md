# Quy tắc RSS

Khai báo địa chỉ nguồn, danh sách bài, tiêu đề, liên kết, ngày cập nhật và nội dung. Feed XML, trang HTML và JSON cần bộ phân tích tương ứng.

- URL bài phải trỏ đúng nội dung, không chỉ trang danh sách.
- Phân trang và lọc bài phải giữ định danh ổn định để tránh trùng.
- Nội dung HTML cần xử lý đường dẫn ảnh/liên kết theo URL cơ sở.
- Kiểm tra một bài cũ và một bài mới, cả chế độ đăng nhập nếu có.
- Dùng trợ giúp JS/URL nguồn cho header, cookie và xử lý bổ sung. Các ví dụ bên dưới giữ nguyên cú pháp giao thức.

## Ví dụ và chữ ký API

Tên hàm, khóa, dữ liệu mẫu và cú pháp được giữ nguyên từ tài liệu nguồn.

```
@@ 默认规则,直接写时可以省略@@
@XPath: xpath规则,直接写时以//开头可省略@XPath
@Json: json规则,直接写时以$.开头可省略@Json
: regex规则,不可省略,只可以用在书籍列表和目录列表
```

```js
//在预注入Js规则里
window.ajaxAwait = ajaxAwait;
window.java = java;
//在网页内容
window.ajaxAwait("https://example.com")
.then(r=>alert(r));
java.toast("调用java函数");
```


```js
//执行阅读函数代码字符串，并返回字符串结果
run("java.toast('执行成功');'成功'")
.then(r=>alert(r))
.catch(e=>alert("执行出错:"+e));
//用java.ajax异步访问
ajaxAwait(url, callTimeout)
//用java.connect异步访问，返回序列化后的响应
connectAwait(url, header, callTimeout)
//返回响应体结果
getAwait(url, header, callTimeout)
//返回序列化后的响应头
headAwait(url, header, callTimeout)
//返回响应体结果
postAwait(url, body, header, callTimeout)
//用java.webView异步访问
webViewAwait(html, url, js, cacheFirst)
//用java.webViewGetSourceAwait异步访问
webViewGetSourceAwait(html, url, js, sourceRegex, cacheFirst, delayTime)
//同java.createSymmetricCrypto(transformation, key, iv).decryptStr(data)
decryptStrAwait(transformation, key, iv, data)
//同java.createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
encryptBase64Await(transformation, key, iv, data)
//同java.createSymmetricCrypto(transformation, key, iv).encryptHex(data)
encryptHexAwait(transformation, key, iv, data)
//同java.createSign(algorithm).setPublicKey(publicKey).setPrivateKey(privateKey).signHex(data)
createSignHexAwait(algorithm, publicKey, privateKey, data)
//同java.downloadFile(url)
downloadFileAwait(url)
//同java.readTxtFile(url)
readTxtFileAwait(path)
//同java.importScript(url)
importScriptAwait(url)
//同java.getString(ruleStr, mContent)
getStringAwait(ruleStr, mContent)

```

```js
//部分示例参考
java.put("cs","v");
java.toast("cs"):
java.ajax("https://example.com");
java.get("https://example.com","{}");
java.base64Encode("cs");
java.aesDecodeToString("str","key","tran","iv");
java.searchBook("系统");
source.login();
source.getLoginInfo();
cache.putMemory("cs","v");
```



