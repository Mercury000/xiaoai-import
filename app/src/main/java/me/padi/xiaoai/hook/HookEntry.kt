package com.mercury.xiaoaiimport.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.kongzue.dialogx.DialogX
import com.kongzue.dialogx.style.MIUIStyle
import top.sacz.xphelper.XpHelper


@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    companion object {
        val prefs get() = XpHelper.context.prefs()
    }

    override fun onInit() = configs {
        debugLog {
            tag = "XiaoAiKeBiao"
        }
    }

    override fun onHook() = encase {
        onAppLifecycle {
            onCreate {
                XpHelper.moduleApkPath = moduleAppFilePath
                XpHelper.initContext(this)
                XpHelper.injectResourcesToContext(this)
                DialogX.init(this)
                DialogX.globalTheme = DialogX.THEME.AUTO
                DialogX.globalStyle = MIUIStyle()
            }
        }
        loadApp("com.miui.voiceassist") {
            loadHooker(VoiceAssistHook)
            loadHooker(WebViewHook)
        }
    }
}