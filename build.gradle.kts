

plugins {
    // Apply the java-library plugin to add support for Java Library
    `java-library`
}

group = "edu.sc.seis"
version = "4.0.0-SNAPSHOT"

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
    maven(url = "http://www.seis.sc.edu/software/maven2")
}

dependencies {
  implementation("edu.sc.seis:sod-model:4.0.0-SNAPSHOT")
  implementation("javamailUSC:javamail:1.3.2")
  implementation("activationUSC:activation:1.0.2")
  implementation("com.isti:isti.util:20120201")
  testImplementation(project(":sod-mock"))
}

configurations.all {
  resolutionStrategy.dependencySubstitution {
    substitute(module("edu.sc.seis:sod-model")).with(project(":sod-model"))
    substitute(module("edu.sc.seis:seisFile")).with(project(":seisFile"))
  }
}
