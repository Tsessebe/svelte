package io.github.tsessebe.svelte

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.psi.SvelteMustacheElement
import io.github.tsessebe.svelte.psi.SvelteScriptScope

class SvelteTemplateReferencesTest : BasePlatformTestCase() {

  private fun resolveAtCaret(): PsiNamedElement? =
    myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve() as? PsiNamedElement

  fun testResolvesToScriptDeclaration() {
    myFixture.configureByText("A.svelte", """
      <script lang="ts">
        let count = ${'$'}state(0);
        function inc() { count++; }
      </script>
      <button onclick={inc}>{cou<caret>nt}</button>
    """.trimIndent())
    assertEquals("count", resolveAtCaret()?.name)
  }

  fun testHandlerInAttribute() {
    myFixture.configureByText("A.svelte", "<script>function handleDelete() {}</script><button onclick={handle<caret>Delete}>x</button>")
    assertEquals("handleDelete", resolveAtCaret()?.name)
  }

  fun testPropertyNameIsNotAReference() {
    myFixture.configureByText("A.svelte", "<script>let count = 0; let obj = {};</script>{obj.cou<caret>nt}")
    assertNull(resolveAtCaret())
  }

  fun testEachBindingShadowsScript() {
    myFixture.configureByText("A.svelte", "<script>let item = 1; let items = [];</script>{#each items as item}{it<caret>em}{/each}")
    assertNull(resolveAtCaret())
  }

  fun testStoreSubscription() {
    myFixture.configureByText("A.svelte", "<script>import { count } from './stores';</script>{${'$'}cou<caret>nt}")
    assertEquals("count", resolveAtCaret()?.name)
  }

  fun testFindUsagesIncludesTemplate() {
    myFixture.configureByText("A.svelte", """
      <script>
        let deleting = false;
        async function handleDelete() { deleting = true; }
      </script>
      <button onclick={handleDelete} disabled={deleting}>{deleting ? 'Deleting…' : 'Delete'}</button>
    """.trimIndent())
    val decl = SvelteScriptScope.declarations(myFixture.file)["handleDelete"]!!
    val usages = myFixture.findUsages(decl)
    assertEquals(1, usages.count { it.element is SvelteMustacheElement })
    val deleting = SvelteScriptScope.declarations(myFixture.file)["deleting"]!!
    assertEquals(2, myFixture.findUsages(deleting).count { it.element is SvelteMustacheElement })
  }

  fun testRenameUpdatesTemplate() {
    myFixture.configureByText("A.svelte", "<script>let count = 0;</script><p title=\"n={count}\">{count + 1}</p>")
    myFixture.renameElement(SvelteScriptScope.declarations(myFixture.file)["count"]!!, "total")
    assertEquals("<script>let total = 0;</script><p title=\"n={total}\">{total + 1}</p>", myFixture.file.text)
  }
}
