# 普通正文页面模板

在普通正文的 EPUB 渲染模式中打开阅读菜单的「界面」，直接进入「页面模板」。此模式始终由页面模板决定排版，不提供「原有排版」选项；未选择模板时自动使用「Minecraft · 天光漫游」。可以直接应用内置模板，也可以复制后编辑。分页模板编辑首页 HTML、续页 HTML；滚动模板只编辑一份滚动 HTML。两种类型均支持 CSS 和 JavaScript。原生正文与出版 EPUB 的排版不受模板选择影响。

内置五套模板：「Minecraft · 天光漫游」以主世界天空、方块浮岛与苦力怕开篇，续页进入有钻石矿和火把的矿洞，正文放在地图纸上，物品栏与经验进度条组成页脚；「明日香 · 赤色同步」以朱红人物海报开篇，续页使用炭黑驾驶舱、二号机背景与暖白书页；「诡秘之主 · 灰雾之上」以按章节抽取的真实塔罗三牌阵开篇，标题使用愚者、倒吊人等对应牌名，续页切换为星盘与古金手札，纸页和天空随本地时间变化；「哆啦 A 梦 · 未来口袋」使用官网人物插画、蓝天、铃铛、任意门与口袋书卷，整章连续滚动；「古籍竖排」使用自上而下、从右向左的正文、书框与分页前的图文适配。可以复制后自由改写，也可以通过文件分享自己的模板。「鎏金花笺」「花间书页」「星幕月相」「清爽书页」「花园信笺」「小猫游记」「书刊」已退出内置列表，原先复制的用户模板保留。

「天光漫游」首页与续页使用不同的 HTML 结构，各自固定页眉、正文区和页脚。Minecraft 1.21.5 原版贴图与 OFL 像素字体以 data URI 内置，来源、版权与处理方式见 `tools/reader-templates/minecraft/SOURCES.md`；这是非官方 Minecraft 主题。日夜、天空渐变和日月轨迹取设备本地时间；太阳按 6:00—18:00 的装饰轨迹移动，月亮走夜间轨迹，不定位或查询天气。苦力怕、小白、微光、云朵和火焰只在当前页运行，拖动、截图与减少动态效果时停止，不改变正文尺寸，也不需要阅读时联网。长章名保留在正文流中，可以正常换行、跨页；首段使用首行强调，避免首字伪元素影响组合 emoji 的原生选区。普通段落允许跨页，对话段和段落编号不把整段锁在一页。正文保持标点完整字宽，避免分页裁切前后标点压缩变化造成越界。窄屏和横屏会缩小页眉及装饰，为正文保留空间。

「赤色同步」内置 EvaGeeks 的明日香原作画面及 Barlow Condensed 字体，来源与处理记录见 `tools/reader-templates/asuka/SOURCES.md`。页脚同步率对应本章阅读进度；扫描光、波形与连接灯只在当前页运行，不改变正文尺寸。标题、首段和对话段在分页前装饰，接排片段不重复章名装饰与首行强调。系统安全区由整页背景连续覆盖，正文和信息栏分别避让。此主题不读取时间、电量，相关更新不会清掉它的已验证快照。

「赤色同步」与「灰雾之上」允许正文段落和预格式文本只剩一行时跨页，不强制在页首、页尾各保留两行，减少段尾反引号与段评气泡换行后额外推走正文造成的留白。未指定独立图片布局、但带段评类型或段评点击操作的 img 标签，在正文分段之前就按行内图片识别；即使书源省略 style="text"，也不会把引号后的气泡拆成独立插图。气泡保留原图片和点击动作；剩余空间不足一行或一个完整气泡时，正常续到下一页。

「灰雾之上」内置从 Wikimedia Commons 获取的二十二张莱德—韦特—史密斯塔罗牌原图，由 Pamela Colman Smith 绘制，1909 年出版，属于公有领域。首页三张大图与页脚缩略图分别压缩保存，牌名、题词和三牌组合按书名与章节稳定抽取；同章重新分页、翻回首页和读取快照不会重新抽牌。本章第 23 页回到最初抽中的牌，页脚中心牌与续页右上角同步。晨雾、日光、暮色、绯红长夜按本地时间每十五分钟更新纸页、文字和天体位置，不改变正文尺寸。图片、Cinzel 字体及 OFL 许可均内置，来源见 `tools/reader-templates/lord-of-mysteries/SOURCES.md`。星盘、天体与首页单层灰雾保持静态；取消粒子、扫光、全页纹理和续页雾层，并精简塔罗牌叠加阴影。主题没有常驻动画或逐帧计时器，换页时只更新牌面与页码，阅读时不需要联网。

