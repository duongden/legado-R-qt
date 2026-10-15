# JavaScript and source APIs

## Runtime
Source JS runs in Rhino inside the app, not in a browser. Syntax support and exposed objects depend on context. Do not assume browser DOM, `fetch`, Node.js or web-style Promise behavior.

## Objects
- `java`: app extension functions; see signatures/examples below.
- `source`: source configuration/state, variables and sign-in.
- `book`: title, author, URL, contents, variables and progress.
- `chapter`: chapter index/title/URL where provided.
- `cookie`, `cache`: the app cookie/cache APIs.
- `result`: input/intermediate data where supported by the rule.

## URLs and parsing
`AnalyzeUrl` handles URL/requests; `AnalyzeRule` handles extraction rules. Use the correct string, element or list-returning method. Preserve types across the Java/JS boundary and check nulls before calling methods.

## Specialized contexts
Login, RSS, custom-button and player APIs exist only in their corresponding contexts. A player function cannot be assumed to work in a search rule. Examples retain method/parameter names for comparison with source code.

## State and errors
Store only necessary data; keep sign-in secrets out of display fields and exported sources. Bound loops and request counts. Keep source errors visible instead of returning fabricated chapter text. Test in the debugger and then verify in the reader.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

```js
* @param key 搜索关键词
* @param searchScope 搜索作用域，为空时调用所以书源搜索
//searchScope作用域,形式为`源名称::源地址`、或者`,`符号隔开的源分组名称
//在书源调用时可写为java.searchBook(key, source)，仅本书源进行搜索
java.searchBook(key: String, searchScope: String? = null)
```


```js
java.addBook(bookUrl: String)
```


```js
* @param name 为"sort"打开订阅源分类界面、为"rss"打开订阅源正文界面、为"explore"打开书源发现界面、"search"打开书籍搜索界面、"login"打开源登录界面
* @param url 为传递到界面的链接，"sort"时为分类链接、"rss"时为正文链接、"explore"时为发现链接，"search"、"login"时该参数无意义
//特别说明，"sort"时url可以传序列化后的键值对用来打开多个分类界面
* @param title 为对应界面的标题，"search"时为搜索关键词，"login"时该参数无意义
* @param origin 打开指定源界面的源地址
java.open(name: String, url: String? = null, title: String? = null, origin: String? = null)
```


```js
java.showPhoto(src: String)
```


```js
//用内置浏览器打开本地html
* @param url 指定网页的基础URL，解决本地网页跨越问题
* @param html 加载的内容
* @param preloadJs 预注入js，用法同订阅源预注入js规则
* @param config 界面配置，json字符串，
java.showBrowser(url: String, html: String, preloadJs: String? = null, config: String? = null)
//复制文本到剪贴板
java.copyText(text: String)
//实时更新登录界面用户信息，upLoginData(null)会全部重置为默认值
java.upLoginData(data: Map<String, String?>?)
//刷新登录界面
java.reLoginView(deltaUp: Boolean = false)
//刷新书籍详情页
java.refreshBookInfo()
//刷新书籍目录页
java.refreshBookToc()
//刷新书籍正文内容
java.refreshContent()
//清除tts源的缓存，仅限tts源的登录界面
java.clearTtsCache()
//刷新发现，仅限发现按钮
java.refreshExplore()
```

```js
initUrl() //重新解析url,可以用于登录检测js登录后重新解析url重新访问
getHeaderMap().putAll(source.getHeaderMap(true)) //重新设置登录头
getStrResponse( jsStr: String? = null, sourceRegex: String? = null) //返回访问结果,文本类型,书源内部重新登录后可调用此方法重新返回结果
getResponse(): Response //返回访问结果,网络朗读引擎采用的是这个,调用登录后在调用这方法可以重新访问,参考阿里云登录检测
```


```js
java.getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
java.getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
```

```js
java.setContent(content: Any?, baseUrl: String? = null):
```


```js
java.getElement(ruleStr: String)
java.getElements(ruleStr: String)
```


```js
java.reGetBook()
java.refreshTocUrl()
```

```js
java.get(key)
java.put(key, value)
```


```js
java.toURL(url: String, baseUrl: String? = null): JsURL
```

```js
java.getWebViewUA(): String
java.getFontList(): String  // JSON 数组 [{ref,displayName,pathOrUri}]
java.getFontList(withAtFontPrefix: Boolean): String
```

