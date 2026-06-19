package com.example.zwl

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class ZwlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja fabryki grafiki Mapsforge wymagana do poprawnego renderowania mapy
        AndroidGraphicFactory.createInstance(this)
    }
}
