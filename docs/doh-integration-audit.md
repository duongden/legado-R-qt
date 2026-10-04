# DoH 接入与网络配置审查

审查日期：2026-09-11。对象：当前 `legado-private-armv8-release` 源码，最近发布版本为 `3.26.09111945-examples`。本次是源码、调用链及上游依赖审查，没有实现 DoH、修改网络设置、构建 APK 或发布 R2；没有进行 Android 真机网络验证。

**结论：可以接入，建议使用与现有 OkHttp 5.3.2 同版本的官方 `okhttp-dnsoverhttps` 模块。需要先统一 DNS 策略和网络后端选择，单独增加 `.dns()` 和设置开关不能保证生效。** Maven Central 已确认该版本存在，依赖 `okhttp-jvm:5.3.2`，不需要为此升级现有 OkHttp。

现有“自定义 Hosts”“书源 dnsIp”“代理”各有用途；当前没有 DoH 设置或隐藏开关。Cronet 的 `UseDnsHttpsSvcb` 表示 HTTPS/SVCB 记录能力，`AsyncDNS` 表示异步解析，都不能视作已经启用 DoH。

下面的“已确认”指代码路径明确如此，不代表已在设备上复现全部组合。

| 已确认的问题 | 代码证据 | 接入时的处理 |
| --- | --- | --- |
| Cronet 成功请求绕过 OkHttp DNS | `HttpHelper.kt:107` 添加应用拦截器；`CronetInterceptor.kt:54` 直接返回 native 请求结果，异常才继续 OkHttp | DoH 模式使用 OkHttp；保留 Cronet 的原有用途，明确当前实际生效的后端 |
| 书源显式代理可能被 Cronet 绕过 | `HttpHelper.kt:170` 克隆带 Cronet 的 client，再设置 proxy；`CronetHelper.kt:76` 仅接收 Request，没有传递 client 的 proxy、认证或 DNS | 显式 HTTP/SOCKS 代理客户端不安装 Cronet 拦截器；这不等于 Cronet 不支持系统代理 |
| 源 `dnsIp` 会覆盖其他域名 | `AnalyzeUrl.kt:629` 的 DNS 回调对任何 hostname 都返回同一组地址；失败时直落 `Dns.SYSTEM` | 仅覆盖原始目标 `HttpUrl.host`，其他域名委托上一级解析器；保留字段含义，不能改成“DNS 服务器地址” |
| Cronet 一次性 IP 表键不一致且会串请求 | `AnalyzeUrl.kt:288`、`:619` 使用不含 GET 查询参数的 URL；`CronetHelper.kt:77`、`:111` 用完整 URL 读取并删除；同 URL 并发共用键 | 将指定 IP 的请求交给 OkHttp 的请求级解析包装，迁移后删除全局 `customIp` 表 |
| Cronet 用替换 URL 主机实现 Hosts/IP | `CronetHelper.kt:110` 的 `customHost` | 指定 DNS/Hosts 请求改走保留原始 URL、Host、SNI 的 OkHttp 路径，之后删除 URL 改写旁路 |
| Hosts 是否生效取决于客户端首次创建时机 | `HttpHelper.kt:101` 和 `JsWebSocketManager.kt:49` 仅在 Hosts 非空时装 DNS 回调；`AppConfig.kt:161` 保存设置只清映射缓存 | 始终安装统一解析器；显式规定设置生效时机，不能仅更新 preference 就宣称即时生效 |
| Cronet 设置不是热更新 | `AppConfig.kt:65` 的 `isCronet` 是初始化 val；`OtherConfigFragment.kt:259` 的开关没有对应重建或重启提示 | 首版采用一致的“重启后生效”约定，或完整实现配置代际和客户端重建；不能混用两种行为 |
| IP 列表解析重复且空值行为不同 | `AppConfig.kt:226` 的数组解析和 `StringExtensions.kt:155` 的字符串解析；两者均调用 `InetAddress.getByName`，并非只解析 IP 字面量 | 统一校验与解析；数组空结果不得成为有效 Hosts 项；旧配置含域名别名时应明确兼容或给出校验提示，不能默默丢弃 |
| 通用 HTTP 客户端不适合承载 DoH 查询 | `HttpHelper.kt:64`、`:66` 放宽 TLS/主机名校验，`:81` 添加 no-cache；`SSLHelper.kt:27`、`:70` 接受任意证书/主机名 | DoH 查询使用独立的正常 TLS 客户端、Dispatcher、连接池与缓存，不继承这些兼容选项及 Cronet |

