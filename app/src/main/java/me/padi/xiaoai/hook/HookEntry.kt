package me.padi.xiaoai.hook

import android.app.Application
import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.injectModuleAppResources
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
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
            }
        }
        loadApp("com.xiaomi.aischedule") {
            loadHooker(MainHook)
        }
    }
}