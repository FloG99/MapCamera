plugins {
    // Lets Gradle fetch the JDK 25 toolchain itself, so a fresh clone builds without one installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MapCamera"

// A checkout of MapGUI next to this one is substituted for the published mapgui-api, so working on both at
// once needs no publishing step. Without it the coordinates in build.gradle.kts resolve normally, from
// mavenLocal or Central.
val mapgui = file("../MapGUI")
if (mapgui.resolve("settings.gradle.kts").isFile) {
    includeBuild(mapgui)
}
