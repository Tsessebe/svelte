package io.github.tsessebe.svelte.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Per-project, per-user (workspace) Svelte settings. */
@Service(Service.Level.PROJECT)
@State(name = "SvelteSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SvelteSettings : SimplePersistentStateComponent<SvelteSettingsState>(SvelteSettingsState()) {

  /** Run svelte-language-server for .svelte files. */
  var serviceEnabled: Boolean
    get() = state.serviceEnabled
    set(value) { state.serviceEnabled = value }

  /** Custom svelte-language-server package folder (or its bin/server.js). Null: project node_modules, then managed copy. */
  var serverPath: String?
    get() = state.serverPath?.takeIf { it.isNotBlank() }
    set(value) { state.serverPath = value?.trim()?.takeIf { it.isNotEmpty() } }

  /** Load typescript-svelte-plugin into the TypeScript service for .ts/.js files. */
  var tsPluginEnabled: Boolean
    get() = state.tsPluginEnabled
    set(value) { state.tsPluginEnabled = value }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SvelteSettings = project.service()
  }
}

class SvelteSettingsState : BaseState() {
  var serviceEnabled by property(true)
  var serverPath by string(null)
  var tsPluginEnabled by property(true)
}
