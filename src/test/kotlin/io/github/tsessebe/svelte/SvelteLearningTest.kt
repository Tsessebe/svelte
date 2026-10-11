package io.github.tsessebe.svelte

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.docs.SvelteDocs
import io.github.tsessebe.svelte.docs.SvelteDocumentationProvider
import io.github.tsessebe.svelte.inspections.SvelteLegacySyntaxInspection

class SvelteLearningTest : BasePlatformTestCase() {

  private fun keyAtCaret(text: String): String? {
    val file = myFixture.configureByText("Doc.svelte", text)
    val leaf = file.findElementAt(myFixture.caretOffset)!!
    return SvelteDocumentationProvider.keyFor(leaf)
  }

  fun testDocKeys() {
    assertEquals("#if", keyAtCaret("{#i<caret>f a}x{/if}"))
    assertEquals("#each", keyAtCaret("{#each xs as x}x{/ea<caret>ch}"))
    assertEquals(":then", keyAtCaret("{#await p th<caret>en v}{/await}"))
    assertEquals("@render", keyAtCaret("{@ren<caret>der s()}"))
    assertEquals("bind:", keyAtCaret("<input bi<caret>nd:value={v} />"))
    assertEquals("svelte:head", keyAtCaret("<svelte:he<caret>ad></svelte:head>"))
    assertEquals("slot", keyAtCaret("<sl<caret>ot />"))
  }

  fun testEveryEntryRenders() {
    for ((key, entry) in SvelteDocs.entries) {
      val html = SvelteDocs.html(entry)
      assertTrue(key, html.contains("svelte.dev/docs/svelte/${entry.slug}"))
    }
  }

  fun testLegacySyntaxWarnings() {
    myFixture.enableInspections(SvelteLegacySyntaxInspection())
    myFixture.configureByText("Legacy.svelte", """
      <script>
        import { createEventDispatcher } from 'svelte';
        export let name = 'x';
        ${'$'}: upper = name.toUpperCase();
        const dispatch = createEventDispatcher();
      </script>
      <button on:click={() => dispatch('go')}>{upper}</button>
      <slot />
    """.trimIndent())
    val warnings = myFixture.doHighlighting().filter { it.description?.contains("Svelte 4") == true }.map { it.text }
    for (expected in listOf("export", "${'$'}:", "createEventDispatcher", "on:click", "slot")) {
      assertTrue("$expected not flagged in $warnings", warnings.any { it == expected })
    }
  }

  fun testRunesComponentHasNoWarnings() {
    myFixture.enableInspections(SvelteLegacySyntaxInspection())
    myFixture.configureByText("Runes.svelte", """
      <script lang="ts">
        let { name = 'x', onsave } = ${'$'}props();
        let upper = ${'$'}derived(name.toUpperCase());
      </script>
      <button onclick={() => onsave?.()}>{upper}</button>
    """.trimIndent())
    val warnings = myFixture.doHighlighting().filter { it.description?.contains("Svelte 4") == true }
    assertEquals(emptyList<Any>(), warnings.map { it.text })
  }

  fun testMustacheAttributeValuesAreNotWrongHtmlValues() {
    myFixture.enableInspections(com.intellij.codeInspection.htmlInspections.HtmlWrongAttributeValueInspection())
    myFixture.configureByText("Attr.svelte", "<script>let busy = false;</script><button disabled={busy} aria-busy={busy}>x</button>")
    val wrong = myFixture.doHighlighting().filter { it.description?.contains("Wrong attribute value") == true }
    assertEquals(emptyList<Any>(), wrong.map { it.text })
  }

  fun testOnDirectiveQuickFix() {
    myFixture.enableInspections(SvelteLegacySyntaxInspection())
    myFixture.configureByText("Fix.svelte", "<button on:cl<caret>ick={go}>x</button>")
    val fix = myFixture.getAvailableIntention("Replace 'on:click' with 'onclick'")!!
    myFixture.launchAction(fix)
    assertEquals("<button onclick={go}>x</button>", myFixture.file.text)
  }
}
