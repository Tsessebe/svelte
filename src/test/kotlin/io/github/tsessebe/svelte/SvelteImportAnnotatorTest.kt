package io.github.tsessebe.svelte

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.highlighting.SvelteHighlighterColors

class SvelteImportAnnotatorTest : BasePlatformTestCase() {

  fun testUsedAndUnusedImports() {
    myFixture.configureByText("Imports.svelte", """
      <script lang="ts">
        import Card from './Card.svelte';
        import Unused from './Unused.svelte';
        import { cn, notUsed, type Item } from './utils';
        import { fade } from 'svelte/transition';
        import { count } from './stores';
        let items: Item[] = [];
      </script>
      <Card class={cn('a')} transition:fade>{${'$'}count}</Card>
    """.trimIndent())
    val infos = myFixture.doHighlighting()
    fun keyOf(name: String) = infos.firstOrNull { it.text == name && it.forcedTextAttributesKey != null }?.forcedTextAttributesKey
    val dump = infos.filter { it.forcedTextAttributesKey != null }.joinToString("\n") { "'${it.text}' -> ${it.forcedTextAttributesKey?.externalName}" }
    fun assertKey(expected: Any?, name: String) = assertEquals("$name\n$dump", expected, keyOf(name))

    assertKey(SvelteHighlighterColors.COMPONENT, "Card")
    assertKey(SvelteHighlighterColors.USED_IMPORT, "cn")
    assertKey(SvelteHighlighterColors.USED_IMPORT, "fade")   // directive
    assertKey(SvelteHighlighterColors.USED_IMPORT, "count")  // ${'$'}count store
    assertKey(SvelteHighlighterColors.USED_IMPORT, "Item")   // type used in script, not a tag
    assertKey(HighlightInfoType.UNUSED_SYMBOL.attributesKey, "Unused")
    assertKey(HighlightInfoType.UNUSED_SYMBOL.attributesKey, "notUsed")
  }
}
