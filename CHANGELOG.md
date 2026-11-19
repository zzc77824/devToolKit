<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# DevToolkit Changelog

## [Unreleased]

### Added

- 二维码生成工具窗口：把任意文本（UTF-8，支持中文/emoji）生成二维码，可调节容错率（L/M/Q/H）与生成尺寸，输入时实时预览。
- JSON 工具窗口：格式化（2/4 空格缩进）、压缩，以及基于 Jayway JSONPath 的查询（支持通配、递归、过滤表达式），解析错误提示行列号。

### Changed

- 两个工具合并为一个 `DevToolkit` 工具窗口，通过 `QR Code` / `JSON` 两个标签页切换。
- JSON 的输入与输出不再使用普通文本框，改为支持 JSON 高亮、行号与括号折叠的编辑器（`CollapsibleJsonEditor`），文本变化后自动重算折叠区域。
- 二维码标签页改为左右布局：左侧输入与参数，右侧实时预览。
- 新的工具箱插件图标 `pluginIcon.svg`，并新增深色主题版本 `pluginIcon_dark.svg`。