塔罗图像直接绑定到相应卡片的样式规则，正文和页面外壳不再继承整套图片的 data URI。完成所有卡片绑定后，集中解码本章实际使用的图片，等待解码完成才提交可翻阅的页面；同一牌面只解码一次，减少首个续页的样式重算和图片准备开销。

五套内置模板自带完整配色、字体、字号、行距和段落样式，可直接在模板 CSS 开头修改；原阅读样式的背景、字体、字号、页边距、页眉页脚及底部对齐不参与排版。原字体工具、字号与间距滑块、底部圆形样式列表、原页眉页脚设置和日夜配色切换入口均隐藏。系统亮度、屏幕方向与导航操作继续可用；段评气泡及其点击动作保留。

管理列表复用应用现有的卡片、操作菜单、文件选择与确认弹窗。每张卡片可直接应用、编辑、预览、导出或删除。删除当前模板后，优先选择可用的默认模板，否则选择其他可用模板；全部删除时只恢复默认模板的可见状态，始终保持页面模板排版。右上角菜单可以恢复当前五套内置模板，已退出内置列表的项目不会复活。升级后旧内置模板引用会清理，再按相同规则选择模板。三套插画主题使用独立 ID `builtin.minecraft_live`、`builtin.asuka_sync` 和 `builtin.lord_of_mysteries`，旧 `builtin.minecraft`、`builtin.gilded`、`builtin.flower`、`builtin.night` 备份快照不会覆盖它们；用户副本仍按原文保留。完整说明放在「使用与编写帮助」。

列表顶部的「主题翻页方式」设置当前应用的分页模板，可选择覆盖、联动覆盖、滑动、仿真、滚动、无动画或跟随阅读设置。每套分页模板在本机分别记忆，阅读菜单中的翻页设置也写入同一偏好。滚动模板固定为滚动，不显示可切换的动画选项，配置层也强制该方式；即使书籍或全局设置为仿真翻页也不会改变。分页模板的滚动偏好仍保留分页外壳，和下面的连续滚动模板是两种能力。

这是高级排版能力。复杂模板会增加首次分页的开销；预览使用正式阅读器的独立模板排版与资源加载链路。格式校验通过只表示模板可以保存，实际布局仍需预览。模板错误、分页停滞或脚本长时间无响应会结束本次渲染，并保留已显示的页面或展示错误；模板选择和代码保留，不会自动套回原阅读样式。

分页模板在手指按下时立即暂停装饰，不为暂停、恢复动画单独阻塞两帧；真正提交目标页和截取静止画面时仍等待绘制完成。当前页和相邻页的新截图通过校验后提前调用绘图准备，减少起拖首帧同时上传纹理的开销。截图数量和内存预算保持原有限制。

按下手指时，已完成章节分页的渲染器仍可补齐紧邻页截图，也允许读取当前页和紧邻页的磁盘快照；远页任务和新文档的后台排版暂停。确定拖动方向后优先准备该目标，动画已经持有的截图不重复生成。若截图在拖动中才就绪，直接交给当前手势，不再等待现场网页提交；源截图晚到时也会续接仍按住的手势，无需再滑动一次。已取消、已结束或排版发生变化的手势不会接入迟到的截图。

## 连续滚动模板

编辑器可选择「分页模板」或「滚动模板」。滚动类型只有一份 `scrollHtml`，每章建立一个 `data-reader-page="scroll"` 文档，正文原样放入唯一的 `data-reader-flow="body"` 区域，不运行分页器、不分栏、不产生首页与续页。正文始终使用自然高度，不为正文设置分页尺寸或多栏。默认整章随页面滚动；也可以声明独立的正文滚动容器，让页眉、页脚和两侧装饰固定。

```json
{
  "schemaVersion": 2,
  "type": "scroll",
  "id": "user.continuous",
  "name": "连续书卷",
  "scrollHtml": "<article><h1 data-reader-field=\"chapterTitle\"></h1><main data-reader-flow=\"body\"></main></article>",
  "css": "article{padding:24px} [data-reader-flow]{height:auto}",
  "javascript": ""
}
```

