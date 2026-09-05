import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = 37
    namespace="lu.knaff.alain.saf_sftp"
    defaultConfig {
        applicationId = "lu.knaff.alain.saf_sftp"
        minSdk = 26
	// Min SDK Version is greater than 24, in order to be able to disable v1 signing
	// Min SDK Version is 26, for ProxyFileDescriptorCallback

	//noinspection OldTargetApi
        targetSdk = 36
	// we cannot yet go to 37, due to additional permissions
	// needed to access hosts on local network, and cumbersome
	// error reporting associated with this

	// version code is supposed to be 2 digits of major, 2 digits
	// of minor, and 2 digits of 3rd part. As major is still 0, and
	// as no leading zeroes are represented, 200 is what we get for
	// 0.2
        versionCode = 226
        versionName = "0.2.26"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
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
            jvmTarget.set(JvmTarget.JVM_21)
	}
    }
    lint {
        checkAllWarnings = true // Checks all lint warnings
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
    implementation(libs.androidx.room.rt)
    ksp(libs.androidx.room.ksp)
    
    implementation(libs.jsch)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.bcprov.jdk18on)

    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
}
