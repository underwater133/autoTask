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
- [x] M3 调度引擎（AlarmManager 精确闹钟 + 开机恢复 + 智能模式日期推算 + 2025 节假日日历，14 个单元测试通过）
- [x] M4 打开 App + 前台检测 + 重试（悬浮窗豁免后台启动 + UsageStats 前台判定 + 3 次递增重试，17 个单元测试通过）
- [x] M5 日志模块（环形 1MB 文件日志 + 执行日志页 + 清空）
- [x] M6 防清理完整方案（KeeperService 前台服务 dataSync + 厂商自启动引导 + 最近任务锁定引导 + 常驻服务状态项）
- [ ] M4 打开 App + 前台检测 + 重试
- [ ] M5 日志模块
- [ ] M6 防清理完整方案
- [x] M7 准备：真机测试清单 `docs/真机测试清单.md` + 冒烟脚本 `scripts/smoke-test.ps1`（**待真机执行**）
- [x] M8 发布（R8 压缩 + 签名，release APK 1.64 MB）