固定画框的模板在正文区外包一层 `data-reader-scroll-viewport`，并通过 CSS 为它分配可用高度。这个容器必须唯一、包含唯一的正文区，不能和 `data-reader-flow` 是同一个元素。运行时将页面固定在一屏内，仅让该容器滚动；其余装饰无需使用 `position: fixed`。例如：

```html
<article class="reading-frame">
  <header data-reader-field="chapterTitle"></header>
  <div data-reader-scroll-viewport><main data-reader-flow="body"></main></div>
  <footer data-reader-field="progress"></footer>
</article>
```

```css
.reading-frame { height: 100%; display: grid; grid-template-rows: 72px minmax(0, 1fr) 48px; }
[data-reader-scroll-viewport] { min-height: 0; margin: 0 16px; }
[data-reader-flow] { padding: 16px; }
```

没有该标记的滚动模板仍按自然长文档布局。阅读位置、定位、可视区域和章节边界手势都以实际正文滚动容器为准；从固定页眉或页脚上滑动不会触发跨章。正文仍保留一份完整 DOM，滚动不会重建正文。

旧版格式 1 未声明 `type` 时仍是 `paged`，首页与续页保持原样。含滚动模板的独立备份使用库格式 2；导入、复制、导出和样式关联均保留类型。编辑器切换类型时保留未使用的 HTML，避免丢失作者代码；CSS 仍需按新类型的自然高度或固定画框尺寸调整。

连续文档沿用正文位置完整性校验、选区、段评点击和章节边界导航。原生进度与定位内部仍需要位置序号，因此会在布局完成后按实际滚动容器的可视高度记录位置；该索引不会分割显示 DOM。`readerTemplate.pages` 只有一个元素，`currentPage` 始终指向它，`pageIndex`/`pageCount` 是用于导航的位置序号与数量。`beforePage`、`afterPage` 每次布局只调用一次，`afterLayout.pages` 也只有这个文档；`pageChange` 表示跨过记录的位置或执行定位，不会替换文档。滚动不使用离散页截图，也不会每帧遍历整章测量正文。

「未来口袋」的 ID 是 `builtin.doraemon_scroll`。图片以 data URI 离线内置，来源与处理记录见 `tools/reader-templates/doraemon/SOURCES.md`。页眉、铃铛、左右边框和口袋页脚固定，只有中央矩形阅读框的正文滚动；小屏与横屏收紧装饰，为正文保留空间。天色按阅读器时间分为日间、傍晚和夜间，正文保持暖白纸面。装饰保持静止，没有持续动画、JS 计时器或主题滚动监听器。

## 首页、续页与正文流

每一章重新从首页开始。HTML 可以有任意页面结构，CSS 不设属性白名单。页面中的一个或多个 `data-reader-flow="body"` 元素承接正文，按数值 `data-reader-order` 排序，相同值按 DOM 顺序。上一块正文区域放不下的内容会接到下一块区域，最后一块放不下的内容进入续页模板。

输入是未经页面排版的语义正文 HTML，保留段落、图片、图片动作和原文位置。模板路径直接构建章节，不经过普通正文的页面样式生成器。先建立模板页面及其正文区域，再加载仅装饰正文的高亮样式，等待组合后的字体和图片，最后统一测量与分页。高亮匹配可以提前准备，但高亮产生的字号、间距和装饰必须在分页前生效。分页完成后不再执行原阅读器的底部对齐或逐行位置调整。

四套内置模板通过 `data-reader-flow-pagination="columns"` 使用浏览器分栏计算断点。首页和续页分别测量，同一种正文区域尺寸复用一次布局结果，再生成各页的正文片段；页脚、脚本钩子、原文位置和完整性检查仍然保留。浏览器决定的分页位置可能与兼容引擎不同，书签与阅读进度继续按原文偏移恢复。

自定义模板可以在正文区域添加同一标记。它适合正文区域尺寸稳定、正文样式不随具体页码改变的横排或竖排模板。表格、Ruby 等复杂内容、不断变化的区域，或不能完整放入页面的分栏片段会自动使用兼容分页。未添加标记的现有模板保持原分页方式；无需修改 JSON 格式版本。正文 DOM 或字体、尺寸改变后，会重新计算断点。

