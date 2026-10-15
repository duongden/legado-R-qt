# RSS rules

Define the source URL, item list, titles, links, update dates and article content. XML feeds, HTML pages and JSON need their corresponding parsers.

- Article URLs must identify the content, not just a listing page.
- Pagination and filtering should retain stable identities to avoid duplicates.
- Resolve HTML image/link paths against the base URL.
- Test old/new articles and authenticated mode where applicable.
- Use the JS/source URL guide for headers, cookies and extra processing. Examples below retain protocol syntax.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

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



