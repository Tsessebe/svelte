package io.github.tsessebe.svelte.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.tsessebe.svelte.service.SvelteLanguageServer
import io.github.tsessebe.svelte.service.SvelteServices

/** Settings | Languages & Frameworks | TypeScript | Svelte */
class SvelteConfigurable(private val project: Project) : BoundSearchableConfigurable("Svelte", "settings.svelte") {

  private val settings = SvelteSettings.getInstance(project)

  override fun createPanel(): DialogPanel = panel {
    group("Svelte Language Server") {
      row {
        checkBox("Enable for .svelte files")
          .bindSelected(settings::serviceEnabled)
          .comment("Completion, navigation, hover, type errors and rename in .svelte files.")
      }
      row("Server package:") {
        textFieldWithBrowseButton(
          FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("svelte-language-server Package Folder"),
          project,
        )
          .align(AlignX.FILL)
          .bindText({ settings.serverPath ?: "" }, { settings.serverPath = it })
          .comment(
            "Leave empty to use the project's node_modules/svelte-language-server, " +
            "or else a managed copy of version ${SvelteLanguageServer.VERSION}."
          )
      }
      row("In use:") {
        comment(SvelteLanguageServer.describe(project))
      }
    }
    group("TypeScript") {
      row {
        checkBox("Load typescript-svelte-plugin into the TypeScript service")
          .bindSelected(settings::tsPluginEnabled)
          .comment("Lets .ts files see real types of .svelte imports, including &lt;script module&gt; exports.")
      }
    }
    onApply { SvelteServices.restart(project) }
  }
}