```html
<article class="page">
  <header><span data-reader-field="bookName"></span></header>
  <h1 data-reader-field="chapterTitle"></h1>
  <main class="columns">
    <section data-reader-flow="body" data-reader-order="1"></section>
    <section data-reader-flow="body" data-reader-order="2"></section>
  </main>
  <footer>
    <span data-reader-field="time"></span>
    <span data-reader-field="page"></span>
    <span data-reader-field="battery"></span>
  </footer>
</article>
```

```css
.page {
  box-sizing: border-box;
  height: 100%; width: 100%;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: .6em;
  padding: 1em;
}
.columns {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 1em;
  min-height: 0;
}
[data-reader-flow] { min-width: 0; min-height: 0; }
header, footer { display: flex; justify-content: space-between; font-size: .65em; }
.reader-chapter-title { display: none; }
.reader-paragraph { text-indent: 2em; }
[data-reader-page="first"] h1 { font-size: 1.8em; }
```

每页外层标记是 `data-reader-page="first"` 或 `"other"`，`data-reader-page-index` 从 0 开始。正文区域需要可测量的宽高；Grid 布局中通常使用 `minmax(0, 1fr)` 和 `min-height: 0`，避免正文反过来把页面撑高。无需所有区域等宽等高，也无需使用双栏。可以使用 Flex、Grid、绝对定位、装饰元素以及浏览器支持的其他 CSS。

CSS 能力取决于设备的 Android System WebView。自动分页还需要元素能够分片或完整放入区域：无法拆开的超大表格、持续改变大小的脚本、没有高度的正文区域等会产生可见错误。浏览器分栏不支持的内容仍可在同一模板内使用兼容分页引擎，不会引入原阅读主题。此实现不会把浏览器未支持的排版语法当作已经支持。

「古籍竖排」将 `writing-mode: vertical-rl` 设置在正文区域，页面结构、页眉与页脚各自排版。竖排正文需要确定的宽高，段距使用 `margin-block-end`，接排段落不重复首行缩进。模板在分页前按实际区域宽度约束独立图片；图片保持正向显示，段评气泡和书源点击沿用原有 ID。阅读位置仍使用原文偏移，切换模板后会重新分页。

## 精确选择正文

正文具有稳定的语义标记，不必靠全章正则判断段落和标题。

| 选择器 | 对象 |
| --- | --- |
| `.reader-paragraph` / `[data-reader-kind="paragraph"]` | 含文字的正文段落，包括带行内图片的段落 |
| `.reader-chapter-title` / `[data-reader-kind="title"]` | 原始章名 |
| `[data-reader-kind="image"]` | 独立图片或只有图片的段落 |
| `[data-reader-block="block-12"]` | 来源正文的第 12 号块，编号不会随标题显隐而变化 |
| `img[data-legado-image-id]` | 来源图片，包括段评气泡 |

「天光漫游」在 `beforeLayout` 为语义段落添加分类与编号属性，区分首段、对话和原文中的分隔符；不改写、拆分或包裹原文，不删除图片或动作。续页不重复首行强调与段落编号。自定义模板如需改变正文结构，应在分页前完成，并保留来源 ID。

阅读位置以处理后的原文 UTF-16 偏移记录，独立图片不占正文字符。图片与来源点击动作保留各自的 ID。修改 DOM 时保留已有块与图片的 ID，便于搜索、书签和阅读进度继续对应原文；新增装饰内容可以标记 `data-reader-text-ignore`。

## 页眉、页脚与变量

任意元素都能通过 `data-reader-field` 显示一个动态字段，其位置和外观由模板 CSS 决定。

| 字段 | 内容 |
| --- | --- |
| `bookName`、`chapterTitle` | 书名、章名 |
| `time`、`battery`、`batteryPercentage` | 时间、电量 |
| `page`、`pageIndex`、`pageCount` | 本章页码/总页数、从 1 开始的页码、总页数 |
| `progress`、`chapterProgress` | 阅读进度、章节进度 |

基础样式提供 `--reader-text-color`、`--reader-page-background`、`--reader-accent`、`--reader-font-family`、`--reader-font-size`、`--reader-line-height`、`--reader-paragraph-indent`、`--reader-paragraph-spacing`，以及 `--reader-padding-*` 与 `--reader-safe-*`。除设备安全区外，这些兼容默认值与原阅读设置无关，模板 CSS 可以完整覆盖。模板是页面的唯一主题，原阅读背景图不会显示在它下方；需要的背景应写在模板内。高亮中的主题色占位符使用模板的颜色变量。

