package io.github.tsessebe.svelte.service

import com.intellij.lang.typescript.lsp.restartTypeScriptServicesAsync
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager

object SvelteServices {
  /** Restarts the Svelte language server and the TypeScript service (picks up settings changes). */
  fun restart(project: Project) {
    if (project.isDefault) return
    LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(SvelteLspIntegrationProvider::class.java)
    restartTypeScriptServicesAsync(project)
  }
}

/** Tools | Restart Svelte Language Server */
class RestartSvelteServerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let(SvelteServices::restart)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
