package io.github.tsessebe.svelte.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.html.HtmlParsing
import com.intellij.psi.tree.IElementType
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes

/**
 * HTML parsing plus Svelte mustaches and blocks.
 *
 * Blocks ({#if}…{:else}…{/if}) live on HtmlParsing's item stack next to HTML tags, so HTML and blocks must nest
 * properly, as the Svelte compiler requires: a {/if} closes any HTML tags still open inside the block, and an end tag
 * cannot close a tag that was opened outside the current block.
 */
class SvelteParsing(builder: PsiBuilder) : HtmlParsing(builder) {

  private inner class BlockItem(val name: String, val marker: PsiBuilder.Marker) : HtmlParserStackItem {
    override fun done(builder: PsiBuilder, beforeMarker: PsiBuilder.Marker?, incomplete: Boolean) {
      val type = SvelteElementTypes.blockType(name)
      if (beforeMarker == null) {
        if (incomplete) builder.error("{#$name} block is not closed; expected {/$name}")
        marker.done(type)
      }
      else {
        if (incomplete) beforeMarker.precede().errorBefore("{#$name} block is not closed; expected {/$name}", beforeMarker)
        marker.doneBefore(type, beforeMarker)
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // hooks

  override fun hasCustomTopLevelContent(): Boolean = atMustache()

  override fun parseCustomTopLevelContent(error: PsiBuilder.Marker?): PsiBuilder.Marker? {
    flushError(error)
    parseContentMustache()
    return null
  }

  override fun hasCustomTagContent(): Boolean = atMustache()

  override fun parseCustomTagContent(xmlText: PsiBuilder.Marker?): PsiBuilder.Marker? {
    terminateText(xmlText)
    parseContentMustache()
    return null
  }

  /** {...spread} and {shorthand} in attribute position. */
  override fun hasCustomTagHeaderContent(): Boolean = token() === SvelteTokenTypes.LBRACE

  override fun parseCustomTagHeaderContent() = parseMustache(SvelteElementTypes.MUSTACHE)

  /** attr={expr} */
  override fun hasCustomAttributeValue(): Boolean = token() === SvelteTokenTypes.LBRACE

  override fun parseCustomAttributeValue() = parseMustache(SvelteElementTypes.MUSTACHE)

  /** attr="text {expr} text" */
  override fun hasCustomAttributeValueContent(): Boolean = token() === SvelteTokenTypes.LBRACE

  override fun parseCustomAttributeValueContent() = parseMustache(SvelteElementTypes.MUSTACHE)

  // ---------------------------------------------------------------------------------------------
  // mustaches and blocks in content

  private fun atMustache(): Boolean =
    token() === SvelteTokenTypes.LBRACE || token() === SvelteTokenTypes.RBRACE || SvelteTokenTypes.MUSTACHE_INNER.contains(token())

  /** Keyword right after '{' ("#if", ":else", "/each"), or null. Whitespace is skipped by the builder. */
  private fun blockKeyword(): String? {
    if (token() !== SvelteTokenTypes.LBRACE) return null
    val m = mark()
    advance()
    val text = if (token() === SvelteTokenTypes.BLOCK_KEYWORD) builder.tokenText else null
    m.rollbackTo()
    return text
  }

  private fun parseContentMustache() {
    if (token() !== SvelteTokenTypes.LBRACE) {
      val m = mark()
      advance()
      m.error(if (token() === SvelteTokenTypes.RBRACE) "Unexpected '}'" else "Unexpected token")
      return
    }
    val keyword = blockKeyword()
    if (keyword == null || keyword.length < 2) {
      parseMustache(SvelteElementTypes.MUSTACHE)
      return
    }
    val name = keyword.substring(1)
    when (keyword[0]) {
      '#' -> openBlock(name)
      ':' -> branchBlock(name)
      '/' -> closeBlock(name)
      else -> parseMustache(SvelteElementTypes.MUSTACHE)
    }
  }

  private fun openBlock(name: String) {
    val block = mark()
    parseMustache(SvelteElementTypes.BLOCK_OPEN)
    pushItemToStack(BlockItem(name, block))
  }

  private fun branchBlock(branch: String) {
    val block = findOpenBlock()
    if (block == null) {
      parseMustache(SvelteElementTypes.BLOCK_BRANCH, "{:$branch} must be inside a block")
      return
    }
    closeTagsAboveBlock()
    val error = if (!SvelteElementTypes.isValidBranch(block.name, branch)) "{:$branch} is not valid inside {#${block.name}}" else null
    parseMustache(SvelteElementTypes.BLOCK_BRANCH, error)
  }

  private fun closeBlock(name: String) {
    val block = findOpenBlock()
    if (block == null) {
      parseMustache(SvelteElementTypes.BLOCK_CLOSE, "{/$name} has no matching {#$name}")
      return
    }
    closeTagsAboveBlock()
    val error = if (block.name != name) "Expected {/${block.name}}, found {/$name}" else null
    parseMustache(SvelteElementTypes.BLOCK_CLOSE, error)
    popItemFromStack().done(builder, null, false)
  }

  /** Nearest open block; tags above it are fine (they get closed), but not another block boundary. */
  private fun findOpenBlock(): BlockItem? {
    var found: BlockItem? = null
    processStackItems { item ->
      if (item is BlockItem) {
        found = item
        false
      }
      else true
    }
    return found
  }

  /** HTML tags still open inside the block end where the block tag starts. */
  private fun closeTagsAboveBlock() {
    val before = mark()
    flushIncompleteStackItemsWhile(before) { it !is BlockItem }
    before.drop()
  }

  // ---------------------------------------------------------------------------------------------

  private fun parseMustache(type: IElementType, error: String? = null) {
    val m = mark()
    advance() // {
    while (SvelteTokenTypes.MUSTACHE_INNER.contains(token())) advance()
    if (token() === SvelteTokenTypes.RBRACE) advance()
    else error("'}' expected")
    if (error != null) {
      m.done(type)
      m.precede().error(error)
    }
    else {
      m.done(type)
    }
  }

}