上表文件均位于 `app/src/main/java/io/legado/app/` 下：`HttpHelper`、`SSLHelper` 在 `help/http/`；`CronetInterceptor`、`CronetHelper` 在 `lib/cronet/`；`AnalyzeUrl` 在 `model/analyzeRule/`；`AppConfig` 在 `help/config/`；`JsWebSocketManager` 在 `help/`；`StringExtensions` 在 `utils/`；`OtherConfigFragment` 在 `ui/config/`。

**建议的解析规则：** 对需要解析的直连域名，顺序为“有效且匹配原始域名的书源 dnsIp → 自定义 Hosts → 所选 DNS 模式”。系统模式调用系统解析；DoH 模式调用 DoH，失败后是否允许回退系统 DNS必须由明确配置决定。URL 本身是 IP 地址时不需要 DNS。无效的源覆盖值不能绕过统一策略直接调用系统 DNS。

代理需要单独说明：HTTP 代理通常负责解析目标域名，OkHttp 在本地主要解析代理主机；SOCKS 目标保持 unresolved，走 SOCKS 路径。当前 `InetSocketAddress(host, port)` 还可能提前用系统 DNS 解析代理服务器。不能把这部分宣传成“目标都经应用 DoH”。也不应为了让 DoH 生效，擅自把代理请求改成直连或把目标域名替换成 IP。

**建议的后端规则：** 开启 DoH、配置自定义 Hosts、请求包含指定 IP 或显式代理时，使用统一策略下的 OkHttp。其他请求可以继续使用 Cronet。首版在存在任意自定义 Hosts 时统一采用 OkHttp，避免初始主机未命中 Hosts、Cronet 内部重定向到命中主机后又绕过覆盖。保留用户的 Cronet 偏好，并显示当前实际生效的后端；不要静默制造两个同时开启却相互覆盖的设置。

| 网络入口 | DoH 覆盖方案与边界 |
| --- | --- |
| 搜索、书源、目录、普通正文 | `AnalyzeUrl → getProxyClient`，进入 OkHttp 后使用共享解析器；源 WebView 模式另算 |
| Glide 封面、漫画、正文映射图片 | Glide 已使用全局 client；正文映射图片由 `TextReaderImageLoader.kt:47`、`:63` 经 AnalyzeUrl 下载，再提供为本地资源。缓存命中不重新解析 |
| AI、MCP、搜索工具、生成图片及下载 | 大多使用全局 client 或 newBuilder 派生，能继承解析器；保留流式传输、下载超时等独立策略 |
| HTTP TTS 与内置 URL 音频播放器 | `HttpReadAloudService.kt:622`、`:737`、`:753` 经 AnalyzeUrl；`ExoPlayerHelper.kt:137` 使用全局 client 派生的 OkHttpDataSource，可覆盖 |
| JS WebSocket、Relay 控制与数据通道 | 使用独立 OkHttp 客户端，需要分别接共享解析器；保留无限读取超时、心跳、正常 TLS 等差异，不能整体换成通用客户端 |
| 普通书源、RSS、主题等旧在线导入 | 使用全局或派生 client，可继承；不能据此声称所有导入已有新下载器的地址防护 |
| 段落规则、气泡包在线导入 | 将解析器接到 `GuardedImportDns` 的 delegate；保留其安全边界，见下文 |
| 登录、内置浏览器、BackstageWebView | WebView 原生网络，不受全局 OkHttp DNS 控制 |
| 段评、RSS、划词搜索中的 WebView | 部分 GET 主文档会被 OkHttp 接管，只有被接管的请求可覆盖；POST、子资源、脚本请求和回退路径不能一概保证 |
| EPUB、页面模板 | 本地字体、正文、运行时资源无需 DNS；模板放行的外部 HTTP(S) 资源仍可能走 WebView。`EpubDirectWebLayer.kt:6128`、`:6181` 的资源边界应保持原义 |
| HttpCaptureHelper | WebView 请求记录阶段未改网络路径；随后使用 OkHttp 回放的请求才可继承 |
| Java URL.openConnection | `App.kt:119` 注册 ObsoleteUrlFactory 成功后继承 client.dns；兼容层清除原拦截器，包括 Cronet。注册前的启动请求仍需单独检查 |
| 系统或第三方 TTS、外部浏览器/播放器 | 由其他组件或应用自行联网，应用级 DoH 无法接管 |

