package com.lumo.player

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lumo.player.data.FnApi

class App : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { FnApi.httpClient() }
            .crossfade(true)
            .build()
}
