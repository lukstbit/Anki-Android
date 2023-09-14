plugins {
    // Gradle plugin portal
    id 'com.github.triplet.play' version '3.8.4'
    id 'com.android.application'
    id 'app.brant.amazonappstorepublisher'
    id 'idea'
    id 'kotlin-android'
    id 'kotlin-parcelize'
    id 'org.jetbrains.kotlin.plugin.serialization'
}

repositories {
    google()
    mavenCentral()
    maven { url "https://jitpack.io" }
}


idea {
    module {
        downloadJavadoc = System.getenv("CI") != "true"
        downloadSources = System.getenv("CI") != "true"
    }
}

def homePath = System.properties['user.home']
/**
 * @return the current git hash
 * @example edf739d95bad7b370a6ed4398d46723f8219b3cd
 */
static def gitCommitHash() {
    "git rev-parse HEAD".execute().text.trim()
}

android {
    namespace "com.ichi2.anki"

    compileSdk 33 // change api compileSdkVersion at the same time

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        applicationId "com.ichi2.anki"
        buildConfigField "Boolean", "CI", (System.getenv("CI") == "true").toString()
        buildConfigField "String", "ACRA_URL", '"https://ankidroid.org/acra/report"'
        buildConfigField "String", "BACKEND_VERSION", "\"$ankidroid_backend_version\""
        buildConfigField "Boolean", "ENABLE_LEAK_CANARY", "false"
        buildConfigField "Boolean", "ALLOW_UNSAFE_MIGRATION", "false"
        buildConfigField "String", "GIT_COMMIT_HASH", "\"${gitCommitHash()}\""
        resValue "string", "app_name", "AnkiDroid"

        // The version number is of the form:
        // <major>.<minor>.<maintenance>[dev|alpha<build>|beta<build>|]
        // The <build> is only present for alpha and beta releases (e.g., 2.0.4alpha2 or 2.0.4beta4), developer builds do
        // not have a build number (e.g., 2.0.4dev) and official releases only have three components (e.g., 2.0.4).
        //
        // The version code is derived from the version name as follows:
        // AbbCCtDD
        // A: 1-digit decimal number representing the major version
        // bb: 2-digit decimal number representing the minor version
        // CC: 2-digit decimal number representing the maintenance version
        // t: 1-digit decimal number representing the type of the build
        // 0: developer build
        // 1: alpha release
        // 2: beta release
        // 3: public release
        // DD: 2-digit decimal number representing the build
        // 00 for internal builds and public releases
        // alpha/beta build number for alpha/beta releases
        //
        // This ensures the correct ordering between the various types of releases (dev < alpha < beta < release) which is
        // needed for upgrades to be offered correctly.
        versionCode=21700101
        versionName="2.17alpha1"
        minSdk 23
        // change api/build.gradle
        // change robolectricDownloader.gradle
        // After #13695: change .tests_emulator.yml
        // noinspection OldTargetApi
        targetSdk 33
        testApplicationId "com.ichi2.anki.tests"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner 'com.ichi2.testutils.NewCollectionPathTestRunner'
    }
    signingConfigs {
        release {
            storeFile file("${homePath}/src/android-keystore")
            keyAlias "nrkeystorealias"
            storePassword System.getenv("KSTOREPWD")
            keyPassword System.getenv("KEYPWD")
        }
    }
    buildTypes {
        debug {
            versionNameSuffix "-debug"
            debuggable true
            applicationIdSuffix ".debug"
            splits.abi.universalApk = true // Build universal APK for debug always
            // Check Crash Reports page on developer wiki for info on ACRA testing
            // buildConfigField "String", "ACRA_URL", '"https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acra-ankidroid/_design/acra-storage/_update/report"'
            if (project.rootProject.file('local.properties').exists()) {
                Properties localProperties = new Properties()
                localProperties.load(project.rootProject.file('local.properties').newDataInputStream())
                // #6009 Allow optional disabling of JaCoCo for general build (assembleDebug).
                // jacocoDebug task was slow, hung, and wasn't required unless I wanted coverage
                testCoverageEnabled localProperties['enable_coverage'] != "false"
                // not profiled: optimization for build times
                if (localProperties['enable_languages'] == "false") {
                    android.defaultConfig.resConfigs "en"
                }
                // allows the scoped storage migration when the user is not logged in
                if (localProperties["allow_unsafe_migration"] != null) {
                    buildConfigField "Boolean", "ALLOW_UNSAFE_MIGRATION", localProperties["allow_unsafe_migration"]
                }
                // allow disabling leak canary
                if (localProperties["enable_leak_canary"] != null) {
                    buildConfigField "Boolean", "ENABLE_LEAK_CANARY", localProperties["enable_leak_canary"]
                } else {
                    buildConfigField "Boolean", "ENABLE_LEAK_CANARY", "true"
                }
            } else {
                testCoverageEnabled true
            }

            // make the icon red if in debug mode
            resValue 'color', 'anki_foreground_icon_color_0', "#FFFF0000"
            resValue 'color', 'anki_foreground_icon_color_1', "#FFFF0000"
            resValue "string", "applicationId", "${defaultConfig.applicationId}${applicationIdSuffix}"
        }
        release {
            minifyEnabled true
            splits.abi.universalApk = universalApkEnabled // Build universal APK for release with `-Duniversal-apk=true`
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release

            // syntax: assembleRelease -PcustomSuffix="suffix" -PcustomName="New name"
            if (project.hasProperty("customSuffix")) {
                // the suffix needs a '.' at the start
                applicationIdSuffix project.property("customSuffix").replaceFirst(/^\.*/, ".")
                resValue "string", "applicationId", "${defaultConfig.applicationId}${applicationIdSuffix}"
            } else {
                resValue "string", "applicationId", defaultConfig.applicationId
            }
            if (project.hasProperty("customName")) {
                resValue "string", "app_name", project.property("customName")
            }

            resValue 'color', 'anki_foreground_icon_color_0', "#FF29B6F6"
            resValue 'color', 'anki_foreground_icon_color_1', "#FF0288D1"
        }
    }

    /**
     * Product Flavors are used for Amazon App Store and Google Play Store.
     * This is because we cannot use Camera Permissions in Amazon App Store (for FireTv etc...)
     * Therefore, different AndroidManifest for Camera Permissions is used in Amazon flavor.
     */
    flavorDimensions "appStore"
    productFlavors {
        play {
            dimension "appStore"
        }
        amazon {
            dimension "appStore"
        }
        // A 'full' build has no restrictions on storage/camera. Distributed on GitHub/F-Droid
        full {
            dimension "appStore"
        }
    }

    /**
     * Set this to true to create five separate APKs instead of one:
     *   - 2 APKs that only work on ARM/ARM64 devices
     *   - 2 APKs that only works on x86/x86_64 devices
     *   - a universal APK that works on all devices
     * The advantage is the size of most APKs is reduced by about 2.5MB.
     * Upload all the APKs to the Play Store and people will download
     * the correct one based on the CPU architecture of their device.
     */
    def enableSeparateBuildPerCPUArchitecture = true

    splits {
        abi {
            reset()
            enable enableSeparateBuildPerCPUArchitecture
            //universalApk enableUniversalApk  // set in debug + release config blocks above
            include "armeabi-v7a", "x86", "arm64-v8a", "x86_64"
        }
    }
    // applicationVariants are e.g. debug, release
    applicationVariants.all { variant ->
        // We want the same version stream for all ABIs in debug but for release we can split them
        if (variant.buildType.name == 'release') {
            variant.outputs.all { output ->

                // For each separate APK per architecture, set a unique version code as described here:
                // https://developer.android.com/studio/build/configure-apk-splits.html
                def versionCodes = ["armeabi-v7a": 1, "x86": 2, "arm64-v8a": 3, "x86_64": 4]
                def outputFile = output.outputFile
                if (outputFile != null && outputFile.name.endsWith('.apk')) {
                    def abi = output.getFilter("ABI")
                    if (abi != null) {  // null for the universal-debug, universal-release variants
                        //  From: https://developer.android.com/studio/publish/versioning#appversioning
                        //  "Warning: The greatest value Google Play allows for versionCode is 2100000000"
                        //  AnkiDroid versionCodes have a budget 8 digits (through AnkiDroid 9)
                        //  This style does ABI version code ranges with the 9th digit as 0-4.
                        //  This consumes ~20% of the version range space, w/50 years of versioning at our major-version pace
                        output.versionCodeOverride =
                                // ex:  321200106 = 3 * 100000000 + 21200106
                                versionCodes.get(abi) * 100000000 + defaultConfig.versionCode
                    }
                }
            }
        }
    }

    testOptions {
        animationsDisabled true
        kotlinOptions {
            freeCompilerArgs += [
                    '-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi',
            ]
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
        coreLibraryDesugaringEnabled true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11
    }

    packagingOptions {
        resources {
            excludes += ['META-INF/DEPENDENCIES']
        }
    }
}

