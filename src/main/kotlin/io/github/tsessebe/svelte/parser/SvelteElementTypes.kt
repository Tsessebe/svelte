package io.github.tsessebe.svelte.parser

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import io.github.tsessebe.svelte.lang.SvelteHtmlLanguage

class SvelteElementType(debugName: String) : IElementType(debugName, SvelteHtmlLanguage.INSTANCE)

object SvelteElementTypes {
  @JvmField val FILE = IFileElementType("SVELTE_FILE", SvelteHtmlLanguage.INSTANCE)

  /** {expr}, {@html ...}, {@render ...}, {@const ...}, {...spread} — anything that is not a block tag. */
  @JvmField val MUSTACHE = SvelteElementType("SVELTE_MUSTACHE")

  // Block tags: the mustaches that open, branch and close a block.
  @JvmField val BLOCK_OPEN = SvelteElementType("SVELTE_BLOCK_OPEN")      // {#if x}
  @JvmField val BLOCK_BRANCH = SvelteElementType("SVELTE_BLOCK_BRANCH")  // {:else}, {:else if y}, {:then v}, {:catch e}
  @JvmField val BLOCK_CLOSE = SvelteElementType("SVELTE_BLOCK_CLOSE")    // {/if}

  // Whole blocks: open tag, content, branches, close tag.
  @JvmField val IF_BLOCK = SvelteElementType("SVELTE_IF_BLOCK")
  @JvmField val EACH_BLOCK = SvelteElementType("SVELTE_EACH_BLOCK")
  @JvmField val AWAIT_BLOCK = SvelteElementType("SVELTE_AWAIT_BLOCK")
  @JvmField val KEY_BLOCK = SvelteElementType("SVELTE_KEY_BLOCK")
  @JvmField val SNIPPET_BLOCK = SvelteElementType("SVELTE_SNIPPET_BLOCK")
  @JvmField val UNKNOWN_BLOCK = SvelteElementType("SVELTE_UNKNOWN_BLOCK")

  @JvmField val BLOCKS: TokenSet = TokenSet.create(IF_BLOCK, EACH_BLOCK, AWAIT_BLOCK, KEY_BLOCK, SNIPPET_BLOCK, UNKNOWN_BLOCK)
  @JvmField val BLOCK_TAGS: TokenSet = TokenSet.create(BLOCK_OPEN, BLOCK_BRANCH, BLOCK_CLOSE)
  @JvmField val ALL: TokenSet = TokenSet.orSet(BLOCKS, BLOCK_TAGS, TokenSet.create(MUSTACHE))

  @JvmStatic
  fun blockType(name: String): IElementType = when (name) {
    "if" -> IF_BLOCK
    "each" -> EACH_BLOCK
    "await" -> AWAIT_BLOCK
    "key" -> KEY_BLOCK
    "snippet" -> SNIPPET_BLOCK
    else -> UNKNOWN_BLOCK
  }

  /** Branch keywords allowed per block. */
  @JvmStatic
  fun isValidBranch(block: String, branch: String): Boolean = when (block) {
    "if" -> branch == "else"
    "each" -> branch == "else"
    "await" -> branch == "then" || branch == "catch"
    else -> false
  }
}
