
plugins {
  java
  id("com.vanniktech.maven.publish") version "0.33.0"
  id("com.diffplug.spotless") version "7.0.4"
}

// Global properties for all modules
group = "io.github.higher-kinded-j"
version = project.findProperty("projectVersion")?.toString() ?: "0.1.6-SNAPSHOT"



// Configure all submodules
subprojects {
  // Apply necessary plugins to each submodule
  plugins.apply("java-library")
  plugins.apply("com.diffplug.spotless")

  group = rootProject.group
  version = rootProject.version

  // Set Java version for all submodules
  java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
  }

  repositories {
    mavenCentral()
  }


  version = rootProject.version

  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
  // Apply Spotless configuration to all java sources in subprojects
  apply(plugin = "com.diffplug.spotless")
  spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
      target("src/**/*.java")
      googleJavaFormat("1.27.0").reflowLongStrings().formatJavadoc(true)
      removeUnusedImports()
      trimTrailingWhitespace()
      licenseHeaderFile(rootProject.file("config/spotless/copyright.txt"), "(package|import|public|@)")
    }
  }
}

