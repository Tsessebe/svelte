package io.github.tsessebe.svelte

import com.intellij.lexer.Lexer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.lexer.SvelteLexer

class SvelteLexerTest : BasePlatformTestCase() {

  private fun tokens(text: String, highlight: Boolean = false): List<Pair<String, String>> {
    val lexer: Lexer = SvelteLexer(highlight)
    lexer.start(text)
    val out = mutableListOf<Pair<String, String>>()
    while (lexer.tokenType != null) {
      out += lexer.tokenType.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd)
      lexer.advance()
    }
    return out
  }

  private fun assertHas(text: String, type: String, value: String, highlight: Boolean = false) {
    val t = tokens(text, highlight)
    assertTrue("expected $type '$value' in\n${t.joinToString("\n")}", t.contains(type to value))
  }

  fun testBraceInsideStringDoesNotCloseMustache() {
    val text = """<p>{fmt('}', `a ${'$'}{ {x:1}.x } }`, /}/.test(s))}</p>"""
    assertHas(text, "SVELTE_EXPRESSION", """fmt('}', `a ${'$'}{ {x:1}.x } }`, /}/.test(s))""")
    assertHas(text, "XML_END_TAG_START", "</")
  }

  fun testEachBlock() {
    val text = "{#each items as { id, name }, i (id)}x{/each}"
    assertHas(text, "SVELTE_BLOCK_KEYWORD", "#each")
    assertHas(text, "SVELTE_EXPRESSION", "items")
    assertHas(text, "SVELTE_INNER_KEYWORD", "as")
    assertHas(text, "SVELTE_BINDING", "{ id, name }")
    assertHas(text, "SVELTE_BINDING", "i")
    assertHas(text, "SVELTE_EXPRESSION", "id")
    assertHas(text, "SVELTE_BLOCK_KEYWORD", "/each")
  }

  fun testElseIfAndAwait() {
    assertHas("{#if a}{:else if b}{/if}", "SVELTE_INNER_KEYWORD", "if")
    assertHas("{#await p then v}{/await}", "SVELTE_INNER_KEYWORD", "then")
    assertHas("{#await p then v}{/await}", "SVELTE_BINDING", "v")
  }

  fun testSnippetAndRender() {
    val text = "{#snippet row(item: Item, i: number)}{/snippet}{@render row(x, 1)}"
    assertHas(text, "SVELTE_SNIPPET_NAME", "row")
    assertHas(text, "SVELTE_BINDING", "(item: Item, i: number)")
    assertHas(text, "SVELTE_SPECIAL_KEYWORD", "@render")
    assertHas(text, "SVELTE_EXPRESSION", "row(x, 1)")
  }

  fun testAttributes() {
    val text = """<Button {...rest} {disabled} onclick={() => n++} class="a {b} c" bind:value={v} />"""
    assertHas(text, "SVELTE_SPREAD", "...")
    assertHas(text, "SVELTE_EXPRESSION", "rest")
    assertHas(text, "SVELTE_EXPRESSION", "disabled")
    assertHas(text, "SVELTE_EXPRESSION", "() => n++")
    assertHas(text, "SVELTE_EXPRESSION", "b")
    assertHas(text, "XML_NAME", "bind:value")
  }

  fun testDirectivesSplitInHighlightMode() {
    val text = """<button on:click|preventDefault={go}>x</button>"""
    assertHas(text, "SVELTE_DIRECTIVE_PREFIX", "on", highlight = true)
    assertHas(text, "SVELTE_DIRECTIVE_NAME", "click", highlight = true)
    assertHas(text, "SVELTE_DIRECTIVE_MODIFIER", "|preventDefault", highlight = true)
  }

  fun testScriptContentIsNotMustache() {
    val text = "<script lang=\"ts\">\nconst o = { a: '}' };\n</script>\n<p>{o.a}</p>"
    val t = tokens(text)
    val scriptEnd = t.indexOfFirst { it.first == "XML_END_TAG_START" }
    assertTrue(t.joinToString("\n"), t.subList(0, scriptEnd).none { it.first.startsWith("SVELTE_") })
    assertHas(text, "SVELTE_EXPRESSION", "o.a")
  }
}
