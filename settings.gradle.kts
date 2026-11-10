pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 실제 제출 시 Pleos SDK 저장소 추가:
        // maven { url = uri("https://developers.pleos.ai/maven") }
    }
}
rootProject.name = "FateWindow"
include(":app")
