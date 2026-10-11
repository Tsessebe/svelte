package io.github.tsessebe.svelte.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import io.github.tsessebe.svelte.lang.SvelteFileType
import io.github.tsessebe.svelte.settings.SvelteConfigurable
import io.github.tsessebe.svelte.settings.SvelteSettings

/** Starts svelte-language-server when a .svelte file is opened. */
class SvelteLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    // Tests open .svelte files in a temp project; a running Node process would lock that folder on Windows.
    if (ApplicationManager.getApplication().isUnitTestMode) return
    if (!SvelteSettings.getInstance(project).serviceEnabled) return
    if (file.fileType is SvelteFileType) {
      clientStarter.ensureClientStarted(SvelteLspClientDescriptor(project))
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
    LspClientWidgetItem(lspClient, currentFile, SvelteFileType.ICON, SvelteConfigurable::class.java)
}

class SvelteLspClientDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Svelte") {
  override fun isSupportedFile(file: VirtualFile): Boolean = file.fileType is SvelteFileType

  override fun createCommandLine(): GeneralCommandLine = SvelteLanguageServer.createCommandLine(project)
}
