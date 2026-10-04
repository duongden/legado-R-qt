# 星穹 · 天文馆

星图、星盘、行星、流星与书页装饰为本项目绘制的 SVG、CSS 和 JavaScript。星座名称仅用作装饰性的章节航程标记，不代表实时天象。全部资源随模板内置，阅读时不联网。

标题字体为 Natanael Gama 的 Cinzel，来自 Google Fonts，使用 SIL Open Font License 1.1。原文授权保存在 `assets/Cinzel-OFL.txt`，下载地址和文件 SHA-256 保存在 `sources.json`。

静止帧只取决于正文、模板、页码与阅读字段。动画只对当前页装饰的 transform、opacity 生效；暂停保留进度，静止截图和销毁取消动画。正文不使用动态纹理、缩放或逐帧测量。
