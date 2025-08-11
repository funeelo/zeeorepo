package com.Anoboy

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnoboyPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Anoboy())
    }
}