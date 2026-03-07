package com.example.hook;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XiaoaiHooker {

    private static volatile Context sAppContext = null;
    private static final AtomicBoolean sDeviceIdActiveFired = new AtomicBoolean(false);
    private static final AtomicBoolean sEducationActiveFired = new AtomicBoolean(false);
    private static final AtomicBoolean sTokenActiveFired = new AtomicBoolean(false);

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {

        // Application.onCreate: cache context + delayed active calls
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sAppContext = (Application) param.thisObject;
                            XposedBridge.log("[XiaoaiHook] MODULE ALIVE | process=" + lpparam.processName);
                            // getDeviceId is static, call immediately
                            if (sDeviceIdActiveFired.compareAndSet(false, true)) {
                                activeCallGetDeviceId(lpparam, sAppContext);
                            }
                            // Delay 2s to let app finish init, then force-create instances
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    forceCallEducationHelper(lpparam);
                                    forceCallOauthToken(lpparam);
                                }
                            }, 2000);
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Application.onCreate hook failed: " + t);
        }

        // Hook 1 passive: EducationHelper.b()
        try {
            XposedHelpers.findAndHookMethod(
                    "com.xiaomi.voiceassistant.instruction.utils.EducationHelper",
                    lpparam.classLoader,
                    "b",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object result = param.getResult();
                            XposedBridge.log("[XiaoaiHook] EducationHelper.b() => "
                                    + (result != null ? result.toString() : "null"));
                        }
                    }
            );
            XposedBridge.log("[XiaoaiHook] Hook 1 passive registered.");
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            XposedBridge.log("[XiaoaiHook] Hook 1 passive FAILED: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Hook 1 passive FAILED: " + t);
        }

        // Hook 1 constructor: capture instance and actively call b()
        try {
            Class<?> eduClass = XposedHelpers.findClass(
                    "com.xiaomi.voiceassistant.instruction.utils.EducationHelper",
                    lpparam.classLoader);
            XposedBridge.hookAllConstructors(eduClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!sEducationActiveFired.compareAndSet(false, true)) return;
                    final Object instance = param.thisObject;
                    XposedBridge.log("[XiaoaiHook] EducationHelper constructed, calling b()...");
                    try {
                        Object result = XposedHelpers.callMethod(instance, "b");
                        XposedBridge.log("[XiaoaiHook] EducationHelper.b() [active/ctor] => "
                                + (result != null ? result.toString() : "null"));
                    } catch (Throwable t) {
                        XposedBridge.log("[XiaoaiHook] EducationHelper.b() ctor call error: " + t);
                    }
                }
            });
            XposedBridge.log("[XiaoaiHook] Hook 1 constructor(all) registered.");
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Hook 1 constructor FAILED: " + t.getMessage());
        }

        // Hook 2 passive: c30.b.getOauthV2AccessToken(boolean)
        try {
            XposedHelpers.findAndHookMethod(
                    "c30.b",
                    lpparam.classLoader,
                    "getOauthV2AccessToken",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object result = param.getResult();
                            XposedBridge.log("[XiaoaiHook] c30.b.getOauthV2AccessToken() => "
                                    + (result != null ? result.toString() : "null"));
                        }
                    }
            );
            XposedBridge.log("[XiaoaiHook] Hook 2 passive registered.");
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            XposedBridge.log("[XiaoaiHook] Hook 2 passive FAILED: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Hook 2 passive FAILED: " + t);
        }

        // Hook 2 constructor: capture instance and actively call getOauthV2AccessToken(false)
        try {
            Class<?> c30bClass = XposedHelpers.findClass("c30.b", lpparam.classLoader);
            XposedBridge.hookAllConstructors(c30bClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!sTokenActiveFired.compareAndSet(false, true)) return;
                    final Object instance = param.thisObject;
                    XposedBridge.log("[XiaoaiHook] c30.b constructed, calling getOauthV2AccessToken(false)...");
                    try {
                        Object result = XposedHelpers.callMethod(instance, "getOauthV2AccessToken", false);
                        XposedBridge.log("[XiaoaiHook] c30.b.getOauthV2AccessToken() [active/ctor] => "
                                + (result != null ? result.toString() : "null"));
                    } catch (Throwable t) {
                        XposedBridge.log("[XiaoaiHook] c30.b.getOauthV2AccessToken() ctor call error: " + t);
                    }
                }
            });
            XposedBridge.log("[XiaoaiHook] Hook 2 constructor(all) registered.");
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Hook 2 constructor FAILED: " + t.getMessage());
        }

        // Hook 3 passive: q70.j.getDeviceId(Context) - deduplicated
        try {
            XposedHelpers.findAndHookMethod(
                    "q70.j",
                    lpparam.classLoader,
                    "getDeviceId",
                    Context.class,
                    new XC_MethodHook() {
                        private final AtomicBoolean printed = new AtomicBoolean(false);
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!printed.compareAndSet(false, true)) return;
                            Object result = param.getResult();
                            XposedBridge.log("[XiaoaiHook] q70.j.getDeviceId() => "
                                    + (result != null ? result.toString() : "null"));
                        }
                    }
            );
            XposedBridge.log("[XiaoaiHook] Hook 3 registered.");
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            XposedBridge.log("[XiaoaiHook] Hook 3 FAILED: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] Hook 3 FAILED: " + t);
        }
    }

    /** Force-create EducationHelper instance via reflection and call b() */
    private static void forceCallEducationHelper(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!sEducationActiveFired.compareAndSet(false, true)) return;
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.xiaomi.voiceassistant.instruction.utils.EducationHelper",
                    lpparam.classLoader);
            // Try no-arg constructor first
            Object instance = null;
            try {
                instance = XposedHelpers.newInstance(cls);
            } catch (Throwable e1) {
                // Try with Context arg
                try {
                    instance = XposedHelpers.newInstance(cls, sAppContext);
                } catch (Throwable e2) {
                    XposedBridge.log("[XiaoaiHook] EducationHelper newInstance failed: " + e2);
                }
            }
            if (instance != null) {
                Object result = XposedHelpers.callMethod(instance, "b");
                XposedBridge.log("[XiaoaiHook] EducationHelper.b() [active/forced] => "
                        + (result != null ? result.toString() : "null"));
            }
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] forceCallEducationHelper failed: " + t);
        }
    }

    /** Force-create c30.b instance via reflection and call getOauthV2AccessToken(false) */
    private static void forceCallOauthToken(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!sTokenActiveFired.compareAndSet(false, true)) return;
        try {
            Class<?> cls = XposedHelpers.findClass("c30.b", lpparam.classLoader);
            Object instance = null;
            try {
                instance = XposedHelpers.newInstance(cls);
            } catch (Throwable e1) {
                try {
                    instance = XposedHelpers.newInstance(cls, sAppContext);
                } catch (Throwable e2) {
                    XposedBridge.log("[XiaoaiHook] c30.b newInstance failed: " + e2);
                }
            }
            if (instance != null) {
                Object result = XposedHelpers.callMethod(instance, "getOauthV2AccessToken", false);
                XposedBridge.log("[XiaoaiHook] c30.b.getOauthV2AccessToken() [active/forced] => "
                        + (result != null ? result.toString() : "null"));
            }
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] forceCallOauthToken failed: " + t);
        }
    }

    private static void activeCallGetDeviceId(XC_LoadPackage.LoadPackageParam lpparam, Context ctx) {
        try {
            Object result = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("q70.j", lpparam.classLoader),
                    "getDeviceId", ctx);
            XposedBridge.log("[XiaoaiHook] q70.j.getDeviceId() [active] => "
                    + (result != null ? result.toString() : "null"));
        } catch (Throwable t) {
            XposedBridge.log("[XiaoaiHook] q70.j.getDeviceId() active call failed: " + t);
        }
    }
}