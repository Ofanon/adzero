import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read from a file that is not in git, so the key and its passwords never end
// up in the repository. Absent on a fresh clone: debug builds still work, only
// release signing is skipped.
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.adzero.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adzero.app"
        minSdk = 24
        targetSdk = 36
        // versionCode is what the store compares to decide an update is an
        // update; it only ever goes up, and never goes back down for any
        // reason. versionName is what a human reads.
        versionCode = 3
        versionName = "1.1"
    }

    signingConfigs {
        if (signing.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    // What lands in the download is what somebody reads before tapping it.
    // "app-release.apk" says nothing and looks like a build artefact that
    // escaped.
    //
    // The version used to be in the name, and it had to come out. GitHub serves
    // a permanent link to the newest release at
    //
    //   /releases/latest/download/<file name>
    //
    // which resolves only if that file name never changes. With the version in
    // it, the update banner's address had to be edited by hand at every
    // release — one more thing to get wrong on the day everything else is
    // already being changed. The version is in the release title, in the app,
    // and in the installer screen; it does not need to be in the file name too.
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "AdZero.apk"
        }
    }

    buildTypes {
        release {
            // Left off deliberately, and it is a release decision rather than
            // an oversight. R8 shrinks an app that has no dependencies by
            // almost nothing, while the packing and renaming it produces is
            // exactly the shape antivirus heuristics score against — and a
            // clean VirusTotal is the one thing the store asks for. Readable
            // bytecode is also what lets anyone check the app does what it
            // says, which is the whole pitch.
            isMinifyEnabled = false
            if (signing.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Volontairement aucune dependance : l'interface est construite en code,
    // ce qui evite d'embarquer AppCompat/Material pour trois boutons.
    //
    // testImplementation est la seule exception, et elle n'en est pas une :
    // JUnit sert a compiler et lancer les tests sur la machine de developpement.
    // Rien de tout cela n'entre dans l'APK, donc l'argument "aucune dependance
    // tierce" — celui qui rend le binaire lisible pour un analyste et le
    // dossier F-Droid propre — reste exact.
    testImplementation("junit:junit:4.13.2")
}
