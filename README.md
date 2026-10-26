# Endfield Menu 1.5.0

Minecraft 1.21.1 / NeoForge 21.1.251 客户端 Esc 暂停菜单。直接运行附件原 HTML/SVG，鼠标、滚轮、拖动和键盘由 ApricityUI 处理；中央真实地形、海拔等高线、玩家扫描光和实时磨砂使用原生 shader。

## 安装

1. Windows x64、Java 21、Minecraft 1.21.1、NeoForge 21.1.251、WebView2 Runtime。
2. 将 `endfield-menu-1.5.0.jar` 和原版 ApricityUI **1.2.8 或更新版本**放入实例 `mods`，移除旧版 Endfield Menu。
3. 进入世界后按 Esc；服务端无需安装。

AUI 最低版本为 1.2.8（依赖范围 `1.2.8+`）。当前构建与实测使用 1.2.8；新版仍需保持 mixin 所用 iframe 和帧协议兼容。本模组不修改 AUI 的 JAR/DLL。独立图像解码 DLL 随本模组提供，自动提取；其源码和第三方许可证均随工程交付。

## 本版变更

- 视频设置新增菜单分辨率：跟随窗口、720p、1080p、1440p、2160p。仅调整网页，保留窗口宽高比，显示实际尺寸，不自动降画质。
- 原生 SIMD JPEG 解码和可复用像素缓冲，替代默认逐帧 STB 解码；只保留最新完整帧。
- 头像四边形内保留真实绿色，加载前深灰占位；元数据移到可见图像之外，不再污染左上方画面。
- 关闭时停止捕获、清空排队帧和解码缓冲；闲置约 60 秒释放网页纹理、接收缓存、地形、磨砂目标并缩小隐藏浏览器。
- 默认保留浏览器以便快速重开；视频设置新增低内存模式，同一超时也关闭浏览器。退出世界释放会话。
- 保留原 HTML 排版、AUI 输入、900ms 展开 / 520ms 收起、视差、悬浮、列表拖动、实时 GPU 磨砂与玩家原点扫描光。

地形只采样客户端已加载区块，不加载新区域。高度场显示地表，地下也查看地表；不逐方块重建悬挑和建筑内部。等高线按绝对海拔蓝→绿→黄→棕→白平滑过渡，默认锚点 63/96/144/200/256，可配置。

左侧对应返回游戏、背包、进度、统计；右侧前四格对应设置、地图复位、视角复位、刷新地形。地图可拖动旋转、滚轮缩放；退出有确认。其余装饰槽锁定。

## 实测与限制

同机 15 分钟长测完成 **100 次开关、10 次世界往返**；另有输入、缓存重开和分辨率专项。

配对测试中，1080p / 1440p 解码段中位数从 **10.36 / 22.96ms** 降到 **6.14 / 14.07ms**。但网页绘制、编码和传输仍有瓶颈，网页实际帧率为 **64.38 / 57.16 FPS**，P95 帧间隔 **31.76 / 36.15ms**。1080p 整体帧率在这次配对中低于 1.4.2，不能宣称全面提升；**稳定 60 FPS 未达成**。

连续开关时专属浏览器 Private Bytes 峰值仍约 **1.83 GiB**；最终闲置降到约 **347 MiB**，低内存模式超时后专属浏览器退出。本菜单拥有的帧原生缓冲、网页 GPU 纹理、地形及磨砂目标可释放；保留 WebView2 时其缓存不保证回到初始占用。不能据 15 分钟测试保证长期无泄漏。

完整口径、A/B 表、内存范围、输入指标限制和失败实验见 [验证记录](docs/VALIDATION-1.5.0.md)，配置见 [设置说明](docs/SETTINGS-1.5.0.md)。

默认 JPEG 质量 95，通过 shader 近似移除背景色，与当前 GPU 磨砂世界合成。头像绿色已保护，但其他绿色素材与半透明边缘仍有误差。`losslessCapture=true` 使用真实 alpha 的 PNG，帧率更低，改后重新进入世界。双平面 alpha 原型动画误差过大，未启用；独立 DLL 只是解码器，未实现 GPU 共享纹理/零拷贝。

连续帧接口仅连接本机随机端口；给本菜单独立 WebView2 profile 临时设置连接参数，创建后恢复环境。已有远程调试参数时保留它们并沿用 AUI 捕获。通道失败自动回退 AUI。单人仍暂停，联机背景持续更新。

## 构建与验证

```powershell
.gradlew.bat build sourceBundle
.gradlew.bat runClient -PruntimeQa -PqaWidth=1920 -PqaHeight=1080
.gradlew.bat runClient -PruntimeQa -PfinalQa -PqaWidth=1920 -PqaHeight=1080
```

依赖已缓存可加 `--offline`。构建运行 **17 项单元测试**，逐字节校验原附件 `index.html` 和 20 个组件。QA 使用独立存档和 MC/GLFW 输入回调，调整开发实例设置并自动退出；QA 不打入发行 JAR。

原网页在 `reference/endfield_menu-main/`；Minecraft 适配在独立 `minecraft.js`。独立 DLL 重建见 [native-codec](tools/native-codec/README.md)。工程包含其完整固定版本源码、许可证和归档哈希。

`tools/native-export/` 保留 SVG 导出脚本、时间轴与轨迹预览作对照，运行时不读取导出平面。使用 `npm ci`、`node export.mjs`、`node serve.mjs`，浏览器访问本机 8127 端口。未通过的双平面原型在 [experiments](tools/experiments/README.md)，与发行链路隔离。

第三方代码、美术归属及许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
