# Custom reading buttons

Custom buttons add actions to the chapter menu. Button JS must define `function run()` for a short press. Long-press/sign-in behavior follows the supported fields in the examples.

`loginUi` defines input/control fields; the login URL and JS use source state in the relevant context. Preserve field names and return types. Test on the current book before using another source. Buttons execute source code, so import only trusted configurations.

## Examples and API signatures

Method names, keys, sample data and syntax are preserved from the source reference.

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


