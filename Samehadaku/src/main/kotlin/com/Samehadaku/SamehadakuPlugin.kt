package com.Samehadaku

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SamehadakuPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Samehadaku())
    }
}