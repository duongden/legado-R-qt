# 明日香 · 赤色同步

非官方《新世纪福音战士》阅读主题。人物来自原作画面；主题的页面结构、仪表、图形和动态由 HTML、CSS 与 JavaScript 构建。

- 首页人物：TV 版片头 `OP C057`，EvaGeeks 保存的原始画面，https://wiki.evageeks.org/File:OP_C057_asuka.jpg 。
- 续页背景：《福音战士新剧场版：破》二号机登场画面，https://wiki.evageeks.org/File:2.22_Asuka-Nigouki.png 。
- 角色及动画画面版权归原权利人 GAINAX / khara 等所有。未宣称主题获得官方认可。
- 拉丁标题字体：Barlow Condensed Bold，Jeremy Tribby，SIL Open Font License 1.1；完整许可在 `assets/BarlowCondensed-OFL.txt`，导出模板也携带许可。

获取于 2026-09-22。原始 URL、文件大小和 SHA256 保存在 `sources.json`；压缩后的 WebP 尺寸及哈希保存在 `artwork.json`。只调整尺寸与压缩格式，不改画面中的人物。

重建资源：`python tools/reader-templates/fetch-asuka-assets.py`，再在安装 Pillow 的 Python 环境执行 `tools/reader-templates/prepare-asuka-artwork.py`。打包：`python tools/reader-templates/build-asuka.py`。

所有实际引用的图片与拉丁字体均离线内置。正文使用模板指定的系统宋体族；原阅读器的字体设置不参与。动效只有当前页运行，只改变 transform / opacity；暂停保留时间，静止快照与销毁会清理动画。模板不订阅时钟、电量，也不以随机数改变页面像素，便于严格复用已验证的快照。
