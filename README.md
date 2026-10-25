# 订单一键拨号 · 安卓 App

把你的单页 HTML 包成手机 App。页面本身基本没改，只补了手机端必须的东西。

## 出 APK（不用装任何开发工具）

1. GitHub 新建仓库（Private 也行）
2. 把**本文件夹里的全部内容**拖进网页上传（含 `.github` 文件夹，它负责自动编译）
3. 上传后 Actions 自动跑，约 3–4 分钟
4. 进去 → 底部 **Artifacts** → 下载 `call-order-apk.zip` → 解压得 `app-debug.apk`
5. 手机安装（允许“未知来源”）

> 也可以用 Android Studio 打开本文件夹 → Build → Build APK(s)。

## 相比原版改了什么

| 项 | 原版 | App 版 |
|---|---|---|
| 手机适配 | 没有 `<meta viewport>`，手机上字极小 | 已加 viewport、按钮 44px 高、表格横向滚动 |
| jsQR | 从 CDN 加载，断网不能扫 | **打包进 APK**，离线可用（CDN 仅作兜底） |
| 摄像头 | 浏览器直接 `getUserMedia` | 原生授权：`CAMERA` + `onPermissionRequest` |
| 拨号 | `href="tel:"`，WebView 里点了没反应 | 原生拦截 → 打开系统拨号盘（`ACTION_DIAL`） |
| 数据 | IndexedDB | 同左（已开 DOM Storage） |
| 重复导入 | 重复订单号会重复入库 | 自动跳过，提示跳过几条 |
| CSV | 备注里有 `"` 会串列 | 标准双引号转义 |
| 单条删除 | 有接口没入口 | 每行加了「删除」 |
| 拍照识别 | 无 | 新增「🖼️ 拍照识别」：拍一张 → jsQR 解码（不依赖实时摄像头，老机型兜底） |

## 结构

```
call-order-android/
├── app/src/main/
│   ├── AndroidManifest.xml          # CAMERA / INTERNET
│   ├── assets/index.html            # 页面（jsqr.min.js 由构建时下载到这里）
│   ├── java/com/dsh/callorder/MainActivity.java
│   └── res/                         # 图标、主题、file_paths
├── .github/workflows/build-apk.yml  # 自动下载 jsQR + 编译
└── settings.gradle / build.gradle / gradle.properties
```

## 常见问题

- **扫码黑屏/打不开相机**：检查系统设置里本应用的「相机」权限；部分机型首次会弹窗，拒绝后不再弹，需手动开。
- **点手机号没反应**：确认系统有拨号应用；我们用的是 `ACTION_DIAL`（只打开拨号盘，不直接拨出）。
- **数据丢了**：数据在 App 私有存储的 IndexedDB 里，卸载即清空。重要记录请定期「导出备份 CSV」。
- **换了包名/想改图标**：改 `app/build.gradle` 的 `applicationId` 和 `res/values/strings.xml` 的 `app_name`。
