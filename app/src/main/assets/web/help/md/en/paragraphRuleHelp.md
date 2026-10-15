# Paragraph processing

Paragraph rules process a whole chapter after extraction. Enable them per book in paragraph-rule management; they are not applied to every book automatically. They apply to TXT/source text, not EPUB.

Define the entry point shown in the examples. `ctx` provides chapter/text context and supported variables. Return a string, paragraph array or paragraph replacement map according to the existing contract. Keep types/indices correct; do not confuse JSON strings with JS objects.
Read/write variables through the supplied APIs. Refresh the current chapter after changes. Test short/long chapters and nonmatching cases.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

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


