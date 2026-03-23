import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = gropify.project.app.packageName
    compileSdk = 36

    defaultConfig {
        applicationId = gropify.project.app.packageName
        minSdk = gropify.project.android.minSdk
        targetSdk = gropify.project.android.targetSdk
        versionName = gropify.project.app.versionName
        versionCode = gropify.project.app.versionCode
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }

        androidComponents {
            onVariants(selector().all()) { variant ->
                variant.outputs.map { it as com.android.build.api.variant.impl.VariantOutputImpl }
                    .forEach { output ->
                        output.outputFileName =
                            "课表修复_v${output.versionName.get()}(${variant.name}).apk"
                    }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    lint { checkReleaseBuilds = false }

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id", "--package-id", "0xf4"
    )
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions"
        )
    }
}

dependencies {
    compileOnly(libs.rovo89.xposed.api)
    ksp(libs.yukihookapi.ksp.xposed)
    implementation(libs.yukihookapi)

    // Optional: KavaRef (https://github.com/HighCapable/KavaRef)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)

    // Optional: Hikage (https://github.com/BetterAndroid/Hikage)
    ksp(libs.hikage.compiler)
    implementation(libs.hikage.core)
    implementation(libs.hikage.extension)
    implementation(libs.hikage.widget.androidx)
    implementation(libs.hikage.widget.material)

    // Optional: BetterAndroid (https://github.com/BetterAndroid/BetterAndroid)
    implementation(libs.betterandroid.ui.component)
    implementation(libs.betterandroid.ui.component.adapter)
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.betterandroid.system.extension)

    implementation(libs.drawabletoolbox)
    implementation(libs.dexkit)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation("com.github.suzhelan:XpHelper:3.0")

    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.0")
    // 可选：添加 miuix-icons 以获取更多图标
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.8.0")

    implementation("androidx.navigation3:navigation3-runtime:1.1.0-alpha03")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.8.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-adaptive:0.8.0")

    implementation("io.github.kevinnzou:compose-webview:0.33.6")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")

    val dialogXVersion = "0.0.50.beta38"
    //引入DialogX主体
    implementation("com.github.suzhelan.DialogX:DialogX:$dialogXVersion")
    //非必须 DialogX官方提供的主题样式
    implementation("com.github.suzhelan.DialogX:DialogXKongzueStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXMIUIStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXIOSStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXMaterialYou:$dialogXVersion")

    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}
