@file:kotlin.Suppress("unsupported")
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.*
import java.nio.file.Paths

val isCI = !System.getenv("CI").isNullOrBlank()
val commitHash = kotlin.run {
	val process: Process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
	process.waitFor()
	val output = process.inputStream.use {
		process.inputStream.use { it.readBytes().let(::String) }
	}
	process.destroy()
	output.trim()
}

val pluginComingVersion = "0.5.0"
val pluginVersion = if (isCI) "$pluginComingVersion-$commitHash" else pluginComingVersion
val packageName = "org.ice1000.julia"

group = packageName
version = pluginVersion

plugins {
	java
	id("org.jetbrains.intellij") version "1.8.0"
	id("org.jetbrains.grammarkit") version "2021.1.3"
	kotlin("jvm") version "1.5.32"
}

fun fromToolbox(root: String, ide: String) = file(root)
	.resolve(ide)
	.takeIf { it.exists() }
	?.resolve("ch-0")
	?.listFiles()
	.orEmpty()
	.filterNotNull()
	.filter { it.isDirectory }
	.filterNot { it.name.endsWith(".plugins") }
	.maxBy {
		val (major, minor, patch) = it.name.split('.')
		String.format("%5s%5s%5s", major, minor, patch)
	}
	?.also { println("Picked: $it") }

allprojects {
	apply { plugin("org.jetbrains.grammarkit") }
}

//grammarKit {
//	grammarKitRelease = "7aecfcd72619e9c241866578e8312f339b4ddbd8"
//}

intellij {
	version.set("2020.3.4")
	updateSinceUntilBuild.set(false)
	instrumentCode.set(true)
	if (!isCI) {
		plugins.set(listOf("PsiViewer:203-SNAPSHOT", "java"))
		type.set("IU")
		downloadSources.set(true)
	} else {
		plugins.set(listOf("java"))
		//version = "2020.3"
	}
	val user = System.getProperty("user.name")
	val os = System.getProperty("os.name")
	val root = when {
		os.startsWith("Windows") -> "C:\\Users\\$user\\AppData\\Local\\JetBrains\\Toolbox\\apps"
		os == "Linux" -> "/home/$user/.local/share/JetBrains/Toolbox/apps"
		else -> return@intellij
	}
	val intellijPath = ["IDEA-C", "IDEA-U"]
		.mapNotNull { fromToolbox(root, it) }.firstOrNull()
	intellijPath?.absolutePath?.let { localPath.set(it) }
	val pycharmPath = ["PyCharm-C", "IDEA-C", "IDEA-U"]
		.mapNotNull { fromToolbox(root, it) }.firstOrNull()
	//pycharmPath?.absolutePath?.let { alternativeIdePath = it }
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<PatchPluginXmlTask>().configureEach {
	//changeNotes(file("docs/change-notes.html").readText())
	//pluginDescription(file("docs/description.html").readText())
	//version(pluginVersion)
	//pluginId(packageName)
}

sourceSets {
	main {
		withConvention(KotlinSourceSet::class) {
			listOf(java, kotlin).forEach { it.srcDirs("src", "gen") }
		}
		resources.srcDir("res")
	}

	test {
		withConvention(KotlinSourceSet::class) {
			listOf(java, kotlin).forEach { it.srcDirs("test") }
		}
		resources.srcDir("testData")
	}
}

repositories {
	mavenCentral()
	//maven("https://dl.bintray.com/jetbrains/markdown/")
}

dependencies {
	compile(kotlin("stdlib-jdk8"))
	compile(group = "org.eclipse.mylyn.github", name = "org.eclipse.egit.github.core", version = "2.1.5") {
		exclude(module = "gson")
	}
	compile("org.jetbrains", "markdown", "0.3.1")
	testCompile(kotlin(module = "test-junit"))
	testCompile(group = "junit", name = "junit", version = "4.12")
}

tasks.register("displayCommitHash") {
	group = "help"
	description = "Display the newest commit hash"
	doFirst { println("Commit hash: $commitHash") }
}

tasks.register("isCI") {
	group = "help"
	description = "Check if it's running in a continuous-integration"
	doFirst { println(if (isCI) "Yes, I'm on a CI." else "No, I'm not on CI.") }
}

// Don't specify type explicitly. Will be incorrectly recognized
val parserRoot = Paths.get("org", "ice1000", "julia", "lang")
val lexerRoot = Paths.get("gen", "org", "ice1000", "julia", "lang")
fun path(more: Iterable<*>) = more.joinToString(File.separator)
fun bnf(name: String) = Paths.get("grammar", "$name-grammar.bnf").toString()
fun flex(name: String) = Paths.get("grammar", "$name-lexer.flex").toString()

val genParser = tasks.register<GenerateParser>("genParser") {
	group = "code generation"
	description = "Generate the Parser and PsiElement classes"
	source = bnf("julia")
	targetRoot = "gen/"
	pathToParser = path(parserRoot + "JuliaParser.java")
	pathToPsiRoot = path(parserRoot + "psi")
	purgeOldFiles = true
}

val genLexer = tasks.register<GenerateLexer>("genLexer") {
	group = "code generation"
	description = "Generate the Lexer"
	source = flex("julia")
	targetDir = path(lexerRoot)
	targetClass = "JuliaLexer"
	purgeOldFiles = true
	dependsOn(genParser)
}

val genDocfmtParser = tasks.register<GenerateParser>("genDocfmtParser") {
	group = "code generation"
	description = "Generate the Parser for DocumentFormat.jl"
	source = bnf("docfmt")
	targetRoot = "gen/"
	val root = parserRoot + "docfmt"
	pathToParser = path(root + "DocfmtParser.java")
	pathToPsiRoot = path(root + "psi")
	purgeOldFiles = true
}

val genDocfmtLexer = tasks.register<GenerateLexer>("genDocfmtLexer") {
	group = "code generation"
	description = "Generate the Lexer for DocumentFormat.jl"
	source = flex("docfmt")
	targetDir = path(lexerRoot + "docfmt")
	targetClass = "DocfmtLexer"
	purgeOldFiles = true
	dependsOn(genDocfmtParser)
}

val cleanGenerated = tasks.register("cleanGenerated") {
	group = "clean"
	description = "Remove all generated codes"
	doFirst { delete("gen") }
}

val sortSpelling = tasks.register("sortSpellingFile") {
	val fileName = "spelling.txt"
	val isWindows = "windows" in System.getProperty("os.name").toLowerCase()
	project.exec {
		workingDir = file("$projectDir/res/org/ice1000/julia/lang/editing")
		commandLine = when {
			isWindows -> listOf("sort.exe", fileName, "/O", fileName)
			else -> listOf("sort", fileName, "-f", "-o", fileName)
		}
	}
}

tasks.withType<KotlinCompile>().configureEach {
	dependsOn(
		genParser,
		genLexer,
		genDocfmtParser,
		genDocfmtLexer,
		sortSpelling
	)
	kotlinOptions {
		jvmTarget = "11"
		languageVersion = "1.5"
		apiVersion = "1.5"
		freeCompilerArgs = listOf("-Xjvm-default=enable")
	}
}

tasks.withType<Delete>().configureEach { dependsOn(cleanGenerated) }
