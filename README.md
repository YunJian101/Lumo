# 流光影视 · Lumo

Android TV / 大屏影视客户端：Kotlin + Jetpack Compose + Media3，对接飞牛影视（fnOS Media）网页接口。

## 功能

- 登录（服务器地址 / 账号 / 密码，token 仅保存在本机）
- 首页：Hero 大图、媒体库、继续观看、按库分组的最近添加
- 媒体库：海报网格、分类 / 筛选、拼音排序
- 详情：剧集 / 单集信息、选集、季切换、演职人员、文件与音视频信息
- 播放器：Media3 ExoPlayer、自绘控制层、遥控器操作
- 搜索、设置（退出登录）

## 接口

数据来自飞牛影视网页 API（`<host>/v/api/v1/...`）：

| 用途 | 请求 |
| --- | --- |
| 登录 | `POST /v/api/v1/login` |
| 媒体库 | `GET /v/api/v1/mediadb/list` |
| 条目列表 | `POST /v/api/v1/item/list` |
| 详情 | `GET /v/api/v1/item/{guid}` |
| 播放信息 | `POST /v/api/v1/play/info` |
| 流信息 | `POST /v/api/v1/stream` |
| 取流 | `GET /v/api/v1/media/range/{media_guid}` + `Authorization` + `Range` |
| 图片 | `GET /v/api/v1/sys/img/<path>?w=400` + `Authorization` |

请求头 `authx` 签名：`md5(apiKey_path_nonce_timestamp_dataHash_secret)`，`/media/` 路径不签名。

## 构建

1. 在 `local.properties` 配置：

```properties
sdk.dir=/path/to/android/sdk
fn.apiKey=YOUR_API_KEY
fn.apiSecret=YOUR_API_SECRET
```

2. 构建：

```bat
gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装

```bat
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 已知限制

- 部分模拟器缺少 4K HEVC 10bit 硬解，播放 4K 片源可能提示 `ERROR_CODE_DECODING_FAILED`；支持硬解的电视盒子不受影响。
- 暂未接转码、字幕与音轨切换。
