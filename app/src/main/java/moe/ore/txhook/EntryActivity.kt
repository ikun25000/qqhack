package moe.ore.txhook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import moe.ore.android.AndroKtx
import moe.ore.android.dialog.Dialog
import moe.ore.txhook.app.MainActivity
import kotlin.system.exitProcess

class EntryActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroKtx.isInit = true

        Dialog.CommonAlertBuilder(this)
            .setCancelable(false)
            .setTitle("使用警告")
            .setMessage("该软件仅提供学习与交流使用，切勿应用于违法领域，并请在24小时内删除！")
            .setPositiveButton("同意") { dialog, _ ->
                dialog.dismiss()
                gotoMain()
            }
            .setNegativeButton("不同意") { _, _ ->
                exitProcess(1)
            }
            .show()
    }

    private fun gotoMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
