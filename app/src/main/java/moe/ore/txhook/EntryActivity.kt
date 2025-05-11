package moe.ore.txhook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import moe.ore.android.AndroKtx
import moe.ore.android.dialog.Dialog
import moe.ore.txhook.app.MainActivity
import kotlin.system.exitProcess

class EntryActivity: Activity() {
    companion object {
        private const val PREFS_NAME = "TXHookPrefs"
        private const val KEY_AGREED_TO_TERMS = "agreed_to_terms"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasAgreedToTerms = prefs.getBoolean(KEY_AGREED_TO_TERMS, false)

        if (hasAgreedToTerms) {
            AndroKtx.isInit = true
            gotoMain()
        } else {
            Dialog.CommonAlertBuilder(this)
                .setCancelable(false)
                .setTitle("使用警告")
                .setMessage("该软件仅供学习与交流使用，切勿用于违法领域，并请在24小时内删除！\n\n 由于本软件的性质，使用本软件可能导致您的账号被封禁！继续使用则代表您已知晓该风险行为！\n\n 如果您同意以上内容，请点击“同意”按钮，否则请点击“不同意”按钮并立即删除本软件！")
                .setPositiveButton("同意") { dialog, _ ->
                    dialog.dismiss()
                    saveUserAgreedToTerms()
                    AndroKtx.isInit = true
                    gotoMain()
                }
                .setNegativeButton("不同意") { _, _ ->
                    exitProcess(1)
                }
                .show()
        }
    }

    private fun gotoMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun saveUserAgreedToTerms() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AGREED_TO_TERMS, true) }
    }
}
