package io.github.tsessebe.svelte.lang

import com.intellij.lang.html.HTMLLanguage

/** Svelte markup is an HTML dialect, so HTML features (tags, attributes, Emmet, Tailwind) apply. */
class SvelteHtmlLanguage private constructor() : HTMLLanguage(HTMLLanguage.INSTANCE, "SvelteHTML", "text/x-svelte") {
  override fun getDisplayName(): String = "Svelte"

  companion object {
    @JvmField
    val INSTANCE: SvelteHtmlLanguage = SvelteHtmlLanguage()
  }
}
