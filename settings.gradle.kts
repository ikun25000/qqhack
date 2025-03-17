dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://api.xposed.info/")
            mavenContent {
                includeGroup("de.robv.android.xposed")
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // PhotoView
    }
}

rootProject.name = "TXHook"
include(":app")
