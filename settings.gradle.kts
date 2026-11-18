pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// JDK 25 없으면 자동 다운로드 (build.gradle.kts toolchain)
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "spring-rag"
