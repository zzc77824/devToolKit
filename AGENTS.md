# AGENTS.md

本文件是 AI 编码代理（DSH、Claude Code、Codex、Cursor 等）在本仓库工作时的唯一指引。
改动项目结构、构建方式或约定后，请同步更新本文件。

## 项目概览

- **DevToolkit**：IntelliJ Platform 插件（JetBrains IDE 开发者工具集）。
- 插件 ID `org.zzc.DevToolkit`，**不可变更**（发布后改动会导致插件被视为新插件）。
- Kotlin + Gradle Kotlin DSL。
- 目标 IDE：IntelliJ IDEA `2026.2.4`（见 `build.gradle.kts` 的 `intellijPlatform` 依赖块）。
- Gradle Wrapper `9.8.2`，Kotlin `2.4.0`，测试框架 JUnit 4.13.2 + Platform Test Framework。
- 单个 `DevToolkit` 工具窗口，包含 `QR Code` 与 `JSON` 两个标签页（`JBTabbedPane`）。
- QR Code：把任意文本（UTF-8，含中文/emoji）生成二维码，可调容错率 L/M/Q/H 与尺寸 64–2048px，输入时实时预览。
- 二维码编码委托给 ZXing（`com.google.zxing:core`，见 `gradle/libs.versions.toml`），
  生成逻辑在 `org.zzc.qr.QrCodeGenerator`，UI 在 `org.zzc.QrCodePanel`。
- JSON：标签页支持格式化（2/4 空格）、压缩、完整 JSONPath 查询；
  逻辑在 `org.zzc.json.JsonFormatter`（Jackson 解析 + Jayway JSONPath），UI 在 `org.zzc.JsonPanel`。
  解析/查询在后台线程执行，仅最新一次操作会更新 UI。
- JSON 输入/输出使用 `CollapsibleJsonEditor`（`EditorTextField` + JSON FileType + 自定义括号折叠），
  依赖平台 JSON 插件 `com.intellij.modules.json`；折叠区间由 `org.zzc.json.JsonFoldRanges` 计算。
- 两个标签页都是左右分栏布局：左侧输入与参数，右侧预览/结果。

## 构建与常用命令

始终使用仓库自带的 Wrapper（`./gradlew`），不要依赖全局 Gradle。

| 命令 | 用途 |
|---|---|
| `./gradlew check` | 编译 + 运行全部检查与测试（提交前必跑） |
| `./gradlew test` | 只跑单元测试 |
| `./gradlew runIde` | 启动带插件的沙箱 IDE，用于手工验证 |
| `./gradlew verifyPlugin` | 校验插件在目标 IDE 版本上的兼容性 |
| `./gradlew buildPlugin` | 打包插件，产物在 `build/distributions/` |
| `./gradlew publishPlugin` | 发布到 JetBrains Marketplace（需凭证，勿在验证中执行） |
| `./gradlew patchChangelog` | 根据 `CHANGELOG.md` 的 `[Unreleased]` 生成版本条目 |

- 运行日志：`.intellijPlatform/sandbox/*/*/log/idea.log`；测试日志在 `log-test/idea.log`。
- 构建缓存与配置缓存已在 `gradle.properties` 中开启（`org.gradle.caching`、`org.gradle.configuration-cache`）。
  编写构建脚本时保持任务可缓存：不要读取环境变量/时间戳，不要使用 `doLast` 中的非确定性逻辑。
- 首次构建会下载 IntelliJ Platform SDK（数百 MB），耗时较长属正常现象。
- JDK 要求 21+；当前机器默认 JDK 25。IDE 运行时使用 JetBrains Runtime，无需手动配置。

## 目录结构

```
src/main/kotlin/                    Kotlin 生产代码（UI 在 org.zzc，纯逻辑在 org.zzc.qr / org.zzc.json）
src/main/resources/META-INF/        plugin.xml、pluginIcon.svg
src/main/resources/messages/        国际化消息 Bundle
src/test/kotlin/                   测试代码（JUnit 4：生成器单测 + 工具窗口 UI 冒烟测试）
.run/                              IDE 运行配置（runIde / check / verifyPlugin）
gradle/libs.versions.toml          依赖版本目录
build.gradle.kts                   构建脚本
settings.gradle.kts                仓库与插件版本声明
```

## 编码约定

- 包名统一 `org.zzc`；新增顶层包时保持 `org.zzc.*` 前缀。
- **所有用户可见文案**放进 `messages/MyMessageBundle.properties`，通过 `MyMessageBundle.message(...)` 读取，
  不要在代码中硬编码界面文本。新增 Bundle 时在 `plugin.xml` 中登记 `<resource-bundle>`。
- 扩展点、监听器、Tool Window 等一律在 `plugin.xml` 的 `com.intellij` 命名空间下注册，
  不要在代码里做静态全局注册。
- 新增第三方库通过 `gradle/libs.versions.toml` 版本目录声明，再在 `build.gradle.kts` 引用；
  依赖新的平台模块时在 `intellijPlatform { }` 块里显式添加（如 `bundledPlugin("com.intellij.java")`）。
- 新增工具窗口时：面板放 `org.zzc`（UI）+ 独立逻辑类（纯 Kotlin，便于单测），在 `plugin.xml` 注册
  `toolWindow` 并在 Bundle 中加 `toolwindow.stripe.<Id>`。
- `settings.gradle.kts` 中仓库顺序有意为之：本地平台构件仓库在前、`mavenCentral()` 其次、
  JetBrains 远程仓库最后，改动前先确认依赖解析仍然正常。
- 遵守 IntelliJ 线程模型：UI 修改在 EDT 上执行，耗时操作放到后台任务
  （`ProgressManager.runInBackground` / `Task.Backgroundable`），禁止在 EDT 上阻塞。
- 面向 2026.2.4 平台 API；使用已废弃 API 前先确认替代方案。
- 测试放 `src/test/kotlin`，优先使用 Platform Test Framework（`BasePlatformTestCase` 等）覆盖
  扩展点注册与核心逻辑。

## 变更纪律

- 修改用户可见行为时，在 `CHANGELOG.md` 的 `[Unreleased]` 下补一条（Keep a Changelog 格式）。
- `plugin.xml` 中的 `<id>` 不可变；`<name>`、`<vendor>`、`<description>` 可按需完善。
- 不要提交 `build/`、`.gradle/`、`.intellijPlatform/`、`.idea/`（已在 `.gitignore` 中）；
  不要手工编辑 `DevToolkit.iml` 等 IDE 生成文件。
- 提交前确认 `./gradlew check` 通过；涉及兼容性/发布配置的改动再跑 `./gradlew verifyPlugin`。
- 只改与任务相关的文件，避免顺手格式化无关代码。

## 完成定义

1. 代码可编译，`./gradlew check` 通过（无新增警告理想）。
2. 新功能有对应测试，或在无法自动化时说明手工验证步骤（`runIde`）。
3. 文案已进 Bundle，扩展已进 `plugin.xml`。
4. `CHANGELOG.md` 已按需更新。
