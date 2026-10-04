# 普通正文 EPUB 渲染：图片兼容补齐

> 2026-09-08 故障更正：`3.26.09081602` 发布后收到 `NoClassDefFoundError`、无法开书的反馈。已确认其图片处理类包含 Android ICU 拒绝的正则，首次初始化失败后可导致后续书籍重复报错。下方该版本的 JVM、桌面浏览器与上传记录仅为历史验证记录，不能作为 Android 开书成功的证明。修复与重新交付结果见本页末尾的 ICU 初始化故障修复记录。

## 范围

普通正文使用 EPUB Direct 排版时，保留书源原有图片与 `click`，继续跳过段落规则与 `pclick`。真实 EPUB 不获得书源脚本执行入口；原生正文功能保持独立。

## 项目迁移

- 项目：`D:\projects\legado-private-armv8-release`。
- Android SDK、JDK 和 Gradle：`D:\build-tools\codex-android-build`。
- C 盘旧目录均为兼容 junction；原有 Gradle 缓存仍位于 D 盘。
- 项目 113,997 个文件、10,346,373,788 字节；工具 13,779 个普通文件、1,851,803,364 字节。
- 复制和切换后各执行一次完整 SHA-256 校验；项目 HEAD、Git index 和全部未跟踪文件状态一致。核验后才删除 C 盘备份，历史 APK、堆转储和审计材料均保留。
- 迁移后 C 盘可用空间为 14,227,169,280 字节，约 13.25 GiB；迁移前约 1.61 GiB。
- 迁移证据：`D:\codex-legado-migration-20260908`。

## 图片与交互

1. 先读取图片属性，再交给 Jsoup 处理正文，兼容 `src` 内未转义的 JSON 引号。
2. 将原始 `src`、图片请求参数和动作信息分开保存。data/SVG 图片交给浏览器前移除选项后缀；请求头、请求体和加载时的 `js` 保留在原有图片加载链路中。
3. `style=TEXT` 保持行内排版。宽高仅接受有限的数值单位，避免任意 CSS 改变整页布局。
4. HTML 只携带图片 ID，不包含书源点击脚本或 HTML 事件属性；原始 `src` 作为脚本的 `result`。
5. 原生层校验可见 WebView、会话、书籍、书源、原始章节 URL、generation、页码和排版 revision。预加载、翻页交接、选字期间和过时事件不能执行动作。
6. 动作序号、防抖和执行中保护避免重复运行。图片动作锚点由原生 hit test 先消费点击，避免桥接异步回调与翻页竞争。
7. 保留预览、兼容模式、关闭和双击选项；长按仅预览，移动超过阈值取消长按；`pclick` 与段落规则命名空间持续禁用。
8. 原生动作中的原始地址、脚本和 ID 计入章节缓存的字符预算；动作防重状态使用弱引用，避免继续持有已经离开的章节。

## 目录和进度

图片不生成虚拟章节，不改动原目录序号或 URL。行内图片不写入朗读文本和字符偏移；同一份规范正文用于显示标记与朗读。回归覆盖 Unicode、段落换行、图片前后文字、翻页、字体变化和字符锚点恢复。

## 验证与交付

已通过浏览器检查：横向与纵向两种图片交互模式、6 种生产构建器生成的正文样例、6 组现有 EPUB 回归。覆盖单击／双击、禁用、预览、长按／移动取消、代际切换、SVG 解码、段落动作隔离、Unicode 长段落和字体改变后的字符位置恢复。所有检查无页面脚本错误。

浏览器报告与生产输入的 SHA-256 已绑定在 `output/text-image-browser-verification.json`。最终 APK 校验同时核对这些输入、浏览器报告及运行时资源，防止发布与已验证代码不一致的安装包。

最终 JVM 回归 508 项：506 项通过，2 项缺少本地样例而跳过，失败与错误均为 0。覆盖图片动作的缓存预算、重放防护、过期页面、段落命名空间隔离和旧式图片选项。

完整 Lint 已通过：0 项新增错误，142 项已审计的历史错误由交付基线记录，1,521 项警告。最终 Gradle 构建成功，用时 48 分 16 秒；AGP 完成随 Release 打包附带的关键错误分析。

交付版本为 `3.26.09081010`（`29813890`），包名 `io.legado.app.Archive`，仅 `arm64-v8a`，min SDK 21 / target SDK 36，未启用混淆。沿用此前签名证书，APK 完整性、包信息、签名、ABI、功能类和运行时资源均通过检查。

