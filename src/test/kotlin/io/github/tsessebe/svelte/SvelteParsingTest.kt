package io.github.tsessebe.svelte

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class SvelteParsingTest : BasePlatformTestCase() {

  private fun parseErrors(name: String, text: String): List<String> {
    val file = myFixture.configureByText(name, text)
    return PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).map {
      val o = it.textOffset
      val line = text.substring(0, o).count { c -> c == '\n' } + 1
      "$name:$line [${it.language.id}] ${it.errorDescription}: '${text.substring(o, minOf(text.length, o + 50)).replace("\n", "\\n")}'"
    }
  }

  private fun assertNoParseErrors(name: String, text: String) {
    val errors = parseErrors(name, text)
    assertTrue(errors.joinToString("\n"), errors.isEmpty())
  }

  fun testFileTypeIsSvelte() {
    val file = myFixture.configureByText("A.svelte", "<p>hi</p>")
    assertEquals("Svelte", file.fileType.name)
  }

  fun testRunesComponent() = assertNoParseErrors("Runes.svelte", """
    <script lang="ts">
      import Child from './Child.svelte';
      import { helper, type Item } from './lib';
      interface Props { items: Item[] }
      let { items = [], onselect }: { items: string[]; onselect?: (s: string) => void } = ${'$'}props();
      let count = ${'$'}state(0);
      const doubled = ${'$'}derived(count * 2);
    </script>

    <svelte:head><title>T</title></svelte:head>
    <svelte:window onkeydown={(e) => e.key === 'Escape' && (count = 0)} />

    {#snippet row(item: string)}
      <li class={item === 'a' ? 'on' : ''} onclick={() => onselect?.(item)}>{item}</li>
    {/snippet}

    {#if items.length > 0}
      <ul>
        {#each items as item, i (item)}
          {@const label = `${'$'}{i}: ${'$'}{item}`}
          {@render row(label)}
        {/each}
      </ul>
    {:else if count > 0}
      <Child bind:value={count} {doubled} {...rest} />
    {:else}
      <p title="n = {count}">Nothing</p>
    {/if}

    {#key count}<span>{count}</span>{/key}
    {#await load() then data}{data}{:catch e}{e.message}{/await}
  """.trimIndent())

  fun testLegacyComponent() = assertNoParseErrors("Legacy.svelte", """
    <script>
      import { createEventDispatcher } from 'svelte';
      export let name = 'x';
      const dispatch = createEventDispatcher();
      ${'$'}: upper = name.toUpperCase();
    </script>

    <button on:click|preventDefault={() => dispatch('go')} class:active={name === 'x'}>{upper}</button>
    <slot name="footer" />
    {@html '<b>x</b>'}

    <style>
      button { color: red; }
    </style>
  """.trimIndent())

  /** Real-world files, not committed: run with -PsvelteCorpus=/path/to/dir */
  fun testCorpus() {
    val root = System.getProperty("svelte.corpus") ?: return
    val files = File(root).walkTopDown().filter { it.isFile && it.extension == "svelte" }.toList()
    assertTrue("no .svelte files under $root", files.isNotEmpty())
    val errors = files.flatMap { parseErrors(it.name, it.readText().replace("\r\n", "\n")) }
    assertTrue("${errors.size} parse errors in ${files.size} files:\n" + errors.joinToString("\n"), errors.isEmpty())
  }
}
