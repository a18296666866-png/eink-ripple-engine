# Shizuku 墨水屏专属定制版 (Modified)

本项目基于官方 **Shizuku v13.5.4** 源码进行深度定制，专为墨水屏设备（特别是掌阅、汉王、大我）打造，提供原生的水波纹翻页、全局自动化保活以及极简的 UI 体验。

## ✨ 核心特性 (Features)

*   **🌊 三端波纹自适应**：自动检测底层硬件，完美支持**掌阅(RK)**、**汉王**、**大我(Bigme)** 三家的 EPD 水波纹动画接口。
*   **🌊 瀑布翻页(纵向波纹)**：支持横向翻页与瀑布流纵向翻页的一键切换，各家底层映射均已适配。
*   **🔄 自动无线调试 (Wi-Fi Auto Start)**：连接 Wi-Fi 时，利用 Shizuku 的 Shell 特权自动开启系统的无线调试开关，并扫描端口拉起服务。
*   **🔋 静默保活机制**：服务启动后自动执行底层指令，将自身加入电池优化白名单（DeviceIdle Whitelist）并允许后台运行，彻底告别杀后台。
*   **♿ 无障碍自动配对**：利用无障碍服务截获系统的无线调试配对码，自动完成配对并跳回主页，全程无需手动输入。
*   **🎨 极简 UI 重构**：主页卡片高度与颜色完全统一，纯黑文字，支持一键连点开启“开发者选项”。

## 🚀 快速开始 (Usage)

1. 可直接下载仓库中的 [Release APK](out/apk/shizuku-v13.5.4.r1.b5a3891-release.apk)，也可以自行编译。
2. 安装并打开 App，按首页顺序完成：开发者选项、无障碍权限、无线调试、配对码配对、启动服务。
3. 进入“翻页动画”设置拂风纹/垂幕纹、速度和反向动画，保存后返回首页。
4. 在首页确认设备服务已连接，再由用户手动点击“开启监听”。监听不会随应用启动自动开启。
5. 在阅读器中使用左/右边缘点按、水平滑动或设备翻页按键测试动画。

详细的权限、事件识别和接口分层说明见：

- [接口映射逻辑表](BIGME_HANVON_IREADER_MAPPING.md)
- [事件监控与重构说明](docs/RIPPLE_ARCHITECTURE.md)

## 🛠️ 编译指南 (Build)

本项目基于 Gradle 8.0 与 Java 17 进行编译。

1. 克隆源码（包含子模块）：
   git clone --recurse-submodules https://github.com/a18296666866-png/eink-page-turn-assistant.git
   cd eink-page-turn-assistant

2. 编译 Debug 版本并运行测试：
   .\gradlew.bat :manager:testDebugUnitTest :manager:assembleDebug --no-daemon

生成的 APK 位于 `manager/build/outputs/apk/debug/` 目录下。隐藏 API stub 的 junction 处理方式见重构说明。

### 已上传构建产物

- Release APK：`out/apk/shizuku-v13.5.4.r1.b5a3891-release.apk`
- SHA-256：`12c144a70046d376bc2a54f4920fe012e6457fd299cd8e3edfec018fca5d3230`

## ⚠️ 免责声明 (Disclaimer)

1. 本项目的 EPD 波纹接口通过逆向及社区文档整理而来，由于各厂商固件差异，**不保证在所有同品牌设备上 100% 生效**。
2. 本项目仅供学习与技术交流，请勿用于商业用途。因使用本软件导致的设备异常，开发者不承担任何责任。

## 🙏 致谢 (Credits)

*   [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) - 强大的 ADB 权限管理框架。
*   所有为墨水屏开源社区做出贡献的开发者。