**可以清理的代码与入口：** 完成等价迁移后删除 HTTP/WebSocket 两份 Hosts→SYSTEM DNS 回调、重复的 IP 列表辅助实现、`AnalyzeUrl.customIp` 全局表和 Cronet 的 `customHost` URL 改写。把 DNS 模式、服务商、Hosts 高级配置集中到一个“网络与 DNS”入口，避免重复开关。未发现现有可直接删除的 DoH 设置。

`CronetCoroutineInterceptor` 在本次生产、测试和文档检索中没有调用方，实际入口只实例化 `CronetInterceptor`；后者的私有 `getCookie` 也未被调用。这些属于独立清理候选。若删除，应核对公开脚本/反射兼容约定，并对实际构建和 DEX 引用复核；它们不是接入 DoH 的前置条件，不应扩大首版变更。

**必须保留的能力与边界：** Hosts、书源 dnsIp、HTTP/SOCKS 代理、实际 CookieManager、WebSocket 专用生命周期、Cronet 正在使用的 Loader/回调均有独立职责。`ObsoleteUrlFactory.kt:375` 清拦截器但保留 DNS，是仍在使用的兼容层，不能因名称含 Obsolete 就删除。

`OnlineImportDownloader.kt:146` 的 GuardedImportDns 不是重复 DNS 实现。必须保留全部解析地址的检查、每次下载独立连接池、NO_PROXY、实际连接地址复核、逐跳重定向检查、HTTPS 禁止降级和原有私网确认流程。不能用 DoH 或通用宽松 TLS 客户端替换这些防护。对 EPUB 也不能为了“统一网络”而放开原本被拒绝的外部资源。

`res/xml/pref_config_other.xml` 仍由 `ui/main/my/MyFragment.kt:460` 等处解析为设置搜索索引，不是废弃页面。新增或移动入口必须同时维护 Compose 页面、XML 搜索项和定位 key，不能直接删 XML。

**建议设置与迁移约定：** 默认继续跟随系统 DNS；DoH 提供预设服务商和自定义 HTTPS 地址，可在高级项配置 bootstrap IP、回退策略及 IPv6 查询。自定义 bootstrap 未提供时，解析 DoH 服务域名本身可能仍需系统 DNS，应在界面说明。局域网名称、单标签名称和私有域名要明确采用系统解析、显式 Hosts 或自定义企业 DoH，不能交给公共服务后才发现 NAS/WebDAV 不可用。

首版建议把网络设置明确标记为重启后生效，并保存为一个经过验证的完整配置，避免几个字段逐个更新产生中间状态。HTTP、Glide、WebSocket 和代理客户端目前包含 lazy 实例、独立引用和连接池；仅替换 resolver 并不能让所有已有连接立即重新解析。若选择热更新，必须同时设计配置代际、代理客户端缓存、连接池、WebSocket 重连和在途请求处理，并补对应测试。

保留旧 `customHosts` 和大小写敏感的 `"Cronet"` key。`Backup.kt:232`、`Restore.kt:299` 会备份和合并普通偏好；旧备份缺少 DoH key 时，当前合并行为会保留设备已有值，不能假定自动恢复为系统 DNS。建议旧备份不改变当前 DNS 模式，新备份在校验完整配置后一起应用，并提示按生效约定重启。

