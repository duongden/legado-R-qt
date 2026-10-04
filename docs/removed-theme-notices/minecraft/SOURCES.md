# Minecraft · 天光漫游

非官方 Minecraft 阅读主题。Minecraft 名称及原版贴图属于 Mojang AB / Microsoft；此主题不代表官方产品，也不将这些贴图重新声明为开源或 CC0。

## 游戏贴图

版本：Minecraft Java Edition 1.21.5。获取时间：2026-09-21。

资源镜像基址：<https://assets.mcasset.cloud/1.21.5/assets/minecraft/textures/>。

| 本地 PNG | 原版资源路径 |
| --- | --- |
| grass-side / grass-top / dirt / stone | block/grass_block_side.png / grass_block_top.png / dirt.png / stone.png |
| cobblestone / mossy-cobblestone | block/cobblestone.png / mossy_cobblestone.png |
| deepslate / diamond-ore | block/deepslate.png / deepslate_diamond_ore.png |
| oak-planks / oak-log / oak-leaves | block/oak_planks.png / oak_log.png / oak_leaves.png |
| water / torch / fire | block/water_still.png / torch.png / fire_0.png |
| diamond-pickaxe / diamond / book / enchanted-book | item/diamond_pickaxe.png / diamond.png / book.png / enchanted_book.png |
| compass / clock / map | item/compass_00.png / clock_00.png / map.png |
| apple / emerald / ender-pearl | item/apple.png / emerald.png / ender_pearl.png |
| heart | gui/sprites/hud/heart/full.png |
| hotbar / hotbar-selection | gui/sprites/hud/hotbar.png / hotbar_selection.png |
| creeper | entity/creeper/creeper.png |
| sun / moon-phases | environment/sun.png / environment/moon_phases.png |
| skeleton | entity/skeleton/skeleton.png |
| poppy / red-mushroom / short-grass / amethyst | block/poppy.png / red_mushroom.png / short_grass.png / amethyst_cluster.png |
| map-background | map/map_background.png |
| bow / bow-pulling-0 / bow-pulling-1 / bow-pulling-2 | item/bow.png / bow_pulling_0.png / bow_pulling_1.png / bow_pulling_2.png |
| arrow / target | item/arrow.png / block/target_side.png |

`grass-top-tinted`、`oak-leaves-tinted` 在灰度贴图上乘以生物群系绿，保留 alpha；`water-frame` 使用首帧，场景中叠加水蓝色。`creeper-face`、`creeper-body`、`creeper-foot` 分别取皮肤 UV (8,8)-(16,16)、(20,20)-(28,32)、(4,20)-(8,26)。所有图块采用像素插值。`fire` 保留完整 32 帧，固定窗口中的帧带位移不影响正文布局。

## 像素字体

Press Start 2P，Google Fonts：<https://github.com/google/fonts/tree/main/ofl/pressstart2p>，SIL Open Font License 1.1。许可原文保存在 `PressStart2P-OFL.txt`。

`reader-pixel.woff2` 保留 ASCII 32–126，字体名称改为 Reader Pixel。仅用于装饰标签、页码等，中文正文使用模板指定的清晰无衬线字体。完整 OFL 许可和素材署名也写入生成模板的 CSS 注释，随安装包、模板复制与导出一起保留。

## 构建与动效

运行 `python tools/reader-templates/build-minecraft.py`，将 HTML、CSS、JS、图块和字体打包为 `builtin.minecraft_live.json`。所有实际使用的资源均为 data URI，阅读、复制与导出模板不依赖网络。

首页与续页使用独立 HTML；正文只有一个连续流。原文只在分页前标记段落分类，分页后设置经验条和物品栏选框的 transform，并将续页左上角物品与选中格子同步；日夜取同一主机时间字段，保证预绘制画面一致。角色头部、身体与四肢来自原版皮肤 UV。角色位置、头部与身体固定，苦力怕轻轻原地踏步，小白定点拉弓射箭。每次射击只计算一次随机弧线，由 JS Web Animations 插值，箭矢被裁切在顶部靶场内；上一轮有限动画结束并清理后才开始下一轮，不累积效果对象。仅当前页创建动画，暂停保留姿态；settled、离页与销毁取消全部射击，恢复确定的 CSS 静态画面，角色不会因横向巡游复位而跳动。太阳按本地 6:00—18:00 移动，月亮走夜间轨迹，不是地理日出日落或天气预报。云、矿石与火焰仅在当前页 `data-reader-motion="running"` 时运行，暂停与减少动态效果设置生效。没有计时器、WebGL 或整页持续重绘。

新增素材可用 `output/reader-gilded-template-tools/Scripts/python.exe tools/reader-templates/fetch-minecraft-live-assets.py` 重建。太阳、月亮将游戏的纯黑混合键转为透明；新增头顶和侧脸取真实 UV，骷髅四肢保留两像素宽骨架。获取清单保存在 `output/reader-minecraft-live-artwork/sources.json`。

弓、三帧蓄力贴图、箭与靶面于 2026-09-22 从同版本镜像获取，保留原像素；可用 `python tools/reader-templates/fetch-minecraft-archery-assets.py` 重建，来源与 SHA256 见 `output/reader-minecraft-pose-artwork/sources.json`。