模板背景铺到屏幕边缘，标题、正文、物品栏避开设备安全区；安全区按阅读视图与系统栏的实际交叠计算，避免重复留白。时间、电量等信息更新后会检查正文区域是否改变。几何尺寸未变时只更新显示；尺寸改变才重新分页。如果页数和正文高度相互影响且无法收敛，会结束本次模板排版，避免空白页循环。

## JavaScript

`javascript` 字段每章执行一次，可以使用真实 `window`、`document`、事件、Promise、fetch 和浏览器 DOM API。HTML 中的脚本属于对应页面；复用续页 HTML 时也会执行，公共初始化代码更适合放到 `javascript` 字段。

可以修改 `source` 中的文字、删除段落、增加内容或调整顺序。分页完整性检查比较的是本轮脚本处理后的正文；替换净化规则仍在此之前处理原始正文。未改动的文字保留原文位置，脚本改写部分则按原段落的邻近位置定位，新增内容不会写回书籍原文。

全局对象 `readerTemplate` 提供正文与分页生命周期：

| 成员 | 用法 |
| --- | --- |
| `source` | 排版前的正文 DOM，可用标准 DOM 选择器访问 |
| `fields`、`viewport` | 动态阅读信息、CSS 像素表示的视口 |
| `pages`、`currentPage`、`pageIndex`、`pageCount` | 已完成的页面及当前页 |
| `motionState` | 当前页的装饰状态：`settled`、`running` 或 `paused` |
| `on(name, handler, options?)` | 注册钩子，返回取消监听函数；排版和导航钩子可以返回有限的 Promise |
| `waitUntil(promise)` | 将异步准备工作加入分页等待 |
| `requestLayout()` | DOM 或样式改变后请求重新分页 |

`fieldsChange` 可通过第三个参数 `{fields: ["time"]}` 仅订阅时间变化，事件附带 `changedFields`。无此参数时保持监听全部字段的行为。这样页码、进度变化不会重复调用时钟逻辑和全章几何检查。

页面钩子携带 `page` 与 `pageIndex`，可直接操作对应页，避免无意修改其他页面。

| 钩子 | 时机与用途 |
| --- | --- |
| `beforeLayout` | 每次重新分页前修改 `source` 中的正文 |
| `beforePage` | 每个页面承接正文前设置页面结构、正文区域与排版样式 |
| `afterPage` | 本页正文放入后补充装饰，避免再改变正文尺寸 |
| `afterLayout` | 一轮候选分页完成；用事件中的 `pages` 访问这些页面，此时还未替换已显示的页面 |
| `pageChange` | 翻页或目录、书签定位的导航通知，可返回有限的准备任务 |
| `fieldsChange` | 时间、电量等动态字段更新 |
| `motionChange` | 已提交的当前页面对象或实际动效状态改变，包括首次显示、重新分页与导航 |
| `dispose` | 文档离开时清理计时器、动画、事件与网络请求 |

`motionChange` 提供 `page`、`pageIndex`、`pageCount`、`state`、`previousPage`、`previousState` 和 `reducedMotion`。重新分页后，即使页码和状态不变，也会通知新的页面对象。`state` 是综合前后台、分页与减少动态效果后的实际状态，可取 `settled`、`running`、`paused`。

`motionChange` 与 `dispose` 都是同步开始执行的展示、清理通知，不等待返回的 Promise。动画是否播放完成不会阻塞翻页；`motionChange` 的异常仍按模板脚本错误处理。首次页面初始化和重新分页后的 DOM 绑定使用 `motionChange`，导航响应使用 `pageChange`。`dispose` 不是每页退出事件，换页时也需要释放旧页面的任务。

```js
// 精确选择正文段落，给包含引号的段落添加作者自己的样式类。
for (const paragraph of readerTemplate.source.querySelectorAll('.reader-paragraph')) {
  if (paragraph.textContent.startsWith('“')) paragraph.classList.add('dialogue');
}

readerTemplate.on('beforePage', ({ page, pageIndex }) => {
  page.dataset.parity = pageIndex % 2 ? 'even' : 'odd';
});

// 异步资源应显式登记，避免第一次显示后才改变排版。
// readerTemplate.waitUntil(fetch('https://example.com/theme.json')
//   .then(response => response.json()).then(applyTheme));
```