- APK：`output/Legado-3.26.09081010-29813890-arm64-v8a-release.apk`，40,585,362 字节。
- APK SHA-256：`a672e3ae927b595e82419302f14fdba10b3feda385ad78ec248d370b9af65560`。
- 签名证书 SHA-256：`d6441f18d3413a81c0e82e51f550c558c5d79213e921b02dec30bd3473d4f2b6`。
- R2：`legado-arm64-v8a.apk` 和 `latest.json` 已更新；元数据时间 `2026-09-08T03:24:07Z`。完整回读远端 APK 后，字节数和 SHA-256 与本地一致。
- 证据：`output/text-image-jvm-results.json`、`output/text-image-browser-verification.json`、`output/text-image-apk-verification.json`、`output/text-image-r2-verification.json`、`output/text-image-release-build.log`。

Lint 沿用此前逐项审计的 142 个历史错误，并在迁移后重新核对其 HEAD 上下文；本次错误不能加入这份历史基线。历史报告保持不变。

浏览器检查使用桌面 Edge。没有 Android 真机连接，不将这些检查解释为手机 WebView 或设备性能实测。

## 问题反馈后的加载修复（2026-09-08）

针对 TEXT 图片或气泡只留下空行、打开含图片的书后其他书籍正文也可能无法加载的问题，本轮补齐图片的原生加载路径，并将图片工作从 WebView 的共享资源拦截线程移出。拦截只查询状态或读取已完成的本地图片；下载、书源加载脚本和解密在各阅读会话自己的任务中执行。图片失败显示占位，不阻止正文就绪；退出书籍立即取消旧任务，重开同一本书也使用新的资源域名。

data/SVG 占位图片的加载 `js` 和请求参数保留，另行处理 `dp:`、`bubble://paragraph`、file/content、AI 图片及 MOBI 容器资源。缓存图片按实际字节识别 MIME，避免 `.jpg` 文件中的 SVG 被错误识别。TEXT 图片保持行内布局，气泡文字插入 SVG 前转义 XML 特殊字符。

原始 `click` 及其 `result` 继续保留，段落规则和 `pclick` 继续禁用；真实 EPUB 不获得书源脚本入口。仅在用户明确打开“强制使用软件段评气泡”时，符合条件的原始图片才转换为当前气泡包。这只是图片显示策略，不恢复段落处理。气泡包、缩放、昼夜主题、字体和开关变化参与缓存标识。

图片使用登记后的短路径，完整元数据由可见／预加载章节强持有，会话登记器使用弱引用。原生最多并行 3 个图片任务，页面最多并行 4 个图片请求；排队不占用单张图片的原生加载超时。就绪到 GET 之间有短时缓存保护，重取最多一次且保留占位图。网络图片完整写入临时文件后再替换缓存，较大的生成图片落盘，避免长期占用内存。

无书源的本地正文刷新也会关闭旧会话、清理失败图片状态并保留字符位置。目录继续使用原章节序号和 URL，图片不写入朗读正文或字符偏移。最终所有权与关闭链路审查记录见 `output/text-image-fix-final-review.md`。

最终源码的正式 JVM 回归为 564 项：562 项通过，2 项因缺少本地样例跳过，失败和错误均为 0。桌面浏览器通过 15 项异步图片专项、2 种图片手势模式、6 种正文样例和 6 组原有 EPUB 回归，包括晚到大图、失败占位、取图重试、排队预算、连续切换及字符位置恢复。测试生成样例与浏览器证明所列的 51 个文件哈希全部一致。

浏览器原生响应和书源执行使用夹具模拟。JVM 的 Windows 文件替换测试明确覆盖旧文件被占用时的拒绝、旧内容保留和关闭旧流后的成功重试，没有跳过该分支；它不代替 Android/Linux 的设备验证。本轮最终交付结果如下，前述旧版本交付记录保留。


## 本轮验证交付：3.26.09081602

完整 Gradle 测试、Lint 与签名构建成功，用时 43 分 2 秒。最终 JVM 共 564 项，失败／错误均为 0，跳过 2 项本地样例探针。完整 Lint 为 0 项新增错误，保留原先逐项审计的 142 项历史错误基线，另有 1523 项警告。历史问题未计为已修复，也没有把本轮新问题加入基线。新增的 2 项警告为 `UseKtx` 写法建议，不影响 URI 解析行为，审查记录见 `output/text-image-fix-lint-warning-review.json`。

