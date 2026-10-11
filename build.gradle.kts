import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin { jvmToolchain(25) }

repositories {
    maven("https://repo1.maven.org/maven2")
    intellijPlatform { defaultRepositories() }
}

val jflex: Configuration by configurations.creating

dependencies {
    jflex("de.jflex:jflex:1.9.1")
    intellijPlatform {
        // Use the installed WebStorm when localIde is set (gradle.properties), otherwise download the target version.
        val localIde = providers.gradleProperty("localIde").orNull
        if (localIde != null) local(localIde) else webstorm(providers.gradleProperty("platformVersion"))
        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.css")
        bundledModule("intellij.platform.lsp")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "262" }
        changeNotes = """
            <p><b>0.1.0</b> — first release.</p>
            <ul>
              <li>Svelte 5 and Svelte 4 highlighting, folding and auto-close for blocks.</li>
              <li>svelte-language-server and typescript-svelte-plugin integration.</li>
              <li>Template ↔ script navigation, find usages and rename.</li>
              <li>SvelteKit route awareness: tab titles, icons, file nesting, related files.</li>
              <li>Hover docs for runes, blocks and directives; Svelte 4 syntax hints with migrate fixes.</li>
            </ul>
        """.trimIndent()
    }
    // gradlew verifyPlugin — checks binary compatibility and internal-API use against the IDE we build with.
    pluginVerification {
        ides { current() }
    }
    buildSearchableOptions = false
    // No Swing forms or @NotNull runtime checks needed; avoids the instrumenter hunting for a JDK layout.
    instrumentCode = false
}

// ---- Lexer generation (JFlex + IntelliJ skeleton) ----
val generatedLexerDir = layout.buildDirectory.dir("generated/lexer")

val generateLexer by tasks.registering(JavaExec::class) {
    val flexFile = file("src/main/grammar/_SvelteLexer.flex")
    val skeleton = file("src/main/grammar/idea-flex.skeleton")
    val outDir = generatedLexerDir.map { it.dir("io/github/tsessebe/svelte/lexer") }
    inputs.files(flexFile, skeleton)
    outputs.dir(generatedLexerDir)
    classpath = jflex
    mainClass.set("jflex.Main")
    args("--skel", skeleton.absolutePath, "--nobak", "-q", "-d", outDir.get().asFile.absolutePath, flexFile.absolutePath)
    doLast {
        // Stock JFlex emits the char[] overload; the IntelliJ skeleton uses a CharSequence buffer.
        val gen = outDir.get().file("_SvelteLexer.java").asFile
        gen.writeText(gen.readText().replace(
            "Character.codePointAt(zzBufferL, zzCurrentPosL, zzEndReadL)",
            "Character.codePointAt(zzBufferL, zzCurrentPosL)"))
    }
}

sourceSets.main { java.srcDir(generatedLexerDir) }
tasks.compileJava { dependsOn(generateLexer) }
tasks.compileKotlin { dependsOn(generateLexer) }

tasks.test {
    // Optional corpus of real .svelte files (not committed): -PsvelteCorpus=/path
    providers.gradleProperty("svelteCorpus").orNull?.let { systemProperty("svelte.corpus", it) }
}
