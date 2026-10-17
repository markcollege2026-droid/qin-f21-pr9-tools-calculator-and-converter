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
    }
}

rootProject.name = "qin-f21-toolbox"

include(":app")
include(":core:common")
include(":core:conversion")
include(":core:calc")
include(":core:hardware")
include(":core:data")
include(":core:design")
