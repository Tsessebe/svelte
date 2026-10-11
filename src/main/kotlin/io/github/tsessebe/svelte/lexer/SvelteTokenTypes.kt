package io.github.tsessebe.svelte.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlTokenType
import io.github.tsessebe.svelte.lang.SvelteHtmlLanguage
import io.github.tsessebe.svelte.lexer.SvelteMustacheScanner.Kind

class SvelteTokenType(debugName: String) : IElementType(debugName, SvelteHtmlLanguage.INSTANCE)

object SvelteTokenTypes {
  // mustache
  @JvmField val LBRACE = SvelteTokenType("SVELTE_LBRACE")
  @JvmField val RBRACE = SvelteTokenType("SVELTE_RBRACE")
  @JvmField val BLOCK_KEYWORD = SvelteTokenType("SVELTE_BLOCK_KEYWORD")
  @JvmField val SPECIAL_KEYWORD = SvelteTokenType("SVELTE_SPECIAL_KEYWORD")
  @JvmField val INNER_KEYWORD = SvelteTokenType("SVELTE_INNER_KEYWORD")
  @JvmField val EXPRESSION = SvelteTokenType("SVELTE_EXPRESSION")
  @JvmField val BINDING = SvelteTokenType("SVELTE_BINDING")
  @JvmField val SNIPPET_NAME = SvelteTokenType("SVELTE_SNIPPET_NAME")
  @JvmField val SPREAD = SvelteTokenType("SVELTE_SPREAD")
  @JvmField val COMMA = SvelteTokenType("SVELTE_COMMA")
  @JvmField val LPAREN = SvelteTokenType("SVELTE_LPAREN")
  @JvmField val RPAREN = SvelteTokenType("SVELTE_RPAREN")

  // highlight-mode only
  @JvmField val DIRECTIVE_PREFIX = SvelteTokenType("SVELTE_DIRECTIVE_PREFIX")
  @JvmField val DIRECTIVE_COLON = SvelteTokenType("SVELTE_DIRECTIVE_COLON")
  @JvmField val DIRECTIVE_NAME = SvelteTokenType("SVELTE_DIRECTIVE_NAME")
  @JvmField val DIRECTIVE_MODIFIER = SvelteTokenType("SVELTE_DIRECTIVE_MODIFIER")
  @JvmField val SVELTE_ELEMENT_NAME = SvelteTokenType("SVELTE_ELEMENT_NAME")
  @JvmField val COMPONENT_NAME = SvelteTokenType("SVELTE_COMPONENT_NAME")
  @JvmField val COMPONENT_ATTRIBUTE_NAME = SvelteTokenType("SVELTE_COMPONENT_ATTRIBUTE_NAME")

  /** Everything that may appear between '{' and '}' (whitespace is XML_WHITE_SPACE). */
  @JvmField val MUSTACHE_INNER: TokenSet = TokenSet.create(
    BLOCK_KEYWORD, SPECIAL_KEYWORD, INNER_KEYWORD, EXPRESSION, BINDING, SNIPPET_NAME, SPREAD, COMMA, LPAREN, RPAREN,
  )

  /** Tokens whose text is JS/TS, highlighted by an embedded TypeScript lexer. */
  @JvmField val JS_CONTENT: TokenSet = TokenSet.create(EXPRESSION, BINDING)

  @JvmStatic
  fun fromKind(kind: Kind): IElementType = when (kind) {
    Kind.LBRACE -> LBRACE
    Kind.RBRACE -> RBRACE
    Kind.BLOCK_KEYWORD -> BLOCK_KEYWORD
    Kind.SPECIAL_KEYWORD -> SPECIAL_KEYWORD
    Kind.INNER_KEYWORD -> INNER_KEYWORD
    Kind.EXPRESSION -> EXPRESSION
    Kind.BINDING -> BINDING
    Kind.SNIPPET_NAME -> SNIPPET_NAME
    Kind.SPREAD -> SPREAD
    Kind.COMMA -> COMMA
    Kind.LPAREN -> LPAREN
    Kind.RPAREN -> RPAREN
    Kind.WHITESPACE -> XmlTokenType.XML_WHITE_SPACE
  }
}