```js

java.ajax(urlStr, callTimeout: Int? = null): String

* 并发访问网络
* @param skipRateLimit 为true时不受源并发率限制
java.ajaxAll(urlList: Array<String>, skipRateLimit: Boolean = false): Array<StrResponse>

//ajaxTestAll会忽略网络访问错误，错误类型由callTime()获取，对应的错误码值（-1超过设定时间，-2超时，-3域名错误，-4连接被拒绝，-5连接被重置，-6SSL证书错误，-7其它错误），无错误时callTime()为响应时间
java.ajaxTestAll(urlList: Array<String>, timeout: Int, skipRateLimit: Boolean = false): Array<StrResponse>

java.connect(urlStr, header = null, callTimeout: Int? = null): StrResponse
//返回的StrResponse对象具有的方法 body() code() message() headers() raw() toString() callTime()

java.post(url: String, body: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response

java.get(url: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response

java.head(url: String, headerMap: Map<String, String>, timeout: Int? = null): Connection.Response

* 使用webView访问网络
* @param html 直接用webView载入的html, 如果html为空直接访问url
* @param url html内如果有相对路径的资源不传入url访问不了
* @param js 用来取返回值的js语句, 没有就返回整个源代码
* @param cacheFirst 优先使用缓存,为true能提高访问速度
* @param delayTime 延迟执行js的时间
* @return 返回js获取的内容
java.webView(html: String?, url: String?, js: String?, cacheFirst: Boolean = false): String?

* 使用webView获取跳转url
java.webViewGetOverrideUrl(html: String?, url: String?, js: String?, overrideUrlRegex: String, cacheFirst: Boolean = false, delayTime: Long = 0): String?

* 使用webView获取资源url
java.webViewGetOverrideUrl(html: String?, url: String?, js: String?, overrideUrlRegex: String, cacheFirst: Boolean = false, delayTime: Long = 0): String?

* 使用内置浏览器打开链接，可用于获取验证码 手动验证网站防爬
* @param url 要打开的链接
* @param title 浏览器的标题
* @param html 本地html代码
java.startBrowser(url: String, title: String, html: String? = null)

* 使用内置浏览器打开链接，并等待网页结果 .body()获取网页内容
* @param refetchAfterSuccess 为false时获取最终展示界面的源码
java.startBrowserAwait(url: String, title: String, refetchAfterSuccess: Boolean = false, html: String? = null): StrResponse
```

```js
java.log(msg)
java.logType(var)
```

```js
java.getVerificationCode(imageUrl)
```

```js
java.longToast(msg: Any?)
java.toast(msg: Any?)
```

```js
java.getReadBookConfig(): String
java.getReadBookConfigMap(): Map<String, Any>
```

```js
java.getThemeConfig(): String
java.getThemeConfigMap(): Map<String, Any?>
```

```js
* @return 0 跟随系统，1 亮色主题，2 暗色主题，3 墨水屏
fun getThemeMode(): String
```

```js
java.importScript(url)
//相对路径支持android/data/{package}/cache
java.importScript(relativePath)
java.importScript(absolutePath)
```

```js
获取
java.cacheFile(url)
java.cacheFile(url,saveTime)
执行内容
eval(String(java.cacheFile(url)))
使缓存失效
cache.delete(java.md5Encode16(url))
```

```js
java.get*StringContent(url: String, path: String): String

java.get*StringContent(url: String, path: String, charsetName: String): String

java.get*ByteArrayContent(url: String, path: String): ByteArray?

```

```js
java.encodeURI(str: String, enc: String = "UTF-8")
```

```js
java.base64Decode(str: String)
java.base64Decode(str: String, charset: String)
java.base64DecodeToByteArray(str: String, flags: Int)
java.base64Encode(str: String, flags: Int)
```

```js
Str转Bytes
java.strToBytes(str: String)
java.strToBytes(str: String, charset: String)
Bytes转Str
java.bytesToStr(bytes: ByteArray)
java.bytesToStr(bytes: ByteArray, charset: String)
```

```js
HexString 解码为字节数组
java.hexDecodeToByteArray(hex: String)
hexString 解码为utf8String
java.hexDecodeToString(hex: String)
utf8 编码为hexString
java.hexEncodeToString(utf8: String)
```

```js
java.randomUUID()
java.androidId()
```

```js
将文本转换为简体
java.t2s(text: String): String
将文本转换为繁体
java.s2t(text: String): String
```

```js
java.timeFormatUTC(time: Long, format: String, sh: Int): String?
java.timeFormat(time: Long): String
```

```js
java.htmlFormat(str: String): String
```

```js
//文件下载 url用于生成文件名，返回文件路径
downloadFile(url: String): String
//文件解压,zipPath为压缩文件路径，返回解压路径
unArchiveFile(zipPath: String): String
unzipFile(zipPath: String): String
unrarFile(zipPath: String): String
un7zFile(zipPath: String): String
//文件夹内所有文件读取
getTxtInFolder(unzipPath: String): String
//读取文本文件
readTxtFile(path: String): String
//删除文件
deleteFile(path: String) 
```


```js
// 创建Cipher
java.createSymmetricCrypto(transformation, key, iv)
```

```js
//解密为ByteArray String
cipher.decrypt(data)
cipher.decryptStr(data)
//加密为ByteArray Base64字符 HEX字符
cipher.encrypt(data)
cipher.encryptBase64(data)
cipher.encryptHex(data)
```

```js
//创建cipher
java.createAsymmetricCrypto(transformation)
//设置密钥
.setPublicKey(key)
.setPrivateKey(key)

```

