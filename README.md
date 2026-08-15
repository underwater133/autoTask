# autoTask

低内存、防后台清理的安卓定时任务 App：到点自动打开指定 App，失败自动重试并记录日志。

详细开发计划见 [docs/开发计划.md](docs/开发计划.md)。

## 环境要求

- JDK 17（本地工具链位于 `.tools/`，已 gitignore）
- Android SDK Platform 34 + Build-Tools 34（`local.properties` 指定 `sdk.dir`）
- Gradle 8.9（本地分发，通过 `gradlew` 使用）

## 构建

```powershell
.\gradlew.bat assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 里程碑状态

- [x] M0 项目骨架（构建环境 + 最小可运行 App）
- [x] M1 权限引导框架（悬浮窗/电池优化/通知/使用情况访问，一键跳转系统设置）
- [x] M2 任务 CRUD + SQLite（6 种调度模式字段，列表 + 编辑页）
- [x] M3 调度引擎（AlarmManager 精确闹钟 + 开机恢复 + 智能模式日期推算 + 2025 节假日日历）
- [x] M4 打开 App + 前台检测 + 重试（悬浮窗豁免后台启动 + UsageStats 前台判定 + 3 次递增重试）
- [x] M5 日志模块（环形 1MB 文件日志 + 执行日志页 + 清空）
- [x] M6 防清理完整方案（KeeperService 前台服务 dataSync + 厂商自启动引导 + 最近任务锁定引导 + 常驻服务状态项）
- [x] M7 真机测试（iQOO 12 / Android 14：端到端执行、重试机制、息屏 Doze、内存 53MB、防清理、vivo 自启动引导全部通过；修复"新建任务默认停用"bug，详见 `docs/真机测试清单.md`）
- [x] M8 发布（R8 压缩 + 签名，release APK 1.64 MB）

## 测试

**45 个单元测试全部通过**（JVM 纯逻辑 + Robolectric 运行时验证）：

| 测试类 | 覆盖 |
|---|---|
| NextTriggerCalculatorTest (14) | 6 种调度模式的日期推算（含调休补班、春节连休、工作日/节假日互斥） |
| RetryPolicyTest (3) | 重试次数与递增间隔 |
| TaskDaoTest (5) | SQLite 真实增删改查、启用开关（Robolectric） |
| SchedulerTest (5) | AlarmManager 注册/更新/取消、nextTriggerAt 持久化（Robolectric） |
| HolidayCalendarTest (9) | assets 节假日 JSON 加载（2025+2026 官方数据）、调休/节假日判定、无数据年份回退（Robolectric） |
| AppLoggerTest (4) | 文件日志写入/读取/清空/条数限制（Robolectric） |
| TaskExecutorFlowTest (4) | 端到端：触发→拉起→失败原因→重试调度→重试耗尽→停用；成功路径（Robolectric） |
| TaskEditActivityTest (1) | 回归：新建任务默认启用（Robolectric） |
