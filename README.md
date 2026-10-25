# TubeTune — Yupi Music Download

免費、開源的 YouTube 音樂下載播放器（Android，Compose）。搜尋 YouTube、預覽、一鍵下載成 **MP3** 存入音樂庫並直接播放。

## 功能

- 🔍 **YouTube 搜尋**（多頁載入、長按多選批量加入資料夾）
- ▶️ **預覽**：音訊試聽／影片預覽（NewPipe Extractor）
- ⬇️ **高速下載**：Range 分段並行（8 路 HTTP/1.1，每條連線獨立限速突破）
- 🎵 **MP3 轉檔**：下載後自動解碼 → LAME 編碼成真 MP3（可選 128／192／320 kbps），寫入 ID3 歌名／歌手／資料夾標籤
- 📁 **資料夾一鍵下載**：每個資料夾（含「全部」總覽的卡片）都有「下載全部」，只下載尚未下載的曲目
- 🎧 **音樂庫**：資料夾卡片牆 + 資料夾內曲目清單、長按多選
- 🔀 **播放模式**：順序 / 循環 / 隨機 / 單曲重複（每個資料夾各自記憶）
- 🎤 **KTV 錄音**、**懸浮迷你播放器**、**三指點擊播放/暫停**、**分享整合**

## 技術

- Kotlin + Jetpack Compose（Material 3，自訂深色「極光」主題）
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5（解析搜尋／串流）
- OkHttp 4.12（下載引擎，多連線分段）
- MediaCodec 解碼 + [jump3r](https://github.com/Sciss/jump3r)（純 Java LAME）編碼 → MP3，無需 NDK
- 下載／轉檔期間使用 dataSync 前景服務，切去其他 App 亦不會被中斷

## 建置

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

需要 Android SDK（`local.properties` 指向本機 SDK 路徑）。

> 僅供個人學習與備份使用。請遵守 YouTube 服務條款與當地法律，尊重著作權。
