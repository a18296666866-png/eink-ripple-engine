# 墨水屏翻页助手：重构说明

本文档说明翻页助手的权限流程、事件监控流程、接口调用边界，以及如何继续扩展设备映射。

## 1. 设计目标

本次重构只保留一套方向决策链：

```text
权限与服务准备
    ↓
输入设备能力发现
    ↓
原始触摸/按键事件归一化
    ↓
上一页/下一页语义判定
    ↓
设备 + 动画 + 旋转 + 来源查表
    ↓
厂商接口下发
```

触摸区域、滑动方向、物理按键和厂商动画接口不得在不同类中重复解释方向。

## 2. 代码分层

| 层级 | 主要文件 | 职责 |
|---|---|---|
| 初始化与权限 | `SetupActivity.kt` | 展示开发者选项、无障碍、无线调试、配对、服务和动画配置状态；持有唯一 Shizuku 用户服务连接 |
| 动画设置 | `ripple/RippleActivity.kt` | 修改拂风纹/垂幕纹、速度和反向动画配置；不重复绑定监听服务 |
| 服务协议 | `ripple/ITouchService.aidl`、`ripple/ITouchCallback.aidl` | UI 与 Shizuku 用户服务之间的最小 IPC 接口 |
| 事件服务 | `ripple/TouchService.kt` | 发现输入节点、读取 getevent、判定触摸/按键、过滤目标应用、提交动画请求 |
| 唯一映射 | `ripple/RippleMapping.kt` | 三种设备的旋转、输入来源、动画类型和接口码映射 |
| 按键语义 | `ripple/PageKeyMapping.kt` | 将设备发现到的物理按键转换为上一页/下一页 |
| 设备适配 | `ripple/HanvonRipple.kt`、`ripple/BigmeRipple.kt`、`ripple/EpdcHelper` | 只负责调用厂商接口，不自行计算方向 |
| 触控坐标 | `ripple/TouchCoordinateMapper.kt` | 根据旋转把原始坐标转换为逻辑屏幕坐标 |

启动入口是 `MainActivity`，它继承初始化页，确保应用打开后先完成权限和服务准备。

## 3. 权限与初始化流程

初始化页按以下顺序工作：

1. 打开开发者选项。
2. 为本软件开启无障碍权限。
3. 打开无线调试。
4. 使用配对码完成配对。
5. 启动 Shizuku 无线调试服务。
6. 设置动画并保存配置。
7. 用户手动点击“开启监听”。

Android 普通应用无法静默授予无障碍和无线调试权限，因此软件只能打开系统设置页并显示实时状态。无障碍服务用于读取系统配对页中的配对码，配对完成后由 Shizuku 提供受控的高权限用户服务。

初始化页是唯一的服务连接持有者。动画设置页只写入 `ripple_prefs`，不会创建第二个 `getevent` 读取进程，也不会在页面切换时停止用户主动开启的监听。

## 4. 事件监控流程

### 4.1 输入节点发现

监听启动后，`TouchService` 执行 `getevent -lp`：

- 选择包含 `ABS_MT_POSITION_X/Y` 的真实触摸节点；
- 优先选择名称包含 `fts`、`touch` 或 `pen` 的节点；
- 过滤同时暴露触摸和按键能力的复合节点，避免把遥控器坐标当成屏幕触摸；
- 独立收集物理按键节点；
- 屏幕旋转变化后重新读取按键能力和旋转参数。

事件读取使用单独线程，接口调用使用串行 `ripple-dispatch` 线程，避免厂商墨水屏接口阻塞 `getevent` 消费。

### 4.2 触摸语义

所有触摸先转换为逻辑坐标，再按照统一规则判定：

- 左侧 0%～35% 点按：上一页；
- 右侧 65%～100% 点按：下一页；
- 中间 35%～65% 点按：忽略，保留阅读器设置区；
- 向左滑动：下一页；
- 向右滑动：上一页；
- 垂直滑动、无效滑动、无坐标触摸：忽略。

手势开始时记录旋转值。如果手势结束前屏幕发生旋转，则丢弃这次手势，避免起点和终点不在同一坐标系中。

### 4.3 按键语义

按键不根据触摸坐标判定，而是根据设备能力和旋转查表：

- 掌阅：音量键在 180°/270°按映射表交换语义；
- 汉王：0°/90°使用一组翻页键，180°使用另一组，270°回到 0°按键；
- 大我：物理音量键始终沿用 0°语义，不随屏幕旋转交换；
- 设备按键节点从实际 `getevent -lp` 能力自动发现，不依赖固定 event 编号。

## 5. 动画接口调用

`RippleMapping.command()` 是唯一的接口码入口。输入参数只有：

```text
设备类型 + 动画类型 + 屏幕旋转 + 输入来源 + 上一页/下一页 + 掌阅速度
```

反向动画只交换逻辑上的上一页和下一页，不改变触控区域、按键识别或旋转识别。完整表格见 [`BIGME_HANVON_IREADER_MAPPING.md`](../BIGME_HANVON_IREADER_MAPPING.md)。

厂商适配类收到明确的接口码后直接调用：

- 掌阅：调用 EPDC 效果码；
- 汉王：调用 `setRegalRippleSpeed` 和 `setRegalRipple`；
- 大我：调用 `setEinkAnimationTimeout(mode, duration)`。

厂商适配类不得再次根据 `next`、`rotation` 或 `isWaterfall` 计算方向。

## 6. 日志约定

每次下发日志应包含：

```text
设备、输入来源、原始事件、逻辑方向、反向开关、事件旋转、动画类型、接口码、下发结果
```

建议使用 `TouchService`、`HanvonRipple`、`BigmeRipple` 标签过滤日志。遇到无动画时，先确认日志是否出现“接口码”和“已下发”，再区分事件识别问题或厂商接口问题。

## 7. 构建与测试

```powershell
# 在项目根目录执行
.\gradlew.bat :manager:testDebugUnitTest :manager:assembleDebug --no-daemon
```

当前工程的隐藏 API stub 使用本地 demo 目录。若构建提示 `api/hidden-api-stub` 不存在，可临时创建 junction，构建结束后删除：

```powershell
$junction = Join-Path (Get-Location) 'api\hidden-api-stub'
$target = Join-Path (Get-Location) 'api\demo-hidden-api-stub'
New-Item -ItemType Junction -Path $junction -Target $target
try { .\gradlew.bat :manager:testDebugUnitTest :manager:assembleDebug --no-daemon }
finally { Remove-Item -LiteralPath $junction -Force }
```

Debug APK 输出在 `manager/build/outputs/apk/debug/`。当前测试覆盖映射表全部设备、旋转、触控/按键来源组合，以及触控坐标旋转转换。

## 8. 扩展规则

新增设备或修正接口时按以下顺序处理：

1. 先更新映射文档；
2. 再修改 `RippleMapping.kt`；
3. 为四个旋转和两种输入来源补齐测试；
4. 最后修改厂商适配类的接口调用；
5. 不在 `TouchService`、Activity 或厂商适配类中新增方向分支。