play {
    serviceAccountCredentials.set(file("${homePath}/src/AnkiDroid-GCP-Publish-Credentials.json"))
    track.set('alpha')
}

amazon {
    securityProfile = file("${homePath}/src/AnkiDroid-Amazon-Publish-Security-Profile.json")
    applicationId = "amzn1.devportal.mobileapp.524a424d314931494c55383833305539"
    pathToApks = [ file("./build/outputs/apk/amazon/release/AnkiDroid-amazon-universal-release.apk") ]
    replaceEdit = true
}

// Install Git pre-commit hook for Ktlint
task installGitHook(type: Copy) {
    from new File(rootProject.rootDir, 'pre-commit')
    into { new File(rootProject.rootDir, '.git/hooks') }
    fileMode 0755
}
tasks.getByPath(':AnkiDroid:preBuild').dependsOn installGitHook

// Issue 11078 - some emulators run, but run zero tests, and still report success
task assertNonzeroAndroidTests() {
    doLast {
        // androidTest currently creates one .xml file per emulator with aggregate results in this dir
        File folder = file("./build/outputs/androidTest-results/connected/flavors/play")
        File[] listOfFiles = folder.listFiles({ d, f -> f ==~ /.*.xml/ } as FilenameFilter)
        for (File file : listOfFiles) {
            // The aggregate results file currently contains a line with this pattern holding test count
            String[] matches = file.readLines().findAll { it.contains('<testsuite') }
            if (matches.length != 1) {
                throw new GradleScriptException("Unable to determine count of tests executed for " + file.name + ". Regex pattern out of date?", null)
            }
            if (!(matches[0] ==~ /.* tests="\d+" .*/) || matches[0].contains('tests="0"')) {
                throw new GradleScriptException("androidTest executed 0 tests for " + file.name + " - Probably a bug with the emulator. Try another image.", null)
            }
        }
    }
}
afterEvaluate {
    tasks.getByPath(':AnkiDroid:connectedPlayDebugAndroidTest').finalizedBy(assertNonzeroAndroidTests)
}

