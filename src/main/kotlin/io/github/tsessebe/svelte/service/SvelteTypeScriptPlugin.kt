package io.github.tsessebe.svelte.service

import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceSimpleCommand
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.lang.typescript.compiler.languageService.frameworks.DownloadableTypeScriptServicePlugin
import com.intellij.lang.typescript.compiler.languageService.frameworks.PluggableTypeScriptService
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.ConfigurePluginRequest
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.ConfigurePluginRequestArguments
import com.intellij.lang.typescript.languageService.TypeScriptServiceProvider
import com.intellij.lang.typescript.lsp.LspServerPackageDescriptor
import com.intellij.lang.typescript.lsp.PackageVersion
import com.intellij.lang.typescript.lsp.ServiceActivationHelper
import com.intellij.lang.typescript.lsp.TSPluginActivationRule
import com.intellij.lang.typescript.lsp.TSPluginLoader
import com.intellij.lang.typescript.lsp.createPackage
import com.intellij.lang.typescript.lsp.defaultPackageKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.github.tsessebe.svelte.lang.SvelteFileType
import io.github.tsessebe.svelte.settings.SvelteSettings

/*
 * Loads typescript-svelte-plugin into WebStorm's TypeScript service (same as VS Code's typescriptServerPlugins),
 * so .ts files see real types for `import X, { named } from './X.svelte'` — including exports from <script module>.
 * Pattern follows JetBrains' Astro plugin (Apache-2.0): Pluggable TS service + downloadable TS plugin package.
 */

private const val TS_PLUGIN_NAME = "typescript-svelte-plugin"
private const val TS_PLUGIN_VERSION = "0.3.52"

// Not bundled: downloaded from npm on first use. (PackageVersion.bundled is @ApiStatus.Internal.)
private val tsPluginVersion = PackageVersion.downloadable(TS_PLUGIN_VERSION)

private object SvelteTSPluginPackageDescriptor : LspServerPackageDescriptor(
  name = TS_PLUGIN_NAME,
  defaultVersion = tsPluginVersion,
  defaultPackageRelativePath = "",
) {
  override val registryVersion: String get() = ""
}

object SvelteTSPluginLoader : TSPluginLoader(SvelteTSPluginPackageDescriptor) {
  // Same as Astro with default settings: the downloaded package.
  override fun getSelectedPackage(project: Project): NodePackage =
    createPackage(defaultPackageKey, SvelteTSPluginPackageDescriptor.serverPackage)
}

/** Active for files under a package.json that depends on svelte. */
object SvelteActivationHelper : ServiceActivationHelper {
  override fun isProjectContext(project: Project, context: VirtualFile): Boolean {
    val packageJson = PackageJsonUtil.findUpPackageJson(context) ?: return false
    return PackageJsonData.getOrCreate(packageJson).isDependencyOfAnyType("svelte")
  }

  override fun isEnabledInSettings(project: Project): Boolean =
    !ApplicationManager.getApplication().isUnitTestMode && SvelteSettings.getInstance(project).tsPluginEnabled
}

object SvelteTSPluginActivationRule : TSPluginActivationRule(SvelteTSPluginLoader, SvelteActivationHelper)

private val svelteTsPlugin = DownloadableTypeScriptServicePlugin(
  shortLabel = "Svelte",
  activationRule = SvelteTSPluginActivationRule,
)

class SveltePluginTypeScriptService(project: Project) : PluggableTypeScriptService(project, svelteTsPlugin) {
  override fun getProcessName(): String = "Svelte + TypeScript"

  override fun getInitialOpenCommands(): List<JSLanguageServiceSimpleCommand> =
    listOf<JSLanguageServiceSimpleCommand>(
      ConfigurePluginRequest(
        ConfigurePluginRequestArguments(
          pluginName = TS_PLUGIN_NAME,
          configuration = mapOf("enable" to true, "assumeIsSvelteProject" to true),
        )
      )
    ) + super.getInitialOpenCommands()

  /** .svelte files are checked by the Svelte language server, not by tsserver ("not included in tsconfig.json"). */
  override fun isAcceptableForHighlighting(file: PsiFile): Boolean =
    file.fileType !is SvelteFileType && super.isAcceptableForHighlighting(file)

  override fun isTypeEvaluationEnabled(): Boolean = false
}

class SvelteLanguageServiceProvider(project: Project) : TypeScriptServiceProvider() {
  private val tsService by lazy(LazyThreadSafetyMode.PUBLICATION) { project.service<SveltePluginTypeScriptServiceWrapper>() }

  override val allServices: List<TypeScriptService>
    get() = listOf(tsService.service)

  override fun isHighlightingCandidate(file: VirtualFile): Boolean =
    TypeScriptLanguageServiceUtil.isJavaScriptOrTypeScriptFileType(file.fileType)
}

@Service(Service.Level.PROJECT)
class SveltePluginTypeScriptServiceWrapper(project: Project) : Disposable {
  val service = SveltePluginTypeScriptService(project)

  override fun dispose() {
    Disposer.dispose(service)
  }
}
