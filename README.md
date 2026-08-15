# autoTask

轻量、防后台清理的安卓定时任务 App：**到点自动打开指定 App**，失败自动重试（含震动提醒）并记录完整日志。

- 💾 低内存：驻留态内存约 53 MB，release APK 仅 1.64 MB
- 🛡️ 防后台清理：前台服务常驻 + 厂商自启动引导 + 电池优化豁免 + 最近任务锁定引导
- 📅 智能调度：单次 / 每日 / 每周 / 工作日 / 节假日（内置 2025+2026 中国官方节假日与调休数据）
- 🔁 失败重试：最多 3 次尝试，30s/60s/120s 递增间隔，重试耗尽后**震动提醒**
- 📝 执行日志：环形 1MB 文件日志，App 内可查看/清空

## 功能特性

### 任务调度
| 模式 | 说明 |
|---|---|
| 单次 | 指定时刻执行一次，执行后自动停用 |
| 每日 | 每天固定时刻 |
| 每周 | 勾选任意星期（一~日） |
| 工作日 | 周一~五，自动处理中国法定节假日与调休补班（含 2025/2026 官方数据） |
| 节假日 | 周末 + 法定节假日，跳过调休补班日 |
| 智能 | 规则组合（当前按每日处理，字段已预留） |

### 自动打开 App
- **应用选择器**：只显示可启动的应用（系统服务组件自动排除），**拼音首字母分组排序**（通讯录式）+ 右侧 A-Z 索引条
- **搜索**：支持应用名、拼音全拼（如 `feishu`）、首字母（如 `F`）
- **输入包名**：vivo 等系统会隐藏部分应用（无法被任何第三方 App 枚举），可直接输入包名创建任务——启动走系统解析，不受隐藏过滤影响
- **常用应用速查**：内置 25 个主流应用包名（微信/QQ/飞书/支付宝等），点选自动填入
- Android 10+ 后台启动豁免：自动检测悬浮窗权限并引导授权

### 失败重试与通知
- 启动失败自动重试 3 次（30s → 60s → 120s），每次尝试记录日志
- 失败原因分类：权限缺失 / 前台验证超时 / 启动异常等
- 重试耗尽后**手机震动提醒**（双段脉冲）

### 防后台清理（多层方案）
1. **前台服务** `KeeperService`（Android 14 `dataSync` 类型合规，START_STICKY）
2. **权限引导页**：悬浮窗 / 电池优化 / 通知 / 使用情况访问 / 常驻服务，一键跳转系统设置，返回自动刷新
3. **厂商适配**：小米 / 华为 / 荣耀 / OPPO / vivo / 三星 自启动管理页跳转（含系统设置兜底）
4. **最近任务锁定** 图文引导

## 使用

1. 安装后打开 **菜单 → 权限检测**，逐项开启（悬浮窗、忽略电池优化、通知、使用情况访问、常驻服务）
2. 按引导开启 **自启动管理**（国产 ROM 必需）、在最近任务界面**下拉锁定**本应用
3. 右下角 **+** 新建任务：命名 → 选择应用（或输入包名）→ 选择调度模式与时间 → 保存
4. 执行结果查看 **菜单 → 执行日志**

## 构建

要求：JDK 17、Android SDK Platform 34 + Build-Tools 34、Gradle 8.9（国内网络需配置镜像，见 `settings.gradle.kts`）。

```powershell
.\gradlew.bat assembleDebug    # debug APK
.\gradlew.bat assembleRelease  # release APK（R8 压缩 + 签名）
```

- debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- release APK：`app/build/outputs/apk/release/app-release.apk`（密钥库位于 `.tools/release/`，未入库，正式发布需自行替换）

## 架构

```
app/src/main/java/com/autotask/app/
├── task/          # 任务模型 + 原生 SQLite（TaskDbHelper/TaskDao，无 Room 保持轻量）
├── schedule/      # 调度引擎：AlarmManager 精确闹钟、TaskAlarmReceiver、开机恢复 BootReceiver、日期推算
├── calendar/      # 节假日日历（assets/holidays.json，2025+2026 官方数据 + 调休补班）
├── executor/      # 执行器：AppLauncher（三级启动策略）、ForegroundDetector（UsageStats 前台判定）、重试策略、失败震动
├── picker/        # 应用选择器：全量扫描、拼音分组排序（TinyPinyin）、搜索、索引条
├── permission/    # 权限引导：悬浮窗/电池优化/通知/使用情况访问/常驻服务 + 厂商自启动跳转
├── log/           # 环形文件日志（1MB 上限，线程安全）
└── service/       # KeeperService 常驻前台服务（dataSync）
```

## 测试

**50 个单元测试全部通过**（JVM 纯逻辑 + Robolectric 运行时验证，`.\gradlew.bat testDebugUnitTest`）：

| 测试类 | 覆盖 |
|---|---|
| NextTriggerCalculatorTest (14) | 6 种调度模式的日期推算（含调休补班、春节连休、工作日/节假日互斥） |
| HolidayCalendarTest (9) | assets 节假日 JSON 加载（2025+2026）、调休/节假日判定、无数据年份回退 |
| TaskDaoTest (5) | SQLite 真实增删改查、启用开关 |
| SchedulerTest (5) | AlarmManager 注册/更新/取消、nextTriggerAt 持久化 |
| TaskExecutorFlowTest (4) | 端到端：触发→拉起→失败→重试→耗尽→震动→停用；成功路径 |
| AppLoggerTest (4) | 文件日志写入/读取/清空/条数限制 |
| RetryPolicyTest (3) | 重试次数与递增间隔 |
| AppPinyinTest (6) | 拼音分组、首字母、排序键（中英文混合） |
| TaskEditActivityTest (1) | 回归：新建任务默认启用 |

真机验证（iQOO 12 / Android 14）：端到端执行、重试链、息屏 Doze 触发、驻留内存 53MB、前台服务防清理、vivo 自启动引导跳转、隐藏应用（飞书）按包名拉起，全部通过。详见 [docs/真机测试清单.md](docs/真机测试清单.md)。

## 开发计划与文档

- [docs/开发计划.md](docs/开发计划.md) —— 需求、技术选型、里程碑
- [docs/真机测试清单.md](docs/真机测试清单.md) —— 真机测试矩阵与实测记录
- [scripts/smoke-test.ps1](scripts/smoke-test.ps1) —— 真机冒烟测试脚本（安装/启动/服务/闹钟/截图）

## 已知限制

- **vivo 等系统的"应用隐藏"**：被隐藏的应用无法被任何第三方 App 枚举（系统级过滤），请使用"输入包名"或"常用应用速查"创建任务
- **节假日数据**：内置 2025、2026 年官方安排（国办发明电〔2025〕7号），2027+ 需更新 `assets/holidays.json`（无数据年份回退为周一~五规则）
- **Android 15 (API 35)**：尚未适配测试（当前 targetSdk 34）
- 个别系统应用（如设置深层页面）无法被外部拉起，属系统限制
- 非精确闹钟在部分 ROM 后台有 15~45s 延迟；精确闹钟需"闹钟和提醒"权限（adb 渠道安装默认授予）