「天光漫游」在 `afterLayout` 初始化每页物品栏、经验条与续页左上角物品，在 `pageChange` 校准当前页；物品栏选框为零基页码模 9，翻到第 10 页回到第 1 格，左上角同步显示选中格子的物品。角色位置、头部与身体保持固定：苦力怕原地踏步，小白定点拉弓射箭，每支箭的弧度、落点与节奏随机，活动范围裁切在顶部装饰区。JS Web Animations 只改 transform 与 opacity，没有逐帧 DOM 写入、正文测量或计时器；射击完成后清理旧动画再继续。暂停保留当前时间，静止预绘制、离页与销毁会清理动画，角色站位始终与缓存画面一致。

支持截图翻页的页面使用滑动预绘制窗口，按阅读方向优先补齐紧邻页，空闲时准备前后各最多四页。当前章分页提交后，下一章在后台分页；下一章分页确认完成便向截图缓存提供页数，提前绘制开头几页，接近章末时窗口可以直接延伸进已分页的下一章。截图保持原屏幕分辨率，缓存数量按屏幕像素和设备内存限制，翻页时保留窗口重叠部分并释放过期页面。渲染器数量与截图数量分开限制；紧邻页缺失时可抢占同一已分页文档的远页绘制，正在进行的必要章节分页保持运行。节能模式沿用自身的资源设置。

开书时先给当前页截图最多 180ms 的优先窗口，再优先补齐后两页；章节预分页和离屏页面分页共用一个冷启动名额。离屏显示器、WebView 按实际任务创建。近页失败不能无限阻塞下一章，1.2 秒后放开近页优先限制，仍保留共享冷启动预算。

三套已审阅动态主题还使用应用本地的有界磁盘快照：每书最多五张，总计最多 32MiB，单帧原始像素最多 16MiB。按当前页、后两页、前两页顺序恢复，命中后进入实际翻页缓存，当前页可重新绑定到本次 WebView 的快照键。实际模板正文、完整模板、屏幕与安全区、字体、渲染版本、阅读字段及已测页数都必须一致；会话流水号不参与跨次身份。Minecraft 与诡秘主题的时间字段必须有效且匹配；诡秘主题按十五分钟归一时间刻度，分钟、电量的小幅变化不会丢弃周边快照，新刻度会刷新对应画面。明日香不读取时间、电量，相应字段变化不影响其快照。未版本化的异步资源、外部高亮素材、动态正文不参与磁盘复用，已内联的静态段评 PNG 和静态高亮仍可使用。保存前沿用硬件截图与页面状态校验；像素复制、压缩、读盘和解压在后台进行，额外写入缓冲全局最多一份。缓存损坏、过期或预算不足时正常渲染，不影响原文、段评与图片功能。

其完整 HTML、CSS、JS 与随 APK 打包版本一致时，可以预绘制相邻页；仅凭模板 ID 无法获得此资格。改过渲染代码的自定义模板仍在原 WebView 中运行。静止帧只由页码和主机时间字段决定；暂停保留姿态，settled 回到完整静态姿态，离页和销毁清理动画。

自定义模板可以用以下写法控制装饰元素；`Element.animate()` 使用标准 Web Animations API。「天光漫游」的云、火把与矿石使用 CSS 动画，并遵守 `data-reader-motion` 和减少动态效果设置：

```js
let activePage = null;
let animation = null;
function cancelAnimation() {
  if (animation) animation.cancel();
  animation = null;
}
const off = readerTemplate.on('motionChange', ({ page, state, reducedMotion }) => {
  if (page !== activePage) { cancelAnimation(); activePage = page; }
  if (state === 'settled' || reducedMotion) { cancelAnimation(); return; }
  if (state === 'paused') { if (animation) animation.pause(); return; }
  const star = page.querySelector('[data-reader-script-star]');
  if (!star || typeof star.animate !== 'function') return;
  if (animation) animation.play();
  else animation = star.animate(
    [{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }],
    { duration: 18000, iterations: Infinity }
  );
});
readerTemplate.on('dispose', () => {
  off(); cancelAnimation(); activePage = null;
});
```

对应 SVG 装饰可设置 `transform-box: fill-box; transform-origin: center`。用 Canvas 时，可在同一通知中开始或取消 `requestAnimationFrame`，并在 `settled` 状态同步画出完整静态画面。自己的事件监听、计时器与网络请求仍由作者管理，例如在 `dispose` 中移除监听、清除计时器或调用 `AbortController.abort()`。

