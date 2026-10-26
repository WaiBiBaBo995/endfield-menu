# 1.3.0 原生渲染验证

环境：Minecraft 1.21.1、NeoForge 21.1.251、ApricityUI 1.2.8、Java 21、Windows 11、RTX 4060 Laptop，1920×1080。

## 自动检查

- `gradlew build sourceBundle --offline`：通过。
- `verifyOriginalPage`：通过；附件 `index.html` 和 20 个组件脚本逐字节未修改。
- 地形高度场与等高线 JUnit：通过。
- 原生 QA：通过真实 GLFW 鼠标回调和键盘 Esc，检查设置、进度、统计、背包、地图拖动／滚轮、复位、退出确认、窗口尺寸和 GUI 缩放。
- 连续三次打开／收起：通过；最后一轮收起动画测得 521.3ms。

## 性能记录

复用菜单的 468 个帧样本平均 117.5 FPS，P95 9.8ms，P99 11.5ms；连续动画记录 P95 12.2ms。测试使用游戏主循环 120 FPS 上限，结果是完整原生菜单渲染记录，不是浏览器 rAF 计数。首次打开的首次绘制约 15–20ms，纹理随后常驻。

## 交付物

- `build/libs/endfield-menu-1.3.0.jar`
- `build/libs/endfield-menu-1.3.0-sources.jar`
- `build/distributions/endfield-menu-1.3.0-project.zip`
- `tools/native-export/preview.html`、`preview.mjs`、`serve.mjs` 和 `extract.js`

截图和轨迹预览在本地 QA 运行目录生成；导出预览页还会显示每个独立动画层的命中区域和轨迹。
