plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "ai.lazycode.kinetic.anim"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    api(project(":engine"))
    implementation(libs.androidx.core.ktx)
    api(libs.lottie)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.github.soumyasethy"
                artifactId = "kinetic-anim"
                version = "0.4.0"
            }
        }
    }
}
