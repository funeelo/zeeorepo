package com.Otakudesu

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OtakudesuPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Otakudesu())
        registerExtractorAPI(DesuStream())
    }
}