| 交付项 | 已验证结果 |
| --- | --- |
| 版本 | `3.26.09081602`（`29814242`） |
| 应用 | `io.legado.app.Archive`，非调试包，未启用 R8 混淆 |
| ABI / SDK | 仅 `arm64-v8a`，min SDK 21 / target SDK 36 |
| APK 大小 | 40,611,548 字节 |
| APK SHA-256 | `7daefdf47c03c530151d9b926f3cdf65e175bea8140dec12da7e47325ff62a19` |
| 签名证书 SHA-256 | `d6441f18d3413a81c0e82e51f550c558c5d79213e921b02dec30bd3473d4f2b6` |
| R2 对象 | `legado-arm64-v8a.apk`、`latest.json` |
| R2 元数据时间 | `2026-09-08T10:06:43Z` |

安装包位于 `output/Legado-3.26.09081602-29814242-arm64-v8a-release.apk`。APK ZIP 完整性、包名、版本、SDK、签名、ABI、功能类及运行时内容均通过核验。构建开始时记录 2,610 个输入文件哈希，构建结束与发布前再次核对；浏览器证明与最终包内 runtime 一致。R2 上传后经过完整认证回读，远端 APK 大小和 SHA-256 与本地完全一致。

主要证据：`output/text-image-fix-release-build.log`、`output/text-image-fix-build-inputs.json`、`output/text-image-fix-jvm-results.json`、`output/text-image-fix-browser-verification.json`、`output/text-image-fix-apk-verification.json`、`output/text-image-fix-r2-verification.json` 和 `output/text-image-fix-final-review.md`。正式源码冻结前因补齐本地刷新字符位置而中止的早期构建单独保存在 `output/text-image-fix-interrupted-refresh-build.log`，不作为成功验证证据。

没有连接 Android 真机。桌面浏览器、模拟原生响应及 JVM 测试不代表实际书源服务、设备 WebView、Android SVG 栅格化或低内存场景已通过。设置和更新说明继续提示首次排版、长章节与较多图片的开销，以及可切回原生渲染。


## ICU 初始化故障修复交付：3.26.09082019

`3.26.09081602` 的发布 DEX 中，`TextReaderImageSource` 在静态初始化时直接编译含未转义闭括号的表达式。Windows 原生 ICU 对旧源码与旧 APK 提取值均返回 `U_REGEX_RULE_SYNTAX`（66305），位置 43；桌面 JDK 接受旧模式，因此此前 JVM 测试没有覆盖到这个差异。含图片或书源气泡的章节会触发该类初始化，之后可能持续报 `NoClassDefFoundError`。本次只修正两个字面量闭括号的转义，并添加说明；其余生产代码与上一版构建输入一致。

新增 `tools/check_text_reader_icu.py`，从生产源码提取实际模式，以原生 ICU 执行 24 项匹配回归及旧模式失败负控。`tools/check_text_reader_apk_linkage.py` 从最终 APK 的实际 `<clinit>` 指令跟踪字符串、Regex 构造与字段赋值，核对模式及选项与源码一致，再将包内值送入同一 ICU 检查。新版包内模式已通过全部用例。

完整 Gradle 测试、Lint 与签名构建成功，用时 `41m 50s`。JVM 共 564 项，562 项通过，2 项既有 Currency 样例探针跳过，失败和错误均为 0。完整 Lint 为 0 项新增错误，沿用原有 142 项已审计历史错误基线，另有 1523 项警告。2,613 项构建输入（包括本地 SDK 配置与 ICU gate）在构建前后及上传前核对一致。

| 交付项 | 验证结果 |
| --- | --- |
| 版本 | `3.26.09082019`（`29814499`） |
| 应用 | `io.legado.app.Archive`，非调试包，未启用 R8 |
| ABI / SDK | 仅 `arm64-v8a`，min 21 / target 36 |
| APK 大小 | 40,611,561 字节 |
| APK SHA-256 | `ce17463e73328b0d70ad387acc2630022102c03359936e491ef2be6f43ac30ad` |
| 签名证书 SHA-256 | `d6441f18d3413a81c0e82e51f550c558c5d79213e921b02dec30bd3473d4f2b6` |
| R2 | `legado-arm64-v8a.apk`、`latest.json` |
| R2 更新时间 | `2026-09-08T13:23:52Z` |

安装包为 `output/Legado-3.26.09082019-29814499-arm64-v8a-release.apk`。签名与此前版本一致；包信息、ABI、ZIP、DEX 类定义及模式检查通过。上传后完整认证回读 R2 APK，字节数和 SHA-256 与本地一致。无需清除书籍数据；覆盖安装后重开应用。

浏览器未在本轮重跑：原有 15 项异步图片、2 种图片手势模式、6 种正文样例及 6 组 EPUB 回归仅在全部输入和报告哈希仍一致后复用。原生源执行仍属模拟，不能充当此故障的运行时验证。没有可用 Android 真机或 ART 环境；Windows ICU 能复现本次语法差异，但不代表所有 Android / WebView 和真实书源已经实测。用户原始报错缺少完整类名与 cause，无图正文和真实 EPUB 不调用该图片类，故不能据此保证所有开书故障只由此处引起。

