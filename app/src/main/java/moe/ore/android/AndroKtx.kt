package moe.ore.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.Environment.DIRECTORY_DOCUMENTS
import java.io.File
import kotlin.properties.Delegates

@SuppressLint("StaticFieldLeak")
object AndroKtx {
    var isInit: Boolean = false
    var dataDir: String by Delegates.observable("") { _, _, new: String ->
        File(new).let { if (!it.exists()) it.mkdirs() }
    }
    var pDataDir: String by Delegates.observable("") { _, _, new: String ->
        File(new).let { if (!it.exists()) it.mkdirs() }
    }
    lateinit var context: Context

    fun init(context: Context) {
        this.dataDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS).absolutePath + File.separator + "txhook"
        this.pDataDir = context.filesDir .absolutePath + File.separator + "txhook"
        this.context = context
    }
}