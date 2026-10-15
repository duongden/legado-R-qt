# Book source rules

## Source sections
`bookSourceUrl` identifies a source; `bookSourceName` is its display name. `searchUrl` takes search/page parameters. `exploreUrl` defines categories. `ruleSearch`, `ruleExplore`, `ruleBookInfo`, `ruleToc` and `ruleContent` handle search, Discovery, details, contents and chapters respectively.

## Writing rules
Use the appropriate HTML, JSONPath, XPath or JS parser for the returned data. Interface translation must not rename protocol JSON keys. Relative URLs resolve against the base URL. Preserve the required encoding, method, body and headers.

## Extraction pipeline
1. Inspect the request URL and original response.
2. Extract book lists and stable book URLs.
3. Check title/author, introduction and contents URL.
4. Preserve chapter order, URLs, VIP/purchased flags and contents pagination.
5. Check chapter text, images and continuation pages.

## Variables and sign-in
Placeholders such as `{{key}}` and `{{page}}` belong to URL rules; `@js:` and `<js>` blocks run in the app engine. Reuse source/sign-in state and keep secrets out of logs and exported sources.

## Validation
Test pages 1/2, ongoing/completed books and authenticated sources. Update extraction against actual responses when a site changes. Do not use fabricated chapter text as an error fallback.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

```
@@ 默认规则,直接写时可以省略@@
@XPath: xpath规则,直接写时以//开头可省略@XPath
@Json: json规则,直接写时以$.开头可省略@Json
: regex规则,不可省略,只可以用在书籍列表和目录列表
```

```
//所有按钮类型："text"、"password"、"button"、"toggle"、"select"
规则填写示范
[
    {
        "name": "telephone",
        "type": "text",
        "default": "123"
    },
    {
        "name": "password",
        "type": "password",
        "action": "checkPassword()"
    },
    {
        "name": "注册",
        "type": "button",
        "action": "http://www.yooike.com/xiaoshuo/#/register?title=%E6%B3%A8%E5%86%8C"
    },
    {
        "name": "获取验证码",
        "type": "button",
        "action": "getVerificationCode()",
        "style": {
            "layout_flexGrow": 0,
            "layout_flexShrink": 1,
            "layout_alignSelf": "auto",
            "layout_flexBasisPercent": -1,
            "layout_wrapBefore": false
        }
    },
    {
        "name": "评论开关",
        "type": "toggle",
        "chars": ["❎", "☑️"],
        "default": "☑️"
    },
    {
        "name": "显示书名",
        "viewName": "book?.name||'未获取到书名'",
        "type": "button"
    },
    {
        "name": "选择排序",
        "viewName": "'排序按钮别名'",
        "type": "select",
        "chars": ["月票", "人气"],
        "default": "人气",
        "style": {
            "layout_flexGrow": 0,
            "layout_flexBasisPercent": -1,
            "layout_justifySelf": "flex_end"
        }
    }
]
```

```
示范填写
function login() {
    java.log("模拟登录请求");
    java.log(source.getLoginInfoMap());
}
function getVerificationCode() {
    java.log("登录UI按钮：获取到手机号码"+result.get("telephone"))
}

登录按钮函数获取登录信息
result.get("telephone")
login函数获取登录信息
source.getLoginInfo()
source.getLoginInfoMap().get("telephone")
source登录相关方法,可在js内通过source.调用,可以参考阿里云语音登录
login()
getHeaderMap(hasLoginHeader: Boolean = false)
getLoginHeader(): String?
getLoginHeaderMap(): Map<String, String>?
putLoginHeader(header: String)
removeLoginHeader()
setVariable(variable: String?)
getVariable(): String?
AnalyzeUrl相关函数,js中通过java.调用
initUrl() //重新解析url,可以用于登录检测js登录后重新解析url重新访问
getHeaderMap().putAll(source.getHeaderMap(true)) //重新设置登录头
getStrResponse( jsStr: String? = null, sourceRegex: String? = null) //返回访问结果,文本类型,书源内部重新登录后可调用此方法重新返回结果
getResponse(): Response //返回访问结果,网络朗读引擎采用的是这个,调用登录后在调用这方法可以重新访问,参考阿里云登录检测
```


```js
//读取值
var input = infoMap["关键词"];
//修改值
infoMap["关键词"]="系统";
//替换infoMap
infoMap.set({"键":"值"});
//保存infoMap
infoMap.save();
```

```
//所有按钮类型："url"、"text"、"button"、"toggle"、"select"
规则填写示范
[
  {
    "title": "xxx",
    "url": "",
    "style": {
      "layout_flexGrow": 0,
      "layout_flexShrink": 1,
      "layout_alignSelf": "auto",
      "layout_flexBasisPercent": -1,
      "layout_wrapBefore": false
    }
  },
  {
    "title": "关键词",
    "type": "text"
  }
]
```


```
socks5代理    不支持需要验证的socks5代理
{ "proxy":"socks5://127.0.0.1:1080" }
http代理
{ "proxy":"http://127.0.0.1:1080" }
支持http代理服务器验证
{ "proxy":"http://127.0.0.1:1080@用户名@密码" }
注意:这些请求头是无意义的,会被忽略掉
```


```
https://www.baidu.com,{"js":"java.headerMap.put('xxx', 'yyy')"}
https://www.baidu.com,{"js":"java.url=java.url+'yyyy'"}
```


```
https://www.baidu.com,{"bodyJs":"if(result)'这里的文本作为访问返回的响应体body'else result"}
```


```
https://dns.google,{"dnsIp":"8.8.8.8"}
```


