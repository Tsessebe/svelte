package io.github.tsessebe.svelte.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object SvelteHighlighterColors {
  @JvmField val MUSTACHE_BRACES: TextAttributesKey = createTextAttributesKey("SVELTE_MUSTACHE_BRACES", D.BRACES)
  @JvmField val BLOCK_KEYWORD: TextAttributesKey = createTextAttributesKey("SVELTE_BLOCK_KEYWORD", D.KEYWORD)
  @JvmField val SPECIAL_TAG: TextAttributesKey = createTextAttributesKey("SVELTE_SPECIAL_TAG", D.METADATA)
  @JvmField val INNER_KEYWORD: TextAttributesKey = createTextAttributesKey("SVELTE_INNER_KEYWORD", D.KEYWORD)
  @JvmField val SNIPPET_NAME: TextAttributesKey = createTextAttributesKey("SVELTE_SNIPPET_NAME", D.FUNCTION_DECLARATION)
  @JvmField val PUNCTUATION: TextAttributesKey = createTextAttributesKey("SVELTE_PUNCTUATION", D.COMMA)
  @JvmField val DIRECTIVE_PREFIX: TextAttributesKey = createTextAttributesKey("SVELTE_DIRECTIVE_PREFIX", D.KEYWORD)
  @JvmField val DIRECTIVE_NAME: TextAttributesKey = createTextAttributesKey("SVELTE_DIRECTIVE_NAME", XmlHighlighterColors.HTML_ATTRIBUTE_NAME)
  @JvmField val DIRECTIVE_MODIFIER: TextAttributesKey = createTextAttributesKey("SVELTE_DIRECTIVE_MODIFIER", D.METADATA)
  @JvmField val SVELTE_ELEMENT: TextAttributesKey = createTextAttributesKey("SVELTE_ELEMENT", D.KEYWORD)
  @JvmField val COMPONENT: TextAttributesKey = createTextAttributesKey("SVELTE_COMPONENT", D.CLASS_NAME)
  @JvmField val USED_IMPORT: TextAttributesKey = createTextAttributesKey("SVELTE_USED_IMPORT", D.IDENTIFIER)
  @JvmField val COMPONENT_ATTRIBUTE: TextAttributesKey =
    createTextAttributesKey("SVELTE_COMPONENT_ATTRIBUTE", XmlHighlighterColors.HTML_ATTRIBUTE_NAME)
}