此前错误版本的 APK、构建日志、JVM 报告和原始 ICU 失败证据保持保留。主要新证据为 `output/text-linkage-icu-cli-self-check.json`、`output/text-linkage-build-inputs.json`、`output/text-linkage-release-build.log`、`output/text-linkage-jvm-results.json`、`output/text-linkage-apk-linkage.json`、`output/text-linkage-apk-icu.json`、`output/text-linkage-apk-verification.json`、`output/text-linkage-r2-verification.json` 和 `output/text-linkage-final-review.md`。


## 软件气泡尺寸修复交付：3.26.09090108

修复普通正文使用 EPUB 排版时软件气泡偏小的问题。此前整张内置 64×64 气泡图被缩为一个字高，图中数字只占画布的 15/64。现在软件气泡以 `1.5556em` 定宽，并应用气泡包的 0.5–1.5 倍缩放。这个基准参考原生旧 Android 段尾气泡的一个汉字宽 × 1.5556；不同 Android、字体和原生图片模式的尺寸并非完全相同。

气泡标志来自实际的软件渲染结果，并随资源落盘、状态查询保留。软件气泡替换原图时同时替换原标签宽高，修复固定 px 尺寸导致气泡包缩放无效的问题；重试返回普通图或默认倍率时清除旧缩放。普通原始 TEXT/text 图片继续使用原有尺寸规则。自定义模板保留图形比例，高度限制在同一气泡框内，避免纵向模板把一行撑成大块空白。此次变更不增加图片下载、书源脚本执行或新的渲染轮询。

正文字符、目录锚点、原始 click、段落规则和 pclick 隔离及会话取消代码保持现状。此前导致图片类初始化失败的正则转义修复继续保留；最终 APK 中提取出的实际正则再次通过 Windows 原生 ICU 的 24 项匹配检查及旧模式负控。

桌面 Edge 重新执行了 36 项气泡尺寸场景，以及 15 项异步图片、2 种图片交互模式、6 种正文样例和 6 组 EPUB 回归。报告与当前生产输入、运行时及测试产物的 SHA-256 绑定；原生图片服务和书源执行使用模拟，不能代替 Android WebView 或真机测试。

Gradle 相关测试、全量 Lint 和签名构建成功，用时 `41m 52s`。JVM 共 565 项，563 项通过，2 项既有 Currency 样例探针跳过；无失败或错误。Lint 无新增错误，沿用 142 项已审计历史错误基线，另有 1523 项历史警告，本轮未新增警告。2,616 项清单内输入在构建前后及上传前核对一致。独立审查另补查了清单外的 9 项 Gradle 配置和 Cronet 依赖，二进制与 Git HEAD 字节一致，文本除 CRLF 外一致，且在包检查与上传前重新核对其 SHA-256。这份补充证据是构建期间的审计，不作为事前冻结，也不将清单称为所有构建依赖的完整枚举。

| 交付项 | 验证结果 |
| --- | --- |
| 版本 | `3.26.09090108`（`29814788`） |
| 应用 / ABI | `io.legado.app.Archive`，仅 `arm64-v8a` |
| APK 大小 | 40,611,825 字节 |
| APK SHA-256 | `8bfd883dfc6d1cd6bd08f64ed2d1de17b9a21d3069ba180992161fea925f4343` |
| 签名证书 SHA-256 | `d6441f18d3413a81c0e82e51f550c558c5d79213e921b02dec30bd3473d4f2b6` |
| R2 对象 | `legado-arm64-v8a.apk`、`latest.json` |
| R2 更新时间 | `2026-09-08T18:23:10Z` |

安装包为 `output/Legado-3.26.09090108-29814788-arm64-v8a-release.apk`。签名与此前版本一致，可覆盖安装，无需清除书籍数据。R2 上传后完整回读 APK，字节数和 SHA-256 与本地一致。

没有可用 Android 真机、模拟器或 ART，未声称完成设备验证；默认 PNG 的原有 64px 取样策略未在本轮改变。旧发布证据与修改前快照保留。主要证据为 `output/text-bubble-size-source-audit-20260909.md`、`output/text-bubble-size-browser-verification.json`、`output/text-bubble-size-build-inputs.json`、`output/text-bubble-size-jvm-results.json`、`output/text-bubble-size-apk-verification.json`、`output/text-bubble-size-r2-verification.json`。
