plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdk = 36
    namespace="lu.knaff.alain.saf_sftp"
    defaultConfig {
        applicationId = "lu.knaff.alain.saf_sftp"
        minSdk = 26
	// Min SDK Version is greater than 24, in order to be able to disable v1 signing
	// Min SDK Version is 26, for ProxyFileDescriptorCallback
        targetSdk = 36

	// version code is supposed to be 2 digits of major, 2 digits
	// of minor, and 2 digits of 3rd part. As major is still 0, and
	// as no leading zeroes are represented, 200 is what we get for
	// 0.2
        versionCode = 224
        versionName = "0.2.24"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

	javaCompileOptions {
 	    annotationProcessorOptions {
		arguments += mapOf(
		    "room.schemaLocation" to "$projectDir/schemas".toString()
		)
	    }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
			  "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
	compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
    }
    lint {
        checkAllWarnings = true // Checks all lint warnings
        // warningsAsErrors = true // Treats all warnings as errors
    }
    gradle.projectsEvaluated {
	tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.addAll(
		listOf("-Xlint:unchecked", "-Xlint:deprecation")
            )
	}}

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
        }
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // https://mvnrepository.com/artifact/commons-net/commons-net
    //compile "commons-net:commons-net:+"
    // https://mvnrepository.com/artifact/it.sauronsoftware/ftp4j
    implementation("com.github.mwiede:jsch:2.28.6")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")

    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.work:work-runtime:2.11.2")
}
