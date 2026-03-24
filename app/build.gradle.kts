import com.android.build.api.variant.impl.VariantOutputImpl
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.compose)
}

data class VersionInfo(
    val code: Int,
    val name: String,
    val displayName: String,
)

fun dateStamp(): String = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

val versionPropsFile = rootProject.file("version.properties")

fun loadVersionProps(): Properties = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}

fun saveVersionProps(props: Properties) {
    versionPropsFile.outputStream().use { props.store(it, null) }
}

fun generateVersion(buildType: String): VersionInfo {
    val props = loadVersionProps()
    val today = dateStamp()
    val prefix = if (buildType == "release") "RELEASE_" else "DEBUG_"

    val lastBuildDate = props.getProperty("${prefix}LAST_BUILD_DATE", "")
    val currentCount = props.getProperty("${prefix}BUILD_COUNT", "0").toIntOrNull() ?: 0
    val nextCount = if (lastBuildDate == today) currentCount + 1 else 1

    val versionCode = "${today}${nextCount.toString().padStart(2, '0')}".toInt()
    val versionName = versionCode.toString()
    val displayVersionName = if (buildType == "debug") "${versionName}_debug" else versionName

    props.setProperty("${prefix}LAST_BUILD_DATE", today)
    props.setProperty("${prefix}BUILD_COUNT", nextCount.toString())
    props.setProperty("${prefix}VERSION_CODE", versionCode.toString())
    props.setProperty("${prefix}VERSION_NAME", versionName)
    saveVersionProps(props)

    logger.lifecycle(">>> ${buildType.uppercase()} build: count=$nextCount")
    logger.lifecycle(">>> versionCode=$versionCode")
    logger.lifecycle(">>> versionName=$displayVersionName")

    return VersionInfo(versionCode, versionName, displayVersionName)
}

fun readExistingVersion(buildType: String): VersionInfo {
    val props = loadVersionProps()
    val prefix = if (buildType == "release") "RELEASE_" else "DEBUG_"
    val code = props.getProperty("${prefix}VERSION_CODE", "2000000001").toIntOrNull() ?: 2000000001
    val name = props.getProperty("${prefix}VERSION_NAME", code.toString())
    val display = if (buildType == "debug") "${name}_debug" else name
    return VersionInfo(code, name, display)
}

val taskNamesLower = gradle.startParameter.taskNames.map { it.lowercase() }
val isReleaseBuild = taskNamesLower.any { it.contains("release") }
val isDebugBuild = taskNamesLower.any { it.contains("debug") }

val releaseVersion = if (isReleaseBuild) generateVersion("release") else null
val debugVersion = if (isDebugBuild) generateVersion("debug") else null

val activeVersion = when {
    isReleaseBuild && releaseVersion != null -> releaseVersion
    isDebugBuild && debugVersion != null -> debugVersion
    else -> readExistingVersion("release")
}

val signingPropsFile = rootProject.file("build/config/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) {
        signingPropsFile.inputStream().use { load(it) }
    }
}

fun ensureReleaseSigning() {
    if (!isReleaseBuild) return
    if (!signingPropsFile.exists()) {
        throw GradleException("Missing signing config: build/config/signing.properties")
    }

    val storeFile = signingProps.getProperty("storeFile")
    val storePassword = signingProps.getProperty("storePassword")
    val keyAlias = signingProps.getProperty("keyAlias")
    val keyPassword = signingProps.getProperty("keyPassword")

    if (storeFile.isNullOrBlank() || !file(storeFile).exists() ||
        storePassword.isNullOrBlank() ||
        keyAlias.isNullOrBlank() ||
        keyPassword.isNullOrBlank()
    ) {
        throw GradleException("Invalid signing config in build/config/signing.properties")
    }
}

ensureReleaseSigning()

android {
    namespace = gropify.project.app.packageName
    compileSdk = 36

    defaultConfig {
        applicationId = gropify.project.app.packageName
        minSdk = gropify.project.android.minSdk
        targetSdk = gropify.project.android.targetSdk
        versionName = activeVersion.name
        versionCode = activeVersion.code
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (signingPropsFile.exists()) {
                val storeFileValue = signingProps.getProperty("storeFile")
                val storePasswordValue = signingProps.getProperty("storePassword")
                val keyAliasValue = signingProps.getProperty("keyAlias")
                val keyPasswordValue = signingProps.getProperty("keyPassword")

                if (!storeFileValue.isNullOrBlank()) {
                    storeFile = file(storeFileValue)
                }
                if (!storePasswordValue.isNullOrBlank()) {
                    storePassword = storePasswordValue
                }
                if (!keyAliasValue.isNullOrBlank()) {
                    keyAlias = keyAliasValue
                }
                if (!keyPasswordValue.isNullOrBlank()) {
                    keyPassword = keyPasswordValue
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (signingPropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            isMinifyEnabled = false
            versionNameSuffix = "_debug"
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

    lint {
        checkReleaseBuilds = false
    }

    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id", "--package-id", "0xf4"
    )

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.map { it as VariantOutputImpl }
            .forEach { output ->
                // Keep current project naming behavior (user requested not to copy reference name)
                output.outputFileName = "课表修复_v${output.versionName.get()}(${variant.name}).apk"
            }
    }
}

afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val buildType = variant.buildType.name
        variant.packageApplicationProvider.configure {
            outputDirectory.set(file("$rootDir/build/$buildType"))
        }
    }

    tasks.matching { it.name.startsWith("create") && it.name.endsWith("ApkListingFileRedirect") }.all {
        enabled = false
        println("Disabled task: ${name}")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions"
        )
    }
}

val syncThirdPartyNotices by tasks.registering(Copy::class) {
    val noticesFile = rootProject.file("THIRD_PARTY_NOTICES.md")
    doFirst {
        if (!noticesFile.exists()) {
            throw GradleException("Missing required file: ${noticesFile.absolutePath}")
        }
    }
    from(noticesFile)
    into(file("src/main/assets"))
    rename { "THIRD_PARTY_NOTICES.md" }
}

tasks.named("preBuild") {
    dependsOn(syncThirdPartyNotices)
}

dependencies {
    compileOnly(libs.rovo89.xposed.api)
    ksp(libs.yukihookapi.ksp.xposed)
    implementation(libs.yukihookapi)

    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)

    ksp(libs.hikage.compiler)
    implementation(libs.hikage.core)
    implementation(libs.hikage.extension)
    implementation(libs.hikage.widget.androidx)
    implementation(libs.hikage.widget.material)

    implementation(libs.betterandroid.ui.component)
    implementation(libs.betterandroid.ui.component.adapter)
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.betterandroid.system.extension)

    implementation(libs.drawabletoolbox)

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
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.8.0")

    implementation("androidx.navigation3:navigation3-runtime:1.1.0-alpha03")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.8.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-adaptive:0.8.0")

    implementation("io.github.kevinnzou:compose-webview:0.33.6")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")

    val dialogXVersion = "0.0.50.beta38"
    implementation("com.github.suzhelan.DialogX:DialogX:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXKongzueStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXMIUIStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXIOSStyle:$dialogXVersion")
    implementation("com.github.suzhelan.DialogX:DialogXMaterialYou:$dialogXVersion")

    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}
