package moe.ore.txhook.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.czm.settingview.SettingView
import moe.ore.android.dialog.Dialog
import moe.ore.android.toast.Toast
import moe.ore.android.util.FuckSettingItem
import moe.ore.txhook.databinding.FragmentSettingBinding
import moe.ore.xposed.helper.ConfigPusher
import moe.ore.xposed.helper.ConfigPusher.KEY_FORBID_HTTP
import moe.ore.xposed.helper.ConfigPusher.KEY_PUSH_API
import moe.ore.xposed.helper.DataKind
import moe.ore.xposed.helper.DataPutter
import moe.ore.xposed.helper.SourceFinder

class SettingFragment: Fragment() {
    private lateinit var binding: FragmentSettingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSettingBinding.inflate(inflater, container, false).also {
        this.binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val addressText = binding.address
        addressText.text = ConfigPusher[KEY_PUSH_API].orEmpty().ifBlank { "未配置地址" }

        FuckSettingItem.setSwitchListener(binding.pushApi.also {
            if (ConfigPusher[KEY_PUSH_API].orEmpty().isNotEmpty())
                FuckSettingItem.turnSettingSwitch(it, true)
        }) {
            if ((it as SwitchCompat).isChecked) {
                Dialog.EditTextAlertBuilder(requireContext())
                    .setTitle("输入目标地址")
                    .setTextListener { text ->
                        val finalText = text?.takeIf { it.isNotBlank() } ?: "192.168.31.63:6779"
                        ConfigPusher[KEY_PUSH_API] = finalText.toString()
                        addressText.text = finalText
                        Toast.toast(requireContext(), "Push服务配置成功")
                        FuckSettingItem.turnSettingSwitch(binding.pushApi, true)
                    }
                    .setFloatingText("请输入你自己的Domain：")
                    .setHint("192.168.31.63:6779")
                    .setPositiveButton("确定") { dialog, _ ->
                        dialog.dismiss()
                    }.setOnCancelListener {
                        FuckSettingItem.turnSettingSwitch(binding.pushApi, false)
                    }
                    .show()
            } else {
                addressText.text = "未配置服务"
                ConfigPusher[KEY_PUSH_API] = ""
                Toast.toast(requireContext(), "Push服务已关闭")
            }
        }

        binding.forbidTcp.let {
            it.setmOnLSettingItemClick(object: SwitchSettingListener(it, ConfigPusher[KEY_FORBID_HTTP] == "yes", true) {
                override fun onClick(isChecked: Boolean) {
                    ConfigPusher[KEY_FORBID_HTTP] = if (isChecked) "yes" else "no"
                }
            })
        }

        binding.claerCache.let {
            it.setmOnLSettingItemClick {
                DataPutter.clear(DataKind.ECDH_PUBLIC)
                DataPutter.clear(DataKind.ECDH_SHARE)
                DataPutter.clear(DataKind.QLOG)
                DataPutter.clear(DataKind.WTLOGIN_LOG)
                DataPutter.clear(DataKind.MATCH_PACKAGE)
                SourceFinder.clear()
                Toast.toast(msg = "清理成功")
            }
        }
    }

    abstract class SwitchSettingListener(view: SettingView, value: Boolean = false, private val needRe: Boolean = false): SettingView.OnLSettingItemClick {
        // init { if (value) view.clickOn() }

        abstract fun onClick(isChecked: Boolean)

        override fun click(isChecked: Boolean) {
            this.onClick(isChecked)
            if (needRe) Toast.toast(msg = "需要重新启动QQ后生效")
        }
    }
}