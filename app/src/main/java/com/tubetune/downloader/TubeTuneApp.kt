package com.tubetune.downloader

import android.app.Application
import com.tubetune.downloader.data.YtDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

class TubeTuneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val loc = Localization.fromLocalizationCode("zh-TW").orElse(Localization.DEFAULT)
            NewPipe.init(YtDownloader(), loc)
        } catch (t: Throwable) {
            try {
                NewPipe.init(YtDownloader(), Localization.DEFAULT)
            } catch (t2: Throwable) {
            }
        }
    }
}