```
(()=>{
  if(page==1){
    let url='https://www.yooread.net/e/search/index.php,'+JSON.stringify({
    "method":"POST",
    "body":"show=title&tempid=1&keyboard="+key
    });
    return source.put('surl',String(java.connect(url).raw().request().url()));
  } else {
    return source.get('surl')+'&page='+(page-1)
  }
})()
或者
(()=>{
  let base='https://www.yooread.net/e/search/';
  if(page==1){
    let url=base+'index.php';
    let body='show=title&tempid=1&keyboard='+key;
    return base+source.put('surl',java.post(url,body,{}).header("Location"));
  } else {
    return base+source.get('surl')+'&page='+(page-1);
  }
})()
```


```
let options = {
"headers": {"User-Agent": "xxxx","Referrer":baseUrl,"Cookie":"aaa=vbbb;"}
};
'<img src="'+src+","+JSON.stringify(options)+'">'
```


```
<js>
(function(){
  var b64=String(src).match(/ttf;base64,([^\)]+)/);
  if(b64){
    var f1 = java.queryTTF(b64[1]);
    var f2 = java.queryTTF("https://alanskycn.gitee.io/teachme/assets/font/Source Han Sans CN Regular.ttf");
    // return java.replaceFont(result, f1, f2);
    return java.replaceFont(result, f1, f2, true); // 过滤掉f1中不存在的字形
  }
  return result;
})()
</js>
```


```js
"clickBookName" //点击详情页书名
"longClickBookName" //长按详情页书名
"clickAuthor" //点击详情页作者
"longClickAuthor" //长按详情页作者
"clickCustomButton" //点击书源自定义按钮
"longClickCustomButton" //长按书源自定义按钮（只存在小说的正文界面）
"clickShareBook" //点击详情页分享按钮
"clickClearCache" //点击详情页清理缓存按钮
"clickCopyBookUrl" //点击详情页拷贝书籍URl按钮
"clickCopyTocUrl" //点击详情页拷贝目录URl按钮
"clickCopyPlayUrl" //音频、视频界面点击拷贝播放URL按钮
"clickBookLabel" //点击详情页标签
"longClickBookLabel" //长按详情页标签
//上面的事件回调执行结果返回true会消费事件，原本的软件操作不会再执行

//下面的事件无法被回调结果消费
"addBookShelf" //添加到书架
"delBookShelf" //移除书架
"saveRead" //保存阅读进度
"startRead" //开始阅读
"endRead" //结束阅读
"startShelfRefresh" //开始书架刷新
"endShelfRefresh" //结束书架刷新
```


```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```


```js
function decodeImage(data, key) {
  var input = new Packages.java.io.ByteArrayInputStream(data)
  var out = new Packages.java.io.ByteArrayOutputStream()
  var byte
  while ((byte = input.read()) != -1) {
    out.write(byte ^ key)
  }
  return out.toByteArray()
}

decodeImage(result, key)
```


```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```


```js
function decodeImage(data, key) {
  var out = new Packages.java.io.ByteArrayOutputStream()
  var byte
  while ((byte = data.read()) != -1) {
    out.write(byte ^ key)
  }
  return out.toByteArray()
}

decodeImage(result, key)
```


```js
window.run("java.toast('执行成功');'成功'")
.then(r=>alert(r))
.catch(e=>alert("执行出错:"+e));
```


```js
//建议使用
var url = `https://www.baidu.com/img/flexible/logo/pc/result.png,{"click": "java.toast('这是'+book.name+'正文的图被点击了');"}`;
result = `<img src = "${url}">`;
```

```js
//不建议使用
var url = `https://www.baidu.com/img/flexible/logo/pc/result.png,{"js": "if (book) java.toast('这是'+book.name+'正文的图被点击了');result", "style": "TEXT"}`;
result = `<img src = "${url}">`;
```


```
<img src = "https://du.com/result.png,{'style': 'center','width':'50%'}">
<img src = "https://du.com/result.png,{'style': 'right','width':'300'}">
<img src = "data:image/svg+xml;base64,QQ,{'style': 'left','width':'100%'}">
```


```xml
<usehtml>
<p style="text-align:end">右对齐文本</p>
<button>点我@onclick:java.toast("Hello World")</button>
</usehtml>
```

```xml
<usehtml>
<p style="width:60%;text-align:left;">
左对齐文本块
</p>
<p style="width:60%;margin-left:auto;margin-right:auto;text-align:center;">
居中文本块
</p>
<p style="width:60%;margin-left:auto;margin-right:auto;text-align:left;">
居中文本块, 块内文字跟随正文两端对齐设置
</p>
<p style="width:60%;margin-left:auto;text-align:right;">
右对齐文本块
</p>
<div style="width:72%;padding:.7em .9em;background:rgba(68,150,211,.14);border-radius:12px;text-align:left;">
左侧气泡文本
</div>
<div style="width:72%;padding:.1em .1em;background:rgba(68,150,211,.14);border-radius:12px;text-align:left;">
极小内边距气泡文本
</div>
<div style="width:72%;padding:.1em .1em;background:rgba(68,150,211,.14);border-radius:12px;text-align:left;">
容器内段评文本<img src="dp:5,{&quot;status&quot;:&quot;normal&quot;,&quot;pclick&quot;:&quot;rule:xxx&quot;}">
</div>
<div style="width:72%;margin-left:auto;padding:.7em .9em;background:#e8f3ff;border:1px solid rgba(68,150,211,.45);border-radius:12px;text-align:left;">
右侧气泡文本
</div>
<div style="width:80%;margin-left:auto;margin-right:auto;padding:.8em;background:rgba(0,0,0,.08);border-radius:10px;text-align:center;">
居中提示块
</div>
</usehtml>
```

```xml
<usehtml>
<p data-legado-layout="text-box" data-legado-width="60%" style="margin-left:auto;margin-right:auto;text-align:center;">
明确启用的居中文本块
</p>
</usehtml>
```

