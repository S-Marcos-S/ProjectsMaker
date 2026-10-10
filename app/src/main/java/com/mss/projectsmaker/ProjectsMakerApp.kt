package com.mss.projectsmaker

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ProjectsMakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
