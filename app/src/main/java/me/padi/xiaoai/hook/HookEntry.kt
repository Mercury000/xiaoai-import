package me.padi.xiaoai.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.kongzue.dialogx.DialogX
import com.kongzue.dialogx.style.MIUIStyle
import top.sacz.xphelper.XpHelper


@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

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
        loadApp("com.xiaomi.aischedule") {
            loadHooker(MainHook)
        }
    }
}