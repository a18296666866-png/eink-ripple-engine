# E-Ink Ripple Engine · 墨水屏波纹引擎

面向 Android 墨水屏设备的翻页动画实验工程，研究掌阅（iReader）、汉王（Hanvon）、大我（Bigme）三套厂商接口，并实现触摸点击、水平滑动、实体按键、屏幕旋转的统一方向映射。

## 统一预期效果

| 动画 | 上一页 | 下一页 |
|---|---|---|
| 拂风纹 | 左 → 右 | 右 → 左 |
| 垂幕纹 | 下 → 上 | 上 → 下 |

## 源码来源与状态

工程继承自 [eink-page-turn-assistant](https://github.com/a18296666866-png/eink-page-turn-assistant)，其 Android 架构基于 [Shizuku](https://github.com/RikkaApps/Shizuku)。原始上游代码采用 Apache License 2.0；迁移时需保留许可证和版权声明。

**注意：** 厂商私有接口的效果随设备/系统固件而异。映射表的静态测试并不等同于实机验证；未经三品牌实测和 Android Debug 编译验收，不应标记为正式稳定版。

## 工程结构

- `manager/`：Android UI、用户服务、输入事件、厂商适配
- `server/`、`starter/`、`shell/`、`common/`：Shizuku 底层组件
- `api/`：Shizuku API
- `docs/`：映射说明、架构与测试资料
- `.github/workflows/`：CI

## 测试建议

1. 编译：`./gradlew :manager:testDebugUnitTest :manager:assembleDebug --no-daemon`
2. 在真实设备上分别验证：0°/90°/180°/270° × 拂风纹/垂幕纹 × 点按/滑动/按键
3. 日志中将「接口码已提交」与「肉眼确认波纹方向正确」分别记录

**状态：** 源码迁移进行中。请以仓库 Git 提交和 CI 结果为实际依据。
