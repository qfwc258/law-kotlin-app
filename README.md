# 法规宝典 (Law Kotlin App)

一款基于 **Kotlin + Jetpack Compose** 的安卓法律法规查询 APP，对接 **国家法律法规数据库**（flk.npc.gov.cn），支持法条搜索、详情查看、收藏与阅读历史。

## 功能特性

- 🔍 **法条搜索**：关键词模糊/精确搜索，支持按法规类型筛选、日期范围、排序
- 📖 **法规详情**：自动解析"第X条"结构，逐条展示正文
- ⬇️ **原文下载**：支持 PDF / WPS 双格式下载，通知栏显示进度，下载完成可直接打开
- ⭐ **收藏管理**：本地收藏常用法规，离线可查
- 📚 **阅读历史**：自动记录最近阅读，快速续读
- 💾 **离线缓存**：搜索结果自动缓存，网络不可用时回退本地
- 📱 **Material Design 3**：适配手机端，深色模式支持

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 架构 | MVVM + Repository |
| 网络 | Retrofit + OkHttp + Gson |
| 本地存储 | Room Database |
| 异步 | Kotlin Coroutines + Flow |
| 导航 | Navigation Compose |
| 构建 | Gradle (Kotlin DSL) |
| CI | GitHub Actions |

## 项目结构

```
app/src/main/java/com/law/app/
├── LawApp.kt                  # Application 类
├── MainActivity.kt            # 主 Activity
├── data/
│   ├── model/                 # 领域模型（Law, LawType, Article）
│   ├── remote/
│   │   ├── api/               # Retrofit API 接口
│   │   ├── dto/               # 网络响应 DTO
│   │   └── NetworkModule.kt   # OkHttp/Retrofit 配置
│   ├── local/                 # Room 实体、DAO、数据库
│   └── repository/            # 数据仓库（远程+本地）
├── ui/
│   ├── theme/                 # 主题、颜色、字体
│   ├── home/                  # 首页
│   ├── search/                # 搜索页
│   ├── detail/                # 详情页
│   ├── favorites/             # 收藏页
│   └── common/                # 通用组件
├── navigation/                # 导航图
└── util/                      # 工具类（Constants, Result）
```

## 数据来源

本 APP 数据来源于 **国家法律法规数据库**：

- 官网：https://flk.npc.gov.cn/
- API：https://flk.npc.gov.cn/api/

> 本 APP 仅作为学习和个人使用工具，法规内容版权归原发布机关所有。如需正式法律用途，请以官方发布文本为准。

## API 接口说明

### 搜索接口

```
GET https://flk.npc.gov.cn/api/
```

| 参数 | 说明 | 示例 |
|------|------|------|
| `title` | 搜索关键词 | `民法典` |
| `type` | 法规类型 | `flfg` / `xzfg` / `sfjs` / `dfxfg` |
| `searchType` | 搜索方式 | `title;vague` / `title;accurate` |
| `page` | 页码 | `1` |
| `size` | 每页条数 | `10` |
| `sortTr` | 排序 | `f_bbrq_s;desc` |
| `gbrqStart/End` | 公布日期范围 | `2020-01-01` |
| `sxrqStart/End` | 施行日期范围 | `2021-01-01` |

## 构建

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- Gradle 8.5

### 构建命令

```bash
# 调试版
./gradlew assembleDebug

# 发布版（需配置签名）
./gradlew assembleRelease

# 运行单元测试
./gradlew testDebugUnitTest

# Lint 检查
./gradlew lintDebug
```

构建产物位于 `app/build/outputs/apk/`。

## CI/CD

项目配置了 GitHub Actions 工作流（`.github/workflows/build-apk.yml`）：

- 每次 push / PR 自动运行 Lint + 单元测试 + Debug APK 构建
- main/master 分支额外构建 Release APK
- 构建产物作为 Artifact 上传，保留 30-90 天

## 许可证

MIT License — 仅用于学习交流，法规内容版权归原发布机关所有。
