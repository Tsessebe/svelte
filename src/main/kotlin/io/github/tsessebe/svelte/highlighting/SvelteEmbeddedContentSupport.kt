package io.github.tsessebe.svelte.highlighting

import com.intellij.html.embedding.HtmlEmbeddedContentProvider
import com.intellij.html.embedding.HtmlEmbeddedContentSupport
import com.intellij.html.embedding.HtmlEmbedmentInfo
import com.intellij.html.embedding.HtmlTokenEmbeddedContentProvider
import com.intellij.lang.Language
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.HtmlScriptStyleEmbeddedContentProvider
import io.github.tsessebe.svelte.lexer.SvelteLexer
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes
import java.util.function.Function

class SvelteEmbeddedContentSupport : HtmlEmbeddedContentSupport {
  override fun isEnabled(lexer: BaseHtmlLexer): Boolean = lexer is SvelteLexer

  // Note: called from the BaseHtmlLexer constructor, before HtmlLexer.isHighlighting is initialised,
  // so providers can't be chosen by mode. The token provider is harmless in parse mode (same token, same range);
  // its highlighting lexer is only created in highlight mode.
  override fun createEmbeddedContentProviders(lexer: BaseHtmlLexer): List<HtmlEmbeddedContentProvider> = listOf(
    SvelteScriptStyleContentProvider(lexer),
    // TypeScript colours inside mustache expressions / bindings. Parsing embeds real JS PSI in M2.
    HtmlTokenEmbeddedContentProvider(
      lexer,
      SvelteTokenTypes.JS_CONTENT,
      Function { HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(JavaScriptSupportLoader.TYPESCRIPT)?.createHighlightingLexer() },
      Function { it },
    ),
  )
}

/** Like the platform provider, but also honours Svelte's `lang` attribute (<script lang="ts">, <style lang="scss">). */
private class SvelteScriptStyleContentProvider(lexer: BaseHtmlLexer) : HtmlScriptStyleEmbeddedContentProvider(lexer) {

  override fun isInterestedInAttribute(attributeName: CharSequence): Boolean =
    super.isInterestedInAttribute(attributeName) || attributeName.toString().equals("lang", ignoreCase = true)

  override fun scriptEmbedmentInfo(mimeType: String?): HtmlEmbedmentInfo? =
    when (mimeType?.trim()?.lowercase()) {
      "ts", "typescript", "text/typescript", "application/typescript" ->
        HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(JavaScriptSupportLoader.TYPESCRIPT)
      "js", "javascript" ->
        Language.findLanguageByID("JavaScript")?.let { HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(it) }
      else -> super.scriptEmbedmentInfo(mimeType)
    }

  override fun styleLanguage(styleLang: String?): Language? {
    val id = when (styleLang?.trim()?.lowercase()) {
      "scss" -> "SCSS"
      "sass" -> "SASS"
      "less" -> "LESS"
      "postcss", "pcss" -> "PostCSS"
      "styl", "stylus" -> "Stylus"
      else -> null
    }
    return id?.let { Language.findLanguageByID(it) } ?: super.styleLanguage(styleLang)
  }
}
