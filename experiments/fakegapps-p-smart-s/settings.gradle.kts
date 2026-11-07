pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); maven("https://api.xposed.info"); mavenCentral() }
}
rootProject.name = "FakeGApps-PSmartS-Edition"
include(":module", ":probe", ":diagnostics")
