pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (in-app YouTube highlights) and Tesseract4Android (scoreboard OCR)
        // are only published on JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
                includeGroup("cz.adaptech.tesseract4android")
            }
        }
    }
}

rootProject.name = "OpenTV"
include(":app")
