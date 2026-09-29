# autoTask

轻量、防后台清理的安卓定时任务 App：**到点自动打开指定 App**，失败自动重试（含震动提醒）并记录完整日志。

- 💾 低内存：驻留态内存约 53 MB，release APK 仅 1.64 MB
- 🛡️ 防后台清理：前台服务常驻 + 厂商自启动引导 + 电池优化豁免 + 最近任务锁定引导
- 📅 智能调度：单次 / 每日 / 每周 / 工作日 / 节假日（内置 2025+2026 中国官方节假日与调休数据）
- 🔁 失败重试：最多 3 次尝试，间隔统一 30 秒，重试耗尽后**震动提醒**
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
- **应用选择器**：列出全部可启动应用（系统服务组件自动排除），**拼音首字母分组排序**（通讯录式）+ 右侧 A-Z 索引条
- **搜索**：支持应用名、拼音全拼（如 `feishu`）、首字母（如 `F`）
- **输入包名**：兜底手段。vivo 等系统对部分应用做「应用隐藏」时可枚举不到，直接输入包名创建任务，启动走系统解析
- **输入网址/深链**：支持 `https://` 或任意 `scheme://` 地址，到点按 scheme 路由打开（如淘宝 `taobao://`）
- **常用应用速查**：内置 20 个主流应用包名（微信/企业微信/QQ/飞书/钉钉/支付宝等），点选自动填入（已逐条真机实测「已安装且可拉起」）
- **测试启动**：保存前可一键试拉起目标应用，当场确认能否打开
- Android 10+ 后台启动豁免：自动检测悬浮窗权限并引导授权

> ⚠️ **维护须知 —— `<queries>` 声明不可删除**
>
> `AndroidManifest.xml` 里的 `<queries>`（`ACTION_MAIN` + `CATEGORY_LAUNCHER`）是 Android 11+ **包可见性**（package visibility）
> 的必需声明。缺失时，targetSdk ≥ 30 的应用会被系统过滤到只剩预装应用，表现为「**第三方 App 全部无法拉起**」：
>
> - `getLaunchIntentForPackage()` 对所有第三方 App 返回 `null`
> - 隐式 `startActivity` 抛 `ActivityNotFoundException: No Activity found to handle Intent`
> - 应用选择器只能枚举到预装系统应用
>
> 迷惑点在于**显式组件 `setClassName(pkg, activity)` 不受此过滤影响**，所以故障时会出现
> 「个别用显式组件的任务（如淘宝）正常、其余全挂」的现象，容易误判为 ROM 限制。

### 失败重试与通知
- 启动失败自动重试 3 次（间隔统一 30 秒），每次尝试记录日志
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

## 已知限制

- **vivo 等系统的"应用隐藏"**：被系统隐藏的应用无法被第三方 App 枚举，请在编辑页用"输入包名"或"常用应用速查"创建任务。
  ⚠️ 注意与「**Android 11+ 包可见性过滤**」区分：后者会让**所有**第三方 App 都拉不起来，已通过 `<queries>` 声明修复（见上文「自动打开 App」）
- **vivo"后台弹出界面"权限**（重要）：默认禁止第三方 App 在后台拉起 Activity（悬浮窗权限无法豁免），表现为"启动成功但目标应用未打开"。
  需在 **i管家 → 应用管理 → 权限管理 → 后台弹出界面** 中允许 autoTask（权限引导页已提供一键跳转入口）
- **节假日数据**：内置 2025、2026 年官方安排（国办发明电〔2025〕7号），2027+ 需更新 `assets/holidays.json`（无数据年份回退为周一~五规则）
- **Android 15 (API 35)**：尚未适配测试（当前 targetSdk 34）
- 个别系统应用（如设置深层页面）无法被外部拉起，属系统限制
- 非精确闹钟在部分 ROM 后台有 15~45s 延迟；精确闹钟需"闹钟和提醒"权限（adb 渠道安装默认授予）
