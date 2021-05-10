import de.undercouch.gradle.tasks.download.Download

plugins {
    id("base")
    id("de.undercouch.download") version "4.1.1"
}

repositories {
    mavenCentral()
}

version = "0.0.1-SNAPSHOT"

val mavenComponents by configurations.creating

dependencies {
    mavenComponents("com.gradle:capture-published-build-scan-maven-extension:1.0.0-SNAPSHOT")
}

val argbashVersion by extra("2.10.0")

tasks.register<Download>("downloadArgbash") {
    group = "argbash"
    description = "Downloads Argbash."
    src("https://github.com/matejak/argbash/archive/refs/tags/${argbashVersion}.zip")
    dest(file("${buildDir}/argbash/argbash-${argbashVersion}.zip"))
    overwrite(false)
}

tasks.register<Copy>("unpackArgbash") {
    group = "argbash"
    description = "Unpacks the downloaded Argbash archive."
    from(zipTree(tasks.getByName("downloadArgbash").outputs.files.singleFile))
    into(layout.buildDirectory.dir("argbash"))
    dependsOn("downloadArgbash")
}

tasks.register<ApplyArgbash>("generateBashCliParsers") {
    group = "argbash"
    description = "Uses Argbash to generate Bash command line argument parsing code."

    scriptTemplates.set(fileTree("../scripts") {
        include("**/*-cli-parser.m4")
        exclude("gradle/.data/")
        exclude("maven/.data/")
    })

    supportingTemplates.set(fileTree("../scripts") {
        include("**/*.m4")
        exclude("gradle/.data/")
        exclude("maven/.data/")
    })

    argbashVersion.set(project.extra["argbashVersion"].toString())

    dependsOn("unpackArgbash")
}

tasks.register<Copy>("copyGradleScripts") {
    group = "build"
    description = "Copies gradle scripts to output directory."

    from(layout.projectDirectory.dir("../scripts/gradle")) {
        exclude(".data/")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.projectDirectory.dir("../scripts")) {
        include("lib/**")
        exclude("maven")
        exclude("lib/maven")
        exclude("**/*.m4")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(gradle.includedBuild("fetch-build-validation-data").projectDir.resolve("build/libs/fetch-build-validation-data-1.0.0-SNAPSHOT-all.jar")) {
        into("lib/")
    }
    from(layout.buildDirectory.dir("generated/scripts/lib/gradle")) {
        into("lib/gradle/")
    }
    into(layout.buildDirectory.dir("scripts/gradle"))
    dependsOn(gradle.includedBuild("fetch-build-validation-data").task(":shadowJar"))
    dependsOn("generateBashCliParsers")
}

tasks.register<Copy>("copyMavenScripts") {
    group = "build"
    description = "Copies the Maven experiment scripts to output directory."

    from(layout.projectDirectory.dir("../scripts/maven")) {
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
        exclude(".data/")
    }
    from(layout.projectDirectory.dir("../scripts/")) {
        include("lib/**")
        exclude("gradle")
        exclude("lib/gradle")
        exclude("**/*.m4")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.buildDirectory.dir("generated/scripts/lib/maven")) {
        into("lib/maven/")
    }
    from(gradle.includedBuild("fetch-build-validation-data").projectDir.resolve("build/libs/fetch-build-validation-data-1.0.0-SNAPSHOT-all.jar")) {
        into("lib/")
    }
    from(mavenComponents) {
        into("lib/maven/")
    }
    into(layout.buildDirectory.dir("scripts/maven"))
    dependsOn(gradle.includedBuild("fetch-build-validation-data").task(":shadowJar"))
    dependsOn("generateBashCliParsers")
}

tasks.register<Zip>("assembleGradleScripts") {
    group = "build"
    description = "Packages the Gradle experiment scripts in a zip archive."
    baseName = "gradle-enterprise-gradle-build-validation"
    archiveFileName.set("${baseName}.zip")

    from(layout.buildDirectory.dir("scripts/gradle")) {
        exclude("**/.data")
    }
    into(baseName)
    dependsOn("generateBashCliParsers")
    dependsOn("copyGradleScripts")
}

tasks.register<Zip>("assembleMavenScripts") {
    group = "build"
    description = "Packages the Maven experiment scripts in a zip archive."
    baseName = "gradle-enterprise-maven-build-validation"
    archiveFileName.set("${baseName}.zip")

    from(layout.buildDirectory.dir("scripts/maven")) {
        filter { line: String -> line.replace("/../lib", "/lib") }
        exclude("**/.data")
    }
    into(baseName)
    dependsOn("generateBashCliParsers")
    dependsOn("copyMavenScripts")
}

tasks.named("assemble") {
    dependsOn("assembleGradleScripts")
    dependsOn("assembleMavenScripts")
}