```js
//解密为ByteArray String
cipher.decrypt(data,  usePublicKey: Boolean? = true)
cipher.decryptStr(data, usePublicKey: Boolean? = true)
//加密为ByteArray Base64字符 HEX字符
cipher.encrypt(data,  usePublicKey: Boolean? = true)
cipher.encryptBase64(data,  usePublicKey: Boolean? = true)
cipher.encryptHex(data,  usePublicKey: Boolean? = true)
```

```js
//创建Sign
java.createSign(algorithm)
//设置密钥
.setPublicKey(key)
.setPrivateKey(key)
```

```js
//签名输出 ByteArray HexString
sign.sign(data)
sign.signHex(data)
```

```js
java.digestHex(data: String, algorithm: String,): String?

java.digestBase64Str(data: String, algorithm: String,): String?
```

```js
java.md5Encode(str: String)
java.md5Encode16(str: String)
```

```js
java.HMacHex(data: String, algorithm: String, key: String): String

java.HMacBase64(data: String, algorithm: String, key: String): String
```


```js
bookUrl // 详情页Url(本地书源存储完整文件路径)
tocUrl // 目录页Url (toc=table of Contents)
origin // 书源URL(默认BookType.local)
originName //书源名称 or 本地书籍文件名
name // 书籍名称(书源获取)
author // 作者名称(书源获取)
kind // 分类信息(书源获取)
customTag // 分类信息(用户修改)
coverUrl // 封面Url(书源获取)
customCoverUrl // 封面Url(用户修改)
intro // 简介内容(书源获取)
customIntro // 简介内容(用户修改)
charset // 自定义字符集名称(仅适用于本地书籍)
type // 0:text 1:audio
group // 自定义分组索引号
latestChapterTitle // 最新章节标题
latestChapterTime // 最新章节标题更新时间
lastCheckTime // 最近一次更新书籍信息的时间
lastCheckCount // 最近一次发现新章节的数量
totalChapterNum // 书籍目录总数
durChapterTitle // 当前章节名称
durChapterIndex // 当前章节索引
durChapterPos // 当前阅读的进度(首行字符的索引位置)
durChapterTime // 最近一次阅读书籍的时间(打开正文的时间)
canUpdate // 刷新书架时更新书籍信息
order // 手动排序
originOrder //书源排序
variable // 自定义书籍变量信息(用于书源规则检索书籍信息)
 ```
## book对象的部分可用函数
 * 自定义书籍变量存取
```js
book.putVariable(key: String, variable: String?)
book.getVariable(key: String): String?
```


```js
chapter.putVariable(key: String, variable: String?)
chapter.getVariable(key: String): String?
//在函数回调或登录界面等地方调用，chapter自身不会进行保存，需要调用chapter.update()
```

```js
 chapter.putLyric(value: String?) // 存储音频章节歌词
 chapter.putImgUrl(value: String?) // 存储章节图标链接，比如标题上的段评图标链接
 ```
 
## source对象的部分可用函数
* 获取书源url
```js
source.getKey()
```

```js
source.putVariable(variable: String?)
source.getVariable()
```

```js
source.put(key: String, variable: String?)
source.get(key: String): String?
```

```js
获取登录头
source.getLoginHeader()
获取登录头某一键值
source.getLoginHeaderMap().get(key: String)
保存登录头
source.putLoginHeader(header: String)
清除登录头
source.removeLoginHeader()
```

```js
login函数获取登录信息
source.getLoginInfo()
login函数获取登录信息键值
source.getLoginInfoMap().get(key: String)
清除登录信息
source.removeLoginInfo()
login函数存放登录信息，  
在登录界面时请调用java.upLoginData
source.putLoginInfo()
```

```js
刷新发现
source.refreshExplore()
刷新jslib
source.refreshJSLib()
```

```js
获取全部cookie
cookie.getCookie(url: String)
获取cookie某一键值
cookie.getKey(url: String, key: String)
设置cookie
cookie.setCookie(url: String, cookie: String)
替换cookie
cookie.replaceCookie(url: String, cookie: String)
删除cookie
cookie.removeCookie(url: String)
设置内置浏览器cookie
cookie.setWebCookie(url: String, cookie: String)
```


```js
保存,saveTime为0时无过期时间
cache.put(key: String, value: String, saveTime: Int = 0)
读取数据库,onlyDisk为true时只从磁盘读取
cache.get(key: String, onlyDisk: Boolean = false): String?
删除
cache.delete(key: String)
缓存文件内容
cache.putFile(key: String, value: String, saveTime: Int)
读取文件内容
cache.getFile(key: String): String?
保存到内存
cache.putMemory(key: String, value: Any)
读取内存
cache.getFromMemory(key: String): Any?
删除内存
cache.deleteMemory(key: String)
```


```js
// 跳转外部链接，传入http链接或者scheme跳转到浏览器或其他应用
// 指定mimeType，可以跳转指定类型应用，例如（video/*）
java.openUrl(url: String, mimeType: String = null)
```

```js
* @param url 视频播放链接
* @param title 视频的标题
* @param isFloat 是否悬浮窗打开
java.openVideoPlayer(url: String, title: String, isFloat: Boolean = false)
```

