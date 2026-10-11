package io.github.tsessebe.svelte.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.javascript.JSFlexAdapter
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes
import io.github.tsessebe.svelte.parser.SvelteElementTypes

/**
 * Any {...} (expression, block tag, attribute value). Identifiers in its expressions reference the component's
 * <script> declarations, so WebStorm's own navigation, Find Usages, usage counts, highlighting and rename see
 * template usages.
 */
class SvelteMustacheElement(node: ASTNode) : ASTWrapperPsiElement(node) {

  override fun getReferences(): Array<PsiReference> = CachedValuesManager.getCachedValue(this) {
    CachedValueProvider.Result.create(computeReferences(), PsiModificationTracker.MODIFICATION_COUNT)
  }

  private fun computeReferences(): Array<PsiReference> {
    val refs = mutableListOf<PsiReference>()
    val start = textRange.startOffset
    var child = node.firstChildNode
    while (child != null) {
      if (child.elementType === SvelteTokenTypes.EXPRESSION) {
        val base = child.startOffset - start
        for ((name, range) in identifiers(child.chars)) refs += SvelteScriptReference(this, range.shiftRight(base), name)
      }
      child = child.treeNext
    }
    return refs.toTypedArray()
  }

  /** Names declared by the blocks around this mustache: {#each … as x}, {:then v}, {#snippet s(p)}, {@const c = …}. */
  fun blockLocalNames(): Set<String> {
    val names = HashSet<String>()
    var block: PsiElement? = parent
    while (block != null) {
      if (SvelteElementTypes.BLOCKS.contains(block.elementType)) {
        for (tag in block.children) {
          if (SvelteElementTypes.BLOCK_TAGS.contains(tag.elementType)) {
            var c = tag.node.firstChildNode
            while (c != null) {
              if (c.elementType === SvelteTokenTypes.BINDING) WORD.findAll(c.chars).forEach { names += it.value }
              c = c.treeNext
            }
          }
          if (tag.elementType === SvelteElementTypes.MUSTACHE && tag.node.findChildByType(SvelteTokenTypes.SPECIAL_KEYWORD)?.text == "@const") {
            tag.node.findChildByType(SvelteTokenTypes.EXPRESSION)?.text?.substringBefore('=')?.let { lhs -> WORD.findAll(lhs).forEach { names += it.value } }
          }
        }
        // snippet name is visible to the rest of the parent too, but its params only inside
      }
      block = block.parent
    }
    return names
  }

  companion object {
    private val WORD = Regex("""[A-Za-z_$][\w$]*""")

    /** Free identifiers in a JS/TS expression: not property names (a.b, a?.b) and not object-literal keys ({k: v}). */
    fun identifiers(text: CharSequence): List<Pair<String, TextRange>> {
      val lexer = JSFlexAdapter(JavaScriptSupportLoader.TYPESCRIPT.optionHolder)
      lexer.start(text)
      data class Tok(val type: com.intellij.psi.tree.IElementType, val start: Int, val end: Int)
      val toks = mutableListOf<Tok>()
      while (lexer.tokenType != null) {
        val t = lexer.tokenType!!
        if (t !== com.intellij.psi.TokenType.WHITE_SPACE && t !== JSTokenTypes.WHITE_SPACE) toks += Tok(t, lexer.tokenStart, lexer.tokenEnd)
        lexer.advance()
      }
      val out = mutableListOf<Pair<String, TextRange>>()
      for ((i, tok) in toks.withIndex()) {
        if (tok.type !== JSTokenTypes.IDENTIFIER) continue
        val prev = toks.getOrNull(i - 1)?.type
        val next = toks.getOrNull(i + 1)?.type
        if (prev === JSTokenTypes.DOT || prev === JSTokenTypes.ELVIS) continue
        if (next === JSTokenTypes.COLON && (prev === JSTokenTypes.LBRACE || prev === JSTokenTypes.COMMA)) continue
        out += text.subSequence(tok.start, tok.end).toString() to TextRange(tok.start, tok.end)
      }
      return out
    }
  }
}

/** `name` in a template expression → its <script> declaration ($store → store). */
class SvelteScriptReference(element: SvelteMustacheElement, range: TextRange, private val name: String) :
  PsiReferenceBase<SvelteMustacheElement>(element, range, true) {

  override fun resolve(): PsiElement? {
    val declarations = SvelteScriptScope.declarations(element.containingFile)
    if (name in element.blockLocalNames()) return null
    return declarations[name] ?: (if (name.length > 1 && name.startsWith("$")) declarations[name.substring(1)] else null)
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    val prefix = if (name.startsWith("$") && SvelteScriptScope.declarations(element.containingFile)[name] == null) "$" else ""
    return super.handleElementRename(prefix + newElementName)
  }
}

/** Text replacement inside a mustache (used by rename). */
class SvelteMustacheManipulator : AbstractElementManipulator<SvelteMustacheElement>() {
  override fun handleContentChange(element: SvelteMustacheElement, range: TextRange, newContent: String): SvelteMustacheElement {
    val file = element.containingFile
    val manager = PsiDocumentManager.getInstance(element.project)
    val document = manager.getDocument(file) ?: return element
    manager.doPostponedOperationsAndUnblockDocument(document)
    val absolute = range.shiftRight(element.textRange.startOffset)
    document.replaceString(absolute.startOffset, absolute.endOffset, newContent)
    manager.commitDocument(document)
    return PsiTreeUtil.getParentOfType(file.findElementAt(absolute.startOffset), SvelteMustacheElement::class.java) ?: element
  }
}
