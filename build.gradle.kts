plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "de.flog99"
version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val mapguiVersion = providers.gradleProperty("mapguiVersion").get()
val paperVersion = providers.gradleProperty("paperVersion").get()

dependencies {
    // compileOnly on both: the API arrives at runtime from the MapGUI plugin the server owner installed, and
    // shading either in would put a second copy on the classpath.
    compileOnly("io.github.flog99:mapgui-api:$mapguiVersion")
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

    testImplementation("io.github.flog99:mapgui-api:$mapguiVersion")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.add("-Xlint:all,-serial,-processing")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<ProcessResources>().configureEach {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version, "apiVersion" to "26.2")
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// The pack a server hands to its players. Zipped rather than left loose so it can be dropped straight into a
// server's resource-pack hosting, and built next to the jar so the two versions never drift apart.
val resourcePack = tasks.register<Zip>("resourcePack") {
    group = "distribution"
    description = "Zips the resource pack that gives the camera and film their models"

    from(layout.projectDirectory.dir("resourcepack"))
    archiveFileName = "MapCamera-resourcepack-$version.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")

    // Reproducible, because the plugin hands the client this file's SHA-1 and a client caches against it. A
    // zip carrying build timestamps hashes differently every build, so every restart would re-download a pack
    // whose contents had not changed.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// The same zip, inside the jar, so a server that turns pack serving on has nothing to host or upload.
tasks.processResources {
    from(resourcePack) {
        into("pack")
        // Version-less inside the jar, so PackServer can name it as a constant.
        rename { "MapCamera-resourcepack.zip" }
    }
}

tasks.named("build") {
    dependsOn(resourcePack)
}

// A test server with this plugin and the MapGUI sitting next to it, so an unreleased MapGUI can be tried
// through a real consumer before it goes out.
//
// The jar comes from the sibling checkout's own shadowJar rather than from a repository, which is the whole
// point: it is the working copy that is being tested, not a published version of it. The task in the
// included build is depended on directly, so `runServer` here builds MapGUI there first.
val mapguiRoot = file("../MapGUI")
val mapguiPluginJar = fileTree(mapguiRoot.resolve("mapgui-plugin/build/libs")) {
    // The shadow jar alone. The thin one is named for the module and would be missing the layout engine,
    // the camera and the version backend.
    include("MapGUI-*.jar")
}

tasks.runServer {
    minecraftVersion("26.2")
    pluginJars.from(mapguiPluginJar)

    if (mapguiRoot.resolve("settings.gradle.kts").isFile) {
        dependsOn(gradle.includedBuild("MapGUI").task(":mapgui-plugin:shadowJar"))
    }
}

// ./gradlew mockup - renders the camera back to build/mockup.png with no server involved, which is the only
// practical way to look at a shutter that is over in a fifth of a second.
tasks.register<JavaExec>("mockup") {
    group = "mapcamera"
    description = "Renders every stage of the viewfinder to one PNG"

    mainClass = "de.flog99.mapcamera.Mockup"
    classpath = sourceSets["test"].runtimeClasspath
    args(layout.buildDirectory.file("mockup.png").get().asFile.path)
}
