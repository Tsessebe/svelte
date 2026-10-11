package io.github.tsessebe.svelte.lang

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class SvelteFileType private constructor() : HtmlFileType(SvelteHtmlLanguage.INSTANCE) {
  override fun getName(): String = "Svelte"
  override fun getDescription(): String = "Svelte component"
  override fun getDefaultExtension(): String = "svelte"
  override fun getIcon(): Icon = ICON

  companion object {
    @JvmField
    val INSTANCE: SvelteFileType = SvelteFileType()

    @JvmField
    val ICON: Icon = IconLoader.getIcon("/icons/svelte.svg", SvelteFileType::class.java)
  }
}
