package com.Kuramanime

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class KuramanimePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Kuramanime())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(KuramaDrive())
    }
}