模板处于独立的沙盒文档中，不能直接访问阅读器的 Java 对象、宿主 DOM 或书源脚本执行器。外部资源遵循浏览器的网络、CORS 和混合内容规则；这不对模板 CSS、HTML 或 JavaScript 源码进行裁剪。沙盒不是独立的 Android 进程，异常检测也不能替代真机验证。

中文正文的字体整形应与浏览器的标点间距处理保持一致。诡秘之主和明日香使用 `font-feature-settings: normal` 与 `font-kerning: auto`；在默认 `text-spacing-trim` 下强制关闭 `chws`、`vchw` 等特性，会使 Noto 宋体的句号、闭引号实际宽度与断行计算不一致。分页器在确定断点前测量并为这类小幅行边界偏差预留空间，在原正文框内重新换行；不能把第几行的横向字形越界直接当成该页已满。多栏测量与独立页面使用相同的内边距，兼容分页也先重新测量再取断点，最终仍严格检查文字和图片是否完整位于正文框内。该修正不改动原文、源位置或模板字体，恢复的旧模板副本同样适用。真实 `dp:` 段评样例保存在 `app/src/test/resources/reader/quote-comment.html`，`TextReaderDocumentTest` 导出原生 HTML 供浏览器检查。

加载会等待分页所需的图片、字体与样式资源，等待设有时限。自行发起的有限异步准备工作应通过 `waitUntil` 登记；不要登记无限动画的 `finished` 或不会结束的循环。脚本、CSS 动画、视频或 canvas 后续产生的每一帧并不等于一次新分页。需要改变正文区域或排版时，请调用 `requestLayout()`，避免在分页钩子内无条件请求下一轮重排。

每页的 `data-reader-motion` 对应装饰状态。模板预分页与原生翻页截图使用 `settled`，此状态应显示完整章名和静态装饰；当前页完成截图并在前台可见后进入 `running`。离屏、界面暂停或段评遮挡时进入 `paused`；减少动态效果开启时保持静态。首页首次播放还标记 `data-reader-entry="playing"`，离开后变为 `done`，翻回不重复入场。

连续装饰可以使用 CSS 关键帧，按上述状态选择器播放或暂停；也可以使用 `motionChange` 控制标准 JS 动画。建议使用 `transform` 等不改变正文区域尺寸的属性，并让标题在静态状态保持可见。自由编写的 CSS 和 JavaScript 仍保留浏览器能力；自己的计时器、网络请求和媒体不会自动纳入装饰状态，需要作者处理生命周期。

阅读界面暂停时，依赖 WebView 的原生加载和提交计时会暂停，返回后继续剩余时限。不能用离开界面的时长来推断一次分页是否完成。

滚动模式在模板内连续显示各页，从章节首尾继续向外滑动才切换章节。普通图片的大图弹层与书源图片点击沿用阅读设置；从气泡上开始横向滑动仍可翻页。书源图片动作还会检查当前章节、页码和一次实际的原生点击。

## 文件与兼容性

「导出文件」生成一个模板的 ZIP 文件，「独立备份」生成模板库 ZIP 文件，包含模板源码与内置模板隐藏状态。两者通过「从文件导入」读取，兼容旧 JSON 文件。导入先验证完整内容再提交；不存在的 ID 直接加入，相同内容复用，同 ID 的不同内容另存副本。普通全量备份不再打包整个模板库；单个阅读样式分享仍携带自身关联的模板，旧全量备份中的模板库仍可恢复。

ZIP 中分别使用 `readerTemplate.json` 或 `readerTemplates.json`。模板格式仍为 `schemaVersion: 1`，字段为 `id`、`name`、`description`、`firstPageHtml`、`otherPageHtml`、`css`、`javascript`，HTML/CSS/JavaScript 按原文保存。模板内容变动后，旧分页和截图缓存不会复用。

导入旧阅读样式时，若附带已退出内置列表模板的源码，会完整保留为用户副本；若只携带该内置 ID 而没有源码，则在阅读时选择可用的默认模板。未知模板 ID 缺少源码仍会报告错误，避免把丢失的用户作品当作正常导入。

兼容分页适配器固定使用 Paged.js 0.4.3 的 `Layout` 内部接口，按正文区域连续接排，不使用它默认的单正文框页面生成器。来源、MIT 许可和本地导出补丁记录在 `app/src/main/assets/epub/vendor/paged.NOTICE.md`。升级引擎需要重新验证跨区域文字完整性、图片唯一性和阅读器的提交时序。
