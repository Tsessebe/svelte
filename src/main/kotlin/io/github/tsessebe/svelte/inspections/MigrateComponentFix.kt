package io.github.tsessebe.svelte.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Rewrites the whole component to Svelte 5 using the project's own `svelte/compiler` `migrate()` — the same
 * migration as `npx sv migrate svelte-5`. Undo (Ctrl+Z) reverts it in one step.
 */
class MigrateComponentFix : LocalQuickFix {
  override fun getFamilyName(): String = "Migrate this component to Svelte 5 (svelte/compiler migrate)"

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.Html("Runs the Svelte compiler's <code>migrate()</code> on this file and replaces its content.")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val psiFile = descriptor.psiElement?.containingFile ?: return
    val vFile = psiFile.virtualFile ?: return
    val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
    val source = document.text

    val outcome = ProgressManager.getInstance().runProcessWithProgressSynchronously<Pair<String?, String?>, Exception>(
      { runMigrate(project, File(vFile.path), source) }, "Migrating ${vFile.name} to Svelte 5", true, project)
    val result = outcome.first
    if (result == null) {
      NotificationGroupManager.getInstance().getNotificationGroup("Svelte")
        .createNotification("Svelte 5 migration failed: ${outcome.second}", NotificationType.ERROR).notify(project)
      return
    }

    WriteCommandAction.runWriteCommandAction(project, "Migrate to Svelte 5", null, {
      document.setText(result)
    }, psiFile)
  }

  /** (migrated code, null) or (null, error message). */
  private fun runMigrate(project: Project, file: File, source: String): Pair<String?, String?> {
    val node = (NodeJsInterpreterManager.getInstance(project).interpreter as? NodeJsLocalInterpreter)?.interpreterSystemDependentPath
      ?: return null to "no local Node.js interpreter configured"
    val input = File.createTempFile("svelte-migrate", ".svelte").apply { writeText(source, StandardCharsets.UTF_8); deleteOnExit() }
    // `node -e` has no script path: extra args start at process.argv[1].
    val script = """
      const path = require('path'), fs = require('fs');
      const compiler = require(require.resolve('svelte/compiler', { paths: [process.argv[1]] }));
      if (!compiler.migrate) { console.error('svelte/compiler has no migrate() (needs Svelte 5)'); process.exit(2); }
      const res = compiler.migrate(fs.readFileSync(process.argv[2], 'utf8'), { filename: process.argv[3] });
      process.stdout.write(res.code);
    """.trimIndent()
    val cmd = GeneralCommandLine(node, "-e", script, file.parent, input.path, file.name)
      .withCharset(StandardCharsets.UTF_8)
      .withWorkDirectory(file.parentFile)
    val out = CapturingProcessHandler(cmd).runProcess(60_000)
    input.delete()
    if (out.exitCode != 0 || out.stdout.isBlank()) {
      val err = out.stderr.lines().firstOrNull { it.contains("Error") } ?: out.stderr.lines().firstOrNull { it.isNotBlank() }
      return null to (err ?: "migrate() failed (exit ${out.exitCode})")
    }
    return out.stdout to null
  }
}
