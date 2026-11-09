# 144 项设备实测校准矩阵

本目录的 `calibration.csv` 按厂商、动画、旋转、触发方式、上一页/下一页生成，共 **144 项**。

- 默认每项 `UNVERIFIED`，不将 API 返回值当作真实波纹确认。
- `api_code` 为不含掌阅速度位的基础码；掌阅速度取 0、64、128。
- `event=TAP`：左/右侧点击；`SWIPE`：向右/向左水平滑动；`KEY`：设备翻页键。
- 填写 `physical_result` 观察到的真实波纹方向，再更新 status 为 PASS/FAIL。
- 必须记录设备型号、系统固件版本和对应 ADB 日志。不存在实机数据时不要填 PASS。
- 带有触控与按键同码但目标方向相反的组合应作为高优先级复查项。

回归链：`./gradlew :manager:testDebugUnitTest :manager:assembleDebug`；实机验证需要设备运行权限和人为视觉确认。
