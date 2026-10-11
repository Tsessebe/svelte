package io.github.tsessebe.svelte.service

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import io.github.tsessebe.svelte.settings.SvelteSettings
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Locates (or installs) svelte-language-server and builds its command line.
 *
 * Lookup order:
 *  1. the project's node_modules (project root, or a sub-folder up to two levels down, e.g. a WebApp folder in a solution);
 *  2. a plugin-managed copy in the IDE system directory, installed with the project's npm on first use.
 */
object SvelteLanguageServer {
  /** Pinned version for the managed install. Peer: typescript ^5.9.2 || ^6.0.2. */
  const val VERSION = "0.18.4"

  private const val PACKAGE = "svelte-language-server"
  private val SERVER_SCRIPT = listOf("bin", "server.js")

  private val managedRoot: File get() = File(PathManager.getSystemPath(), "svelte-language-server/$VERSION")

  fun createCommandLine(project: Project): GeneralCommandLine {
    val node = nodeExecutable(project)
    val script = customServer(project) ?: findProjectServer(project) ?: managedServer(project, node)
    return GeneralCommandLine(node.path, script.path, "--stdio")
      .withCharset(StandardCharsets.UTF_8)
      .withWorkDirectory(project.basePath)
  }

  /** Human-readable description of which server would be used (for the settings page). Never installs. */
  fun describe(project: Project): String {
    SvelteSettings.getInstance(project).serverPath?.let { return "Custom: $it" }
    findProjectServer(project)?.let { return "Project: ${it.parentFile.parentFile.path}" }
    val managed = serverScript(File(managedRoot, "node_modules/$PACKAGE"))
    return if (managed.isFile) "Managed $VERSION: ${managed.parentFile.parentFile.path}"
    else "Managed $VERSION (installed with npm when a .svelte file is first opened)"
  }

  private fun customServer(project: Project): File? {
    val path = SvelteSettings.getInstance(project).serverPath ?: return null
    val f = File(path)
    val script = if (f.isDirectory) serverScript(f) else f
    if (!script.isFile) throw ExecutionException("Svelte: custom server not found at ${script.path} (Settings | Languages & Frameworks | TypeScript | Svelte)")
    return script
  }

  private fun nodeExecutable(project: Project): File {
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter as? NodeJsLocalInterpreter
      ?: throw ExecutionException("Svelte: no local Node.js interpreter configured (Settings | Languages & Frameworks | Node.js)")
    return File(interpreter.interpreterSystemDependentPath)
  }

  private fun findProjectServer(project: Project): File? {
    val base = project.basePath?.let(::File) ?: return null
    val candidates = sequence {
      yield(base)
      base.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "node_modules" }?.forEach { child ->
        yield(child)
        child.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "node_modules" }?.forEach { yield(it) }
      }
    }
    return candidates.map { serverScript(File(it, "node_modules/$PACKAGE")) }.firstOrNull { it.isFile }
  }

  private fun serverScript(packageDir: File): File = SERVER_SCRIPT.fold(packageDir) { dir, part -> File(dir, part) }

  /** Installs the pinned server once into the system directory (runs on the LSP start thread, not the EDT). */
  @Synchronized
  private fun managedServer(project: Project, node: File): File {
    val script = serverScript(File(managedRoot, "node_modules/$PACKAGE"))
    if (script.isFile) return script

    notify(project, "Installing $PACKAGE $VERSION (first use only)…", NotificationType.INFORMATION)
    managedRoot.mkdirs()
    val npm = npmFor(node)
    val cmd = GeneralCommandLine(npm.path, "install", "--no-audit", "--no-fund", "--prefix", managedRoot.path, "$PACKAGE@$VERSION")
      .withWorkDirectory(managedRoot)
      .withEnvironment("PATH", node.parentFile.path + File.pathSeparator + (System.getenv("PATH") ?: ""))
    val output = CapturingProcessHandler(cmd).runProcess(10 * 60 * 1000)
    if (output.exitCode != 0 || !script.isFile) {
      thisLogger().warn("npm install $PACKAGE failed (${output.exitCode}):\n${output.stdout}\n${output.stderr}")
      notify(project, "Could not install $PACKAGE: ${output.stderr.lines().lastOrNull { it.isNotBlank() } ?: "exit ${output.exitCode}"}", NotificationType.ERROR)
      throw ExecutionException("npm install $PACKAGE failed")
    }
    notify(project, "$PACKAGE $VERSION installed.", NotificationType.INFORMATION)
    return script
  }

  private fun npmFor(node: File): File {
    val dir = node.parentFile
    val names = if (SystemInfo.isWindows) listOf("npm.cmd", "npm.exe") else listOf("npm")
    return names.map { File(dir, it) }.firstOrNull { it.isFile }
      ?: throw ExecutionException("Svelte: npm not found next to ${node.path}")
  }

  private fun notify(project: Project, text: String, type: NotificationType) {
    NotificationGroupManager.getInstance().getNotificationGroup("Svelte").createNotification(text, type).notify(project)
  }
}
