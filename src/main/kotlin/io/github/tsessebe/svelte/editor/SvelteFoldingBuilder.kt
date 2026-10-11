package io.github.tsessebe.svelte.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import io.github.tsessebe.svelte.parser.SvelteElementTypes

/**
 * Folds each block body: {#if x}…{/if} becomes {#if x}…{/if}, and each branch ({:else}) folds separately,
 * so the visible tags stay readable. HTML tag folding still comes from the HTML folding builder.
 */
class SvelteFoldingBuilder : FoldingBuilderEx(), DumbAware {

  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    val result = mutableListOf<FoldingDescriptor>()
    PsiTreeUtil.processElements(root) { element ->
      if (SvelteElementTypes.BLOCKS.contains(element.elementType)) foldBlock(element, document, result)
      true
    }
    return result.toTypedArray()
  }

  /** One region per segment between consecutive block tags (open → branch → … → close). */
  private fun foldBlock(block: PsiElement, document: Document, out: MutableList<FoldingDescriptor>) {
    val tags = block.children.filter { SvelteElementTypes.BLOCK_TAGS.contains(it.elementType) }
    for (i in 0 until tags.size - 1) {
      val start = tags[i].textRange.endOffset
      val end = tags[i + 1].textRange.startOffset
      if (end > start && document.getLineNumber(start) != document.getLineNumber(end)) {
        out += FoldingDescriptor(block.node, TextRange(start, end), null, "…")
      }
    }
    // Unclosed block: fold to its end if it spans lines.
    if (tags.size == 1) {
      val start = tags[0].textRange.endOffset
      val end = block.textRange.endOffset
      if (end > start && document.getLineNumber(start) != document.getLineNumber(end)) {
        out += FoldingDescriptor(block.node, TextRange(start, end), null, "…")
      }
    }
  }

  override fun getPlaceholderText(node: ASTNode): String = "…"

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
