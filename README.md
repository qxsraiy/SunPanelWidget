# SunPanel Desktop Widget

一个 Android 桌面小部件，用于在手机桌面上直接展示 Sun-Panel 导航面板的书签，**无需打开浏览器、无需登录**，直接点击即可访问。

## 核心功能

- **全屏展示**：桌面小部件占据一页，用 GridView 展示 Sun-Panel 的所有书签
- **多列自适应**：根据小部件尺寸自动计算列数（3~8列）
- **图标缓存**：自动下载网站图标并缓存，下次打开秒显
- **一键跳转**：点击任意书签直接打开浏览器访问目标网站（可切换默认浏览器 / Chrome）
- **定时刷新**：每 1 小时自动更新书签数据
- **手动刷新**：打开 App 点击"刷新桌面小部件"即可立即同步

## 打开方式（浏览器选择）

小部件点击书签后的打开方式可在 App 设置页中切换：

- **默认模式（不勾选）**：调用系统默认浏览器；如果系统没有设置默认浏览器，Android 会弹出应用选择器
- **Chrome 模式（勾选）**：直接定位到 Chrome（包名 `com.android.chrome`）打开；若设备未安装 Chrome，自动回退到默认浏览器

实现原理：`Intent.ACTION_VIEW` + `setPackage("com.android.chrome")` + `packageManager.resolveActivity()` 检测 Chrome 是否存在。

## 适用场景

- 手机访问 NAS 导航面板时，每次都要打开浏览器 → 输入地址 → 登录 → 点击书签，非常繁琐
- 本小部件直接将书签渲染在桌面，**滑动到桌面页面 → 点击 → 直达**，三步变一步

## 项目结构

```
SunPanelWidget/
├── app/
│   ├── src/main/java/com/sunpanel/widget/
│   │   ├── MainActivity.kt              # 设置页：配置服务器地址/账号/密码
│   │   ├── SunPanelWidgetApp.kt         # Application 入口
│   │   ├── SunPanelWidgetProvider.kt    # 桌面小部件 Provider（生命周期、点击跳转）
│   │   ├── SunPanelRemoteViewsService.kt # RemoteViewsService + Factory（核心渲染引擎）
│   │   ├── api/
│   │   │   └── SunPanelApi.kt           # Retrofit 接口定义 + 客户端工厂
│   │   └── data/
│   │       ├── Models.kt                # 数据类（分组、书签、API响应）
│   │       └── PreferencesManager.kt    # 本地持久化存储
│   └── src/main/res/
│       ├── layout/
│       │   ├── activity_settings.xml    # 设置页 UI
│       │   ├── widget_layout.xml        # 小部件主布局（GridView）
│       │   └── widget_item.xml          # 单格书签布局
│       ├── xml/
│       │   └── sunpanel_widget_info.xml # 小部件配置元数据
│       └── drawable/...
├── build.gradle.kts
└── settings.gradle.kts
```

## 开发环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Ladybug 2024.2+ (推荐最新版) |
| JDK | 17+ (推荐使用 Android Studio 捆绑的 JBR 21) |
| Gradle | 8.14.5 (wrapper) |
| AGP | 8.13.2 |
| Kotlin | 2.1.21 |
| compileSdk | 34 |
| minSdk | 26 (Android 8.0) |

> ⚠️ **Java 25 兼容性说明**：
> 如果你使用的是 JDK 25，可能会出现 `java.lang.IllegalArgumentException: 25.0.2` 错误。
> 解决方法：
> 1. **在 Android Studio 中**：`File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`，选择 Embedded JDK (JBR 21) 或 JDK 17/21
> 2. **命令行构建**：`set JAVA_HOME=C:\path\to\jdk-21` / `export JAVA_HOME=/path/to/jdk-21`

## 构建步骤

1. 用 Android Studio 打开 `SunPanelWidget/` 目录
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器（Android 8.0+）
4. 点击 Run ▶ 安装到手机
5. 安装后打开 App，配置你的 Sun-Panel 服务器地址和账号密码
6. 返回桌面，长按空白处 → 添加小部件 → 找到 "SunPanel 书签"
7. 将小部件拖到合适位置，尽量占满一页

## Sun-Panel API 接口参考

本 App 调用的 Sun-Panel 接口（基于 v1.3.0 开源版源码分析）：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/login` | POST | 登录获取 Token，body: `{username, password}` |
| `/api/panel/itemIconGroup/getList` | POST | 获取所有分组，header: `Bearer {token}` |
| `/api/panel/itemIcon/getListByGroupId` | POST | 获取指定分组下的书签，body: `{itemIconGroupId}` |

所有接口返回 `{code: 0, data: ..., msg: "success"}`。code=0 表示成功。

## 为什么是 Kotlin + Native？

- **RemoteViews 限制**：Android 桌面小部件不能使用自定义 View，必须用系统组件（GridView/ListView），通过 RemoteViewsService 提供数据
- **流畅体验**：Native 层直接渲染，图标 Bitmap 缓存到内存和磁盘，滑动无延迟
- **全屏适配**：GridView 宽高设为 match_parent，配合动态列数计算，完美填满桌面页面

## 常见问题

**Q: 小部件显示"请先在 App 中配置"？**
A: 打开 App 填写服务器地址和账号密码，点击"保存并同步"。

**Q: 点击书签没有反应？**
A: 检查服务器地址是否正确，确保小部件权限未被禁用。

**Q: 数据不更新？**
A: 打开 App 点击"刷新桌面小部件"，或等待 1 小时自动刷新。

## License

MIT