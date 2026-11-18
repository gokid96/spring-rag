// Spring RAG.  ./gradlew build  ·  ./gradlew bootRun --args='--spring.profiles.active=ollama'
plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.ragstarter"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "2.0.1"
extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	// 모델 연결: OpenAI 호환 API (외부 API · Bifrost · Ollama · vLLM). 설정은 application-*.yml
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
	implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
	implementation("org.springframework.ai:spring-ai-rag")
	// 문서 읽기: md · txt · html 직접, pdf 는 PDFBox
	// docx · pptx · xlsx · hwp: 아래 줄 주석 해제 (Apache Tika, jar 수십 MB 증가). FolderDocumentSource 가 자동 사용
	implementation("org.springframework.ai:spring-ai-pdf-document-reader")
	// implementation("org.springframework.ai:spring-ai-tika-document-reader")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
	useJUnitPlatform()
	defaultCharacterEncoding = "UTF-8"
}

tasks.jar {
	enabled = false
}
