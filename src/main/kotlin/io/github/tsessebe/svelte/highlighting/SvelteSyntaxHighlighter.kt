package io.github.tsessebe.svelte.highlighting

import com.intellij.ide.highlighter.HtmlFileHighlighter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import io.github.tsessebe.svelte.lexer.SvelteLexer
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes as T

class SvelteSyntaxHighlighter : HtmlFileHighlighter() {
  override fun getHighlightingLexer(): Lexer = SvelteLexer(true)

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    MAP[tokenType]?.let { return it }
    @Suppress("UNCHECKED_CAST")
    return super.getTokenHighlights(tokenType) as Array<TextAttributesKey>
  }

  companion object {
    private val TAG = XmlHighlighterColors.HTML_TAG
    private val MAP: Map<IElementType, Array<TextAttributesKey>> = mapOf(
      T.LBRACE to arrayOf(SvelteHighlighterColors.MUSTACHE_BRACES),
      T.RBRACE to arrayOf(SvelteHighlighterColors.MUSTACHE_BRACES),
      T.BLOCK_KEYWORD to arrayOf(SvelteHighlighterColors.BLOCK_KEYWORD),
      T.SPECIAL_KEYWORD to arrayOf(SvelteHighlighterColors.SPECIAL_TAG),
      T.INNER_KEYWORD to arrayOf(SvelteHighlighterColors.INNER_KEYWORD),
      T.SNIPPET_NAME to arrayOf(SvelteHighlighterColors.SNIPPET_NAME),
      T.SPREAD to arrayOf(SvelteHighlighterColors.PUNCTUATION),
      T.COMMA to arrayOf(SvelteHighlighterColors.PUNCTUATION),
      T.LPAREN to arrayOf(SvelteHighlighterColors.PUNCTUATION),
      T.RPAREN to arrayOf(SvelteHighlighterColors.PUNCTUATION),
      T.DIRECTIVE_PREFIX to arrayOf(TAG, SvelteHighlighterColors.DIRECTIVE_PREFIX),
      T.DIRECTIVE_COLON to arrayOf(TAG, SvelteHighlighterColors.DIRECTIVE_PREFIX),
      T.DIRECTIVE_NAME to arrayOf(TAG, SvelteHighlighterColors.DIRECTIVE_NAME),
      T.DIRECTIVE_MODIFIER to arrayOf(TAG, SvelteHighlighterColors.DIRECTIVE_MODIFIER),
      T.SVELTE_ELEMENT_NAME to arrayOf(TAG, SvelteHighlighterColors.SVELTE_ELEMENT),
      T.COMPONENT_NAME to arrayOf(TAG, SvelteHighlighterColors.COMPONENT),
      T.COMPONENT_ATTRIBUTE_NAME to arrayOf(TAG, SvelteHighlighterColors.COMPONENT_ATTRIBUTE),
    )
  }
}

class SvelteSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = SvelteSyntaxHighlighter()
}
