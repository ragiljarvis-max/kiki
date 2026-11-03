package net.garybully.kiki.logs

import android.app.Application
import net.garybully.kiki.BuildConfig
import net.garybully.kiki.ILog
import com.tans.tlog.LogLevel
import com.tans.tlog.tLog
import java.io.File

object AndroidLog : ILog {

    @Volatile private var log: tLog? = null

    fun init(application: Application) {
        log = tLog.Companion.Builder(baseDir = File(application.getExternalFilesDir(null), "AppLog"))
            .setMaxSize(30L * 1024L * 1024L) // 30MB
            .setLogFilterLevel(if (BuildConfig.DEBUG) LogLevel.Debug else LogLevel.Error)
            .build()
    }

    override fun d(tag: String, msg: String) {
        log?.d(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        log?.e(tag, msg, throwable)
    }

}