# Release 签名包

## 签名信息

| 项目 | 值 |
|------|-----|
| Keystore 文件 | `law-app-release.keystore` |
| 别名 (Alias) | `law-app` |
| 有效期 | 10,000 天（至 2054 年） |
| 算法 | RSA 2048 / SHA256withRSA |

### 证书指纹

```
SHA1:   DB:E9:FF:4F:8C:25:C7:6E:78:51:BD:F1:DB:23:8C:56:7D:45:7B:28
SHA256: BA:C7:CC:A4:E0:FA:D4:A2:AC:17:22:75:B3:A5:8F:4C:D0:15:DE:04:2F:38:84:68:8C:7C:1A:F0:18:64:19:3E
```

## 云端构建签名 APK

推送代码到 GitHub 后，GitHub Actions 会自动构建签名 Release APK。

构建完成后，在仓库 Actions 页面下载：
- **Artifact 名称**: `law-app-release-signed-apk`
- **保留时间**: 90 天

仓库地址：https://github.com/qfwc258/law-kotlin-app/actions

## 本地构建签名 APK

如需在本地构建，需先安装 Android SDK 和 JDK 17，然后：

```bash
# 确保 local.properties 中配置了签名信息（已在仓库根目录提供模板）
# 构建签名 Release APK
./gradlew assembleRelease

# 产物路径
app/build/outputs/apk/release/app-release.apk
```

## 安全提示

> ⚠️ 当前 keystore 密码直接配置在 CI 工作流和 local.properties 中，仅适用于个人/学习项目。
>
> 生产环境建议：
> 1. 将 keystore 文件通过 GitHub Secrets 编码存储
> 2. 密码配置为 GitHub Secrets（`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_PASSWORD`）
> 3. CI 工作流从 Secrets 读取，不硬编码密码
