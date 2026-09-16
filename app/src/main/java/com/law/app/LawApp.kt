package com.law.app

import android.app.Application
import com.law.app.data.local.LawDatabase
import com.law.app.data.repository.LawRepository
import com.law.app.util.LawDownloadManager

/**
 * Application 类：初始化数据库、仓库与下载管理器单例
 */
class LawApp : Application() {

    val database by lazy { LawDatabase.getDatabase(this) }
    val repository by lazy { LawRepository(dao = database.lawDao()) }
    val downloadManager by lazy { LawDownloadManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        downloadManager.destroy()
    }

    companion object {
        lateinit var instance: LawApp
            private set
    }
}
