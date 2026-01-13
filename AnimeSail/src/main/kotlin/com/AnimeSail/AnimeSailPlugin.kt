package com.AnimeSail

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeSailPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeSail())
        registerExtractorAPI(Mp4Upload())
    }
}