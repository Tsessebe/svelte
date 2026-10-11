package io.github.tsessebe.svelte

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.editor.SvelteFoldingBuilder
import io.github.tsessebe.svelte.parser.SvelteElementTypes as E

class SvelteBlocksTest : BasePlatformTestCase() {

  private fun parse(text: String): PsiFile = myFixture.configureByText("Blocks.svelte", text)

  private fun errors(file: PsiFile): List<String> =
    PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).map { it.errorDescription }

  private fun all(file: PsiFile): List<PsiElement> {
    val out = mutableListOf<PsiElement>()
    PsiTreeUtil.processElements(file) { out += it; true }
    return out
  }

  private fun blocks(file: PsiFile) = all(file).filter { E.BLOCKS.contains(it.elementType) }

  fun testIfElseIfElse() {
    val file = parse("{#if a}<p>x</p>{:else if b}y{:else}z{/if}")
    assertEquals(emptyList<String>(), errors(file))
    val block = blocks(file).single()
    assertEquals(E.IF_BLOCK, block.elementType)
    val tags = block.children.filter { E.BLOCK_TAGS.contains(it.elementType) }.map { it.elementType }
    assertEquals(listOf(E.BLOCK_OPEN, E.BLOCK_BRANCH, E.BLOCK_BRANCH, E.BLOCK_CLOSE), tags)
    assertEquals("p", PsiTreeUtil.findChildOfType(block, XmlTag::class.java)!!.name)
  }

  fun testNestedBlocksInsideTags() {
    val file = parse("""
      <ul>
        {#if items.length}
          {#each items as item (item.id)}
            <li>{item.name}</li>
          {:else}
            <li>none</li>
          {/each}
        {/if}
      </ul>
    """.trimIndent())
    assertEquals(emptyList<String>(), errors(file))
    val types = blocks(file).map { it.elementType }
    assertEquals(listOf(E.IF_BLOCK, E.EACH_BLOCK), types)
    // the each block sits inside the if block, inside <ul>
    val each = blocks(file)[1]
    assertEquals(E.IF_BLOCK, each.parent.elementType)
    assertEquals("ul", PsiTreeUtil.getParentOfType(each, XmlTag::class.java)!!.name)
  }

  fun testAwaitSnippetKey() {
    val file = parse("{#await p}…{:then v}{v}{:catch e}{e}{/await}{#snippet s(x)}<b>{x}</b>{/snippet}{#key k}k{/key}")
    assertEquals(emptyList<String>(), errors(file))
    assertEquals(listOf(E.AWAIT_BLOCK, E.SNIPPET_BLOCK, E.KEY_BLOCK), blocks(file).map { it.elementType })
  }

  fun testUnclosedBlock() {
    val errs = errors(parse("{#if a}<p>x</p>"))
    assertTrue(errs.toString(), errs.any { it.contains("not closed") })
  }

  fun testMismatchedClose() {
    val errs = errors(parse("{#if a}x{/each}"))
    assertTrue(errs.toString(), errs.any { it.contains("Expected {/if}") })
  }

  fun testBadBranch() {
    val errs = errors(parse("{#key a}x{:else}y{/key}"))
    assertTrue(errs.toString(), errs.any { it.contains("not valid inside") })
  }

  fun testCloseWithoutOpen() {
    val errs = errors(parse("<p>x</p>{/if}"))
    assertTrue(errs.toString(), errs.any { it.contains("no matching") })
  }

  fun testBlockClosesUnclosedTag() {
    // <div> opened inside the block must be closed inside it
    val errs = errors(parse("{#if a}<div>x{/if}</div>"))
    assertTrue(errs.toString(), errs.isNotEmpty())
  }

  fun testFolding() {
    val file = parse("{#if a}\n  <p>x</p>\n{:else}\n  <p>y</p>\n{/if}\n{#each xs as x}{x}{/each}")
    val regions = SvelteFoldingBuilder().buildFoldRegions(file, myFixture.editor.document, false)
    // if: two multi-line segments; single-line each: none
    assertEquals(2, regions.size)
  }

  fun testAutoCloseBlock() {
    myFixture.configureByText("T.svelte", "<div>{#if count > 0<caret></div>")
    myFixture.type('}')
    myFixture.checkResult("<div>{#if count > 0}<caret>{/if}</div>")
  }

  fun testAutoCloseSkipsWhenAlreadyClosed() {
    myFixture.configureByText("T.svelte", "{#each xs as x<caret>{/each}")
    myFixture.type('}')
    myFixture.checkResult("{#each xs as x}<caret>{/each}")
  }

  fun testNoAutoCloseForExpression() {
    myFixture.configureByText("T.svelte", "<p>{name<caret></p>")
    myFixture.type('}')
    myFixture.checkResult("<p>{name}<caret></p>")
  }
}
