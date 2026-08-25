# TubeTune — Yupi Music Download

免費、開源的 YouTube 音樂下載播放器（Android，Compose）。搜尋 YouTube、預覽（音訊／影片）、一鍵下載成音訊檔存入音樂庫並直接播放。

## 功能

- 🔍 **YouTube 搜尋**（多頁載入、長按多選批量下載）
- ▶️ **預覽**：音訊／影片預覽（取自 NewPipe Extractor）
- ⬇️ **高速下載**：Range 分段並行（8 路 HTTP/1.1，每條連線獨立限速突破，實測最高 300+ 倍加速；單連線 fallback）
- 🎵 **音樂庫**：資料夾管理、依資料夾播放
- 🔀 **播放模式**：順序 / 循環 / 隨機 / 單曲重複（每個資料夾各自記憶）
- 🎚️ **播放器**：滑條拖曳、上一首／下一首
- 📱 **分享整合**：從其他 App 分享 YouTube 連結直接加入下載

## 技術

- Kotlin + Jetpack Compose (Material 3)
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5（解析搜尋／串流）
- OkHttp 4.12（下載引擎，多連線分段）
- Android `MediaPlayer`（預覽與本地播放）

## 建置

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

需要 Android SDK（`local.properties` 指向本機 SDK 路徑）。

> 僅供個人學習與備份使用。請遵守 YouTube 服務條款與當地法律，尊重著作權。