apply from: "./robolectricDownloader.gradle"
apply from: "./jacoco.gradle"
apply from: "../lint.gradle"

dependencies {
    configurations.all {
        resolutionStrategy {
            force 'org.jetbrains:annotations:24.0.1'
        }
    }
    api project(":api")
    lintChecks project(":lint-rules")
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.3'

    compileOnly 'org.jetbrains:annotations:24.0.1'
    compileOnly "com.google.auto.service:auto-service-annotations:1.1.1"
    annotationProcessor "com.google.auto.service:auto-service:1.1.1"

    implementation 'androidx.activity:activity-ktx:1.7.2'
    implementation 'androidx.annotation:annotation:1.6.0'
    implementation 'androidx.appcompat:appcompat:1.7.0-alpha02'
    implementation 'androidx.browser:browser:1.5.0'
    implementation "androidx.core:core-ktx:1.10.1"
    implementation 'androidx.exifinterface:exifinterface:1.3.6'
    implementation "androidx.fragment:fragment-ktx:$fragments_version"
    debugImplementation("androidx.fragment:fragment-testing-manifest:$fragments_version")
    implementation "androidx.preference:preference-ktx:1.2.1"
    implementation 'androidx.recyclerview:recyclerview:1.3.1'
    implementation 'androidx.sqlite:sqlite-framework:2.3.1'
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
    implementation 'androidx.viewpager2:viewpager2:1.0.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.webkit:webkit:1.7.0'
    // Note: the design support library can be quite buggy, so test everything thoroughly before updating it
    // Changing the version from 1.8.0 to 1.7.0 because the item in navigation drawer is getting bold unnecessarily
    implementation 'com.google.android.material:material:1.7.0'
    implementation 'com.vanniktech:android-image-cropper:4.5.0'
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0"

    // Backend libraries

    implementation 'com.google.protobuf:protobuf-kotlin-lite:3.24.2' // This is required when loading from a file

    Properties localProperties = new Properties()
    if (project.rootProject.file('local.properties').exists()) {
        localProperties.load(project.rootProject.file('local.properties').newDataInputStream())
    }
    if (localProperties['local_backend'] == "true") {
        implementation files("../../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar")
        testImplementation files("../../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar")
    } else {
        implementation "io.github.david-allison-1:anki-android-backend:$ankidroid_backend_version"
        testImplementation "io.github.david-allison-1:anki-android-backend-testing:$ankidroid_backend_version"
    }

    // May need a resolution strategy for support libs to our versions
    implementation "ch.acra:acra-http:$acra_version"
    implementation "ch.acra:acra-dialog:$acra_version"
    implementation "ch.acra:acra-toast:$acra_version"
    implementation "ch.acra:acra-limiter:$acra_version"

    implementation 'com.afollestad.material-dialogs:core:3.3.0'
    implementation 'com.afollestad.material-dialogs:input:3.3.0'
    // noinspection GradleDependency - commons-compress 1.12 - later versions use `File.toPath`; API26 can remove?
    implementation 'org.apache.commons:commons-compress:1.12'
    implementation 'org.apache.commons:commons-collections4:4.4' // SetUniqueList
    implementation 'commons-io:commons-io:2.13.0' // FileUtils.contentEquals
    implementation 'net.mikehardy:google-analytics-java7:2.0.13'
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.arcao:slf4j-timber:3.1'
    implementation 'com.jakewharton.timber:timber:5.0.1'
    implementation 'org.jsoup:jsoup:1.16.1'
    implementation "com.github.zafarkhaja:java-semver:0.9.0" // For AnkiDroid JS API Versioning
    implementation 'com.drakeet.drawer:drawer:1.0.3'
    implementation 'uk.co.samuelwall:material-tap-target-prompt:3.3.2'
    implementation 'com.github.mrudultora:Colorpicker:1.2.0'
    implementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    implementation "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
    implementation 'com.github.ByteHamster:SearchPreference:2.4.0'
    implementation 'com.github.AppIntro:AppIntro:6.3.1'

    // Cannot use debugImplementation since classes need to be imported in AnkiDroidApp
    // and there's no no-op version for release build. Usage has been disabled for release
    // build via AnkiDroidApp.
    implementation 'com.squareup.leakcanary:leakcanary-android:2.12'

    // A path for a testing library which provide Parameterized Test
    testImplementation "org.junit.jupiter:junit-jupiter:$junit_version"
    testImplementation "org.junit.jupiter:junit-jupiter-params:$junit_version"
    testImplementation "org.junit.vintage:junit-vintage-engine:$junit_version"
    testImplementation 'org.mockito:mockito-inline:5.2.0'
    testImplementation "org.mockito.kotlin:mockito-kotlin:5.1.0"
    testImplementation "org.hamcrest:hamcrest:$hamcrest_version"
    testImplementation 'net.lachlanmckee:timber-junit-rule:1.0.1'
    // robolectricDownloader.gradle *may* need a new SDK jar entry if they release one or if we change targetSdk. Instructions in that gradle file.
    testImplementation "org.robolectric:robolectric:$robolectric_version"
    testImplementation "androidx.test:core:$androidx_test_version"
    testImplementation "androidx.test.ext:junit:$androidx_test_junit_version"
    testImplementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    testImplementation "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
    testImplementation "org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version"
    testImplementation "io.mockk:mockk:1.13.7"
    testImplementation 'org.apache.commons:commons-exec:1.3' // obtaining the OS
    testImplementation("androidx.fragment:fragment-testing:$fragments_version")

    // May need a resolution strategy for support libs to our versions
    androidTestImplementation "androidx.test.espresso:espresso-core:$espresso_version"
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espresso_version") {
        exclude module: "protobuf-lite"
    }
    androidTestImplementation "androidx.test:core:$androidx_test_version"
    androidTestImplementation "androidx.test.ext:junit:$androidx_test_junit_version"
    androidTestImplementation "androidx.test:rules:$androidx_test_version"
    androidTestImplementation "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
    androidTestImplementation "org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version"
    androidTestImplementation("androidx.fragment:fragment-testing:$fragments_version")
}
