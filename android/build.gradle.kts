import org.gradle.api.GradleException

plugins {
    id("com.android.application")
}

// Release signing is opt-in. Never put credentials or keystores in this repository.
val signingEnvironmentNames = listOf(
    "ATARI_KEYSTORE_PATH",
    "ATARI_KEYSTORE_PASSWORD",
    "ATARI_KEY_ALIAS",
    "ATARI_KEY_PASSWORD",
)
val signingEnvironment = signingEnvironmentNames.associateWith {
    providers.environmentVariable(it).orNull.orEmpty()
}
val signingRequested = signingEnvironment.values.any { it.isNotBlank() }
val releaseSigningConfigured = signingEnvironment.values.all { it.isNotBlank() }
if (signingRequested && !releaseSigningConfigured) {
    val missing = signingEnvironment.filterValues { it.isBlank() }.keys
    throw GradleException("Incomplete release signing configuration. Missing: ${missing.joinToString()}")
}
if (releaseSigningConfigured) {
    val keystore = file(signingEnvironment.getValue("ATARI_KEYSTORE_PATH")).canonicalFile
    if (!keystore.isFile || keystore.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
        throw GradleException("ATARI_KEYSTORE_PATH must point to an existing keystore outside the repository.")
    }
}

android {
    namespace = "com.midoali.atari"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.midoali.atari"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(signingEnvironment.getValue("ATARI_KEYSTORE_PATH"))
                storePassword = signingEnvironment.getValue("ATARI_KEYSTORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("ATARI_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("ATARI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // AGP supplies the local Android debug signing configuration.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    // ROMs are excluded by default. Enable packaging only for files you may distribute.
    val bundleRomsValue = providers.gradleProperty("bundleRoms").orElse("false").get()
    if (bundleRomsValue !in setOf("true", "false")) {
        throw GradleException("bundleRoms must be either true or false.")
    }
    val bundledRomsRequested = bundleRomsValue == "true"
    val generatedAssets = layout.buildDirectory.dir("generated/atari/assets")
    val prepareBundledRoms = tasks.register<Sync>("prepareBundledRoms") {
        into(generatedAssets.map { it.dir("roms") })
        if (bundledRomsRequested) {
            from(rootProject.layout.projectDirectory.dir("roms")) {
                include("*")
                exclude { element ->
                    !element.file.isFile ||
                        element.file.extension.lowercase() !in setOf("a26", "bin", "a78")
                }
            }
        }
        doFirst {
            if (bundledRomsRequested) {
                val roms = rootProject.file("roms").listFiles().orEmpty().filter {
                    it.isFile && it.extension.lowercase() in setOf("a26", "bin", "a78")
                }
                if (roms.isEmpty()) {
                    throw GradleException("bundleRoms=true requires at least one .a26, .bin, or .a78 file in roms/.")
                }
            }
        }
    }
    // Register the asset root, not the task's individual output files (which would flatten roms/).
    sourceSets.getByName("main").assets.srcDir(prepareBundledRoms.map { generatedAssets.get() })
    tasks.named("preBuild") {
        dependsOn(prepareBundledRoms)
    }
}

base {
    archivesName = "atari-android"
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "A signed release requires ATARI_KEYSTORE_PATH, ATARI_KEYSTORE_PASSWORD, " +
                    "ATARI_KEY_ALIAS, and ATARI_KEY_PASSWORD. See docs/BUILD_APK.md. " +
                    "For an installable development build, use exportDebugApk instead.",
            )
        }
    }
}

// Stable, universal APK outputs (no ABI split: this scaffold contains no native libraries).
androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val capitalizedName = variantName.replaceFirstChar { it.uppercase() }
        tasks.register<Copy>("export${capitalizedName}Apk") {
            group = "distribution"
            description = "Build and export the $variantName APK to artifacts/apk/."
            dependsOn("assemble$capitalizedName")
            if (variantName == "release") {
                dependsOn(verifyReleaseSigning)
            }
            from(layout.buildDirectory.file("outputs/apk/$variantName/atari-android-$variantName.apk"))
            into(rootProject.layout.projectDirectory.dir("artifacts/apk"))
            rename { "atari-android-$variantName.apk" }
            doFirst {
                if (variantName == "release" && !releaseSigningConfigured) {
                    throw GradleException("Release signing is not configured; see verifyReleaseSigning.")
                }
            }
            doLast {
                val exported = rootProject.file("artifacts/apk/atari-android-$variantName.apk")
                check(exported.isFile && exported.length() > 0) { "APK export failed: $exported" }
                logger.lifecycle("Exported APK: ${exported.absolutePath}")
            }
        }
    }
}

// Refuse to produce an unsigned release: every exported release must be installable.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}
