package io.github.tsessebe.svelte

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Diagnostic: dumps editor highlighter tokens + attribute keys to build/highlight-dump.txt. */
class SvelteHighlightingDumpTest : BasePlatformTestCase() {
  fun testDump() {
    val text = """
      <script lang="ts">let { bank = '' }: { bank?: string } = ${'$'}props();</script>
      <h2 class={cn('a', X[k(bank)])}>{bank}</h2>
      <p class="x"><b>A: </b><span class="m">{account}</span></p>
      {#if ref.trim() !== ''}
        <Ref {ref} inline bind:value={v} />
      {/if}
    """.trimIndent()
    myFixture.configureByText("Dump.svelte", text)
    val it = (myFixture.editor as EditorEx).highlighter.createIterator(0)
    val sb = StringBuilder()
    while (!it.atEnd()) {
      val keys = it.textAttributesKeys.joinToString(",") { k -> k.externalName }
      sb.append(String.format("%-40s %-50s %s%n", it.tokenType, "'" + text.substring(it.start, it.end).replace("\n", "\\n") + "'", keys))
      it.advance()
    }
    File(System.getProperty("user.dir"), "build/highlight-dump.txt").writeText(sb.toString())
    // Component tags and their props get their own tokens (Blazor-style colours).
    assertTrue(sb.toString(), sb.lines().any { it.startsWith("SVELTE_COMPONENT_NAME") && it.contains("'Ref'") })
    assertTrue(sb.toString(), sb.lines().any { it.startsWith("SVELTE_COMPONENT_ATTRIBUTE_NAME") && it.contains("'inline'") })
    // Mustache expressions in text content, attributes and blocks must get TypeScript tokens.
    for (ident in listOf("'account'", "'cn'", "'ref'")) {
      assertTrue("no TS token for $ident:\n$sb", sb.lines().any { it.startsWith("JS:IDENTIFIER") && it.contains(ident) })
    }
  }
}
