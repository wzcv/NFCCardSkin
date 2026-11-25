# NFC 卡面（NFCCardSkin）

ColorOS 钱包（`com.finshell.wallet`）的 NFC 卡面自定义 LSPosed 模块。界面用
[COUI](https://github.com/suqi8/coui)（ColorOS 17 组件的 Compose 实现），思路参考
[cardface-miuix](https://github.com/MiamBao/cardface-miuix) 与
[DIY NFC 卡面](https://github.com/Xposed-Modules-Repo/com.zhizi42.diymiuicard)。
软件由ai辅助完成。

## 使用

1. 安装后在 LSPosed 启用模块，作用域勾选「钱包」（App 首页也可一键请求）。
2. 重启钱包，浏览卡包 / 卡片详情页；看到的卡面会自动出现在本 App 首页。
3. 点卡片 → 从相册选图并裁剪（1.62 : 1）/ 从图库选 / 填网络图片地址。
4. 重启钱包生效（首页「重启钱包」需要 root；没有 root 就手动划掉钱包）。

不需要 root：替换图由本 App 的 ContentProvider 提供，钱包持有 `QUERY_ALL_PACKAGES`，可直接读取。

## libxposed API 102

- 依赖：`io.github.libxposed:api:102.0.0`（compileOnly）、`io.github.libxposed:service:102.0.0`。
- `module.prop`：`targetApiVersion=102`、`minApiVersion=101`、`autoHotReload=true`、`exceptionMode=protective`。102 专属调用都按运行时 API 版本判断，API 101 的框架照常可用
- **Hook id**：每个 hook 用 `setId("nfcskin:<类>#<方法>(<参数>)")`，同 id 再 hook 即原子替换。
- **热重载**：更新模块 APK 后，旧代码在 `onHotReloading` 交出钱包的 ClassLoader 和 Context（不跨代传本模块的对象，并注销远程偏好监听），新代码在 `onHotReloaded` 用相同 id 重新 hook 完成替换，再卸掉不再需要的旧 hook。不用重启钱包。
- **服务端**：App 通过 `getRunningTargets()` 在「主页 → 钱包进程」显示各进程是否为当前版本，可手动 `hotReloadModule()`；框架名称 / 版本直接读 `getFrameworkName()` / `getFrameworkVersion()`。

## 开发者选项

连点7次版本号

## Hook 点

FinUI 组件类名未混淆，按名字定位；Glide 层被混淆，按签名定位，不依赖 DexKit。

| 位置 | 作用 |
|---|---|
| `com.finshell.finui.widget.atomic.card.FinUINfcCardFaceView#showCardImg(String url, String title, boolean, int)` / `#setCardIconAndName(...)` | 卡包、首页轮播、详情页卡面；记录 URL + 卡名并替换 |
| `com.finshell.finui.widget.atomic.FinBigCardFaceView#setCardFaceUrl(String)` | 交通卡详情（`BusDetailActivity`）；记录并替换 |
| `com.finshell.finui.widget.atomic.card.FinCustomCardView#setCardFaceUrl(String)` | 自定义卡视图；记录并替换 |
| `com.finshell.finui.widget.imageview.CircleNetworkImageView` 中「首参 String、返回 Glide RequestBuilder」的方法（5.52 为 `A0/x0/y0`） | 所有 `setImageUrl` 路径的汇聚点（如 `NestNfcHeaderCardAdapter`）；只替换 |
| `com.finshell.finui.utils.CardCoverSettingUtil$Companion#fetchBrightness*` | 钱包按卡面亮度决定文字黑白，让它采样替换图 |
| Glide `RequestManager.load(String/Object)`（设置里「全局 Glide 兜底」） | 由 RequestBuilder 的 `ComponentCallbacks2` 字段反推类型；默认关闭 |

替换为本地图时会调用 `CircleNetworkImageView.setCompatibleSignature(false)`，否则模型被包成
`GlideUrl`（`imageview.j`）无法加载 `content://`。

## 在线卡面库

卡面库为HiBank
- 目录：`https://hi.my-api.cn/api/?resource=face_ctu|face_ctu_origin|face&page=N&page_size=100`，整类拉取后本地按拼音分组排序，缓存 7 天，下拉刷新强制更新。
- 图片只接受 `https://storage.my-api.cn/static/images/…`；应用时下载原图（≤16 MB）、居中裁剪为 1.62:1 存入图库，**不把远程地址交给钱包**。同一张在线图只存一次。
- 缩略图默认走 wsrv.nl 图片代理（设置里可关），代理失败自动回退原图。

## 结构

```
app/src/main/java/com/wu/nfccardskin/
├── xposed/      HookEntry（libxposed API 101）、WalletHooker（hook 点）、HookConfig（配置/上报）、XLog
├── core/        SkinConfig（钱包内外共用的配置 + URL 归一化）、Repository、SkinProvider、RemoteBridge、ImageStore、WalletOps
└── ui/          MainShell（悬浮底栏）+ 主页 / 卡面 / 图库 / 设置四个标签页；OnlineLibrary（卡面库，嵌在图库里）；编辑、裁剪、标志、帮助、关于、自定义 hook 页
```

配置通道：App → LSPosed 远程偏好 `config/json`（钱包冷启动即可同步读取）；兜底为
`SkinProvider.call("get_config")`。钱包 → App：`call("report")` 上报卡面，`call("hello")` 上报生效状态。
Provider 只接受本 App 与钱包的 uid。

## 构建

Android Studio（AGP 9.4 / Gradle 9.6 / Kotlin 2.4.10）。COUI 2.1.0 由 Kotlin 2.4 编译，Kotlin 不能低于 2.4。

`./gradlew :app:testDebugUnitTest` 覆盖配置序列化与 URL 匹配。

## 开源协议

本项目以 **GPL-3.0** 发布，全文见 [LICENSE](LICENSE)。

- 卡面标志图片等（`assets/card_logo`）来自 [DIY NFC 卡面](https://github.com/Xposed-Modules-Repo/com.zhizi42.diymiuicard)（GPL-3.0）。
- 在线卡面库客户端等参考 [cardface-miuix](https://github.com/MiamBao/cardface-miuix)（GPL-3.0）。
- `CouiPage.kt` / `CouiBlur.kt` 来自 [COUI](https://github.com/suqi8/coui)（Apache-2.0），保留原版权声明。
- 不包含 ColorOS 钱包的任何代码；在线卡面图片由用户自行下载，版权归原作者。