**DoH 客户端实现约束：** 使用独立正常证书/主机名验证，HTTPS endpoint 保持原域名，通过 bootstrap 提供连接地址；不要改 URL 成 IP。查询客户端不使用自己的 DoH resolver，也不经过业务拦截器、业务 Cookie 或 Cronet。使用独立 Dispatcher，避免业务请求阻塞等待 DNS，而 DNS 子请求又排在同一队列中。设置有限总超时，使用 HTTP 缓存语义和有界缓存，不把现有 addressCache 当成带 TTL 的 DNS 缓存。并发重复查询、双栈、超时/失败回退和网络切换必须实测；不能预先保证换成 DoH 就更快。系统 DNS 也可能受 Android 私人 DNS/VPN 管理，应用 DoH 只能控制实际进入这条路径的解析。

建议实施顺序和验收条件如下：

1. 建立可独立测试的 DNS 配置、统一 resolver 和网络后端选择策略；明确系统模式、DoH、局域网和代理边界。
2. 合并 Hosts/IP 解析，修复源 dnsIp 的域名作用域；代理和自定义 DNS 请求避开 Cronet。通过等价用例后删除全局 IP 表及 URL 改写。
3. 接入同版本官方 DoH 模块和独立查询客户端，补 bootstrap、证书校验、超时、缓存及明确的失败策略。
4. 把解析器接入 HTTP、Glide 派生链、JS WebSocket、Relay，以及导入防护的 delegate；保留各自非 DNS 策略。
5. 使用现有设置组件增加统一入口，同步搜索索引、备份恢复和生效提示；不增加相互覆盖的独立开关。
6. 自动验证 A/AAAA、无答案、网络超时、错误证书、bootstrap 无递归、并发队列不饥饿、缓存失效、Hosts 初始为空后新增、无效及多地址 Hosts、跨域重定向、带查询参数和同 URL 并发源 IP、HTTP/SOCKS 代理不被绕过。验证导入返回公网/内网混合地址、重绑定和逐跳重定向时的防护保留。
7. 在 Android 上检查 Wi-Fi/移动网络/VPN/私人 DNS、IPv4/IPv6、系统与 DoH 模式切换、书源搜索正文图片、TTS/WebSocket、目录更新和设置恢复；单独验证 WebView 路径边界。比较冷启动与缓存命中的解析耗时、请求量和失败率，再决定默认超时、缓存大小及是否提供兼容回退。

现有 `receiver/NetworkChangedListener.kt:21` 在 API 21–23 分支创建 receiver 后仍返回 null；如果实现时要复用此监听器清理 DNS 状态，需要先修正并测试该分支。应用 minSdk 为 21，不能只验证新系统。

上游依据（本次已读取版本源码及依赖元数据）：

- [OkHttp 5.3.2 DoH 模块说明](https://github.com/square/okhttp/blob/parent-5.3.2/okhttp-dnsoverhttps/README.md)
- [DnsOverHttps 实现](https://github.com/square/okhttp/blob/parent-5.3.2/okhttp-dnsoverhttps/src/main/kotlin/okhttp3/dnsoverhttps/DnsOverHttps.kt)：A/AAAA 查询、HTTP 缓存接入、bootstrap 和默认私有名称限制。
- [BootstrapDns 实现](https://github.com/square/okhttp/blob/parent-5.3.2/okhttp-dnsoverhttps/src/main/kotlin/okhttp3/dnsoverhttps/BootstrapDns.kt)：指定服务主机的启动地址。
- [RouteSelector 实现](https://github.com/square/okhttp/blob/parent-5.3.2/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RouteSelector.kt)：HTTP/SOCKS/直连解析路径。
- [Maven Central 5.3.2 POM](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-dnsoverhttps/5.3.2/okhttp-dnsoverhttps-5.3.2.pom)

这份审查给出接入可行性、确定的源码缺陷和可清理范围。代码修复、DoH 服务实连、性能结论及 Android 验收仍是后续实施工作，不能用本次静态审查替代。
