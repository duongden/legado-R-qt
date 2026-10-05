# Compose UI Reference

Technical reference for the shared settings and dialog components.

## 设计规范速查

### 设置页 (AppSettingPalette 驱动)
- 背景色: palette.page
- 卡片色: palette.row (经 surfaceColor 处理)
- 卡片圆角: panelRadius (10dp × uiCornerScale)
- 卡片边距: 12dp horizontal
- 分组间距: 10dp vertical
- 分区标题: 14sp, accent, Medium, 16dp padding
- 行标题: 16sp, primaryText
- 行副标题: 14sp, secondaryText, max 2 行
- 行最小高度: 60dp, 内边距 16dp H / 10dp V
- 分割线: 16dp 两侧内缩

### 弹窗 (AppDialogStyle 驱动)
- 宽度: 92%~98%, maxWidth 560~760dp
- 框架: AppDialogFrame → LegadoMiuixCard, 阴影 14dp
- 标题: 20sp, SemiBold, titleFontFamily
- 消息: 14sp, secondaryText
- 内容: LazyColumn, maxHeight 520dp, 18dp horizontal padding
- 操作按钮: 右对齐, LegadoMiuixActionButton
- 弹出动画: 160ms tween, scale 0.96→1.0

### 可复用组件
- 基础: LegadoMiuixCard, LegadoMiuixSwitch, LegadoMiuixSlider, AppThemedStepperSlider, LegadoMiuixActionButton, LegadoMiuixSection, LegadoMiuixActionRow, LegadoMiuixChoiceRow, LegadoMiuixSelectField
- 弹窗: AppDialogFrame, AppDialogSliderRow/Grid, AppDialogSwitchRow, AppDialogOptionGroup
- Modifier: appSettingPanelBackground, appSettingRowDecoration
- 快捷函数: showComposeConfirmDialog, showComposeTextInputDialog, showComposeTextFormDialog, showComposeNumberPickerDialog, showComposeMultiChoiceDialog, showComposeSingleChoiceDialog, showComposeActionListDialog, showComposeChoiceListDialog
- 设置页框架: ComposeSettingFragment, SettingSpecScreen, SettingPageSpec/SettingSectionSpec/SettingItemSpec
