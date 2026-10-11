package io.github.tsessebe.svelte.docs

import com.intellij.lang.Language
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import io.github.tsessebe.svelte.lang.SvelteFileType
import io.github.tsessebe.svelte.lang.SvelteHtmlLanguage
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes

/** Quick documentation (Ctrl+Q / hover) for Svelte template syntax. Script code is documented by the language server. */
class SvelteDocumentationProvider : AbstractDocumentationProvider() {

  override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
    if (file.fileType !is SvelteFileType || contextElement == null) return null
    val key = keyFor(contextElement) ?: return null
    return SvelteDocs.entries[key]?.let { SvelteDocElement(key, contextElement) }
  }

  override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    val doc = element as? SvelteDocElement ?: return null
    return SvelteDocs.entries[doc.key]?.let(SvelteDocs::html)
  }

  override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? = generateDoc(element, originalElement)

  override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? =
    (element as? SvelteDocElement)?.let { SvelteDocs.entries[it.key] }?.let { listOf(SvelteDocs.url(it)) }

  companion object {
    private val DIRECTIVES = setOf("bind", "on", "class", "style", "use", "transition", "in", "out", "animate", "let")

    fun keyFor(leaf: PsiElement): String? {
      val text = leaf.text
      when (leaf.elementType) {
        SvelteTokenTypes.BLOCK_KEYWORD -> return if (text.startsWith("/")) "#" + text.substring(1) else text
        SvelteTokenTypes.SPECIAL_KEYWORD -> return text
        SvelteTokenTypes.INNER_KEYWORD -> return when (text) {
          "then" -> ":then"
          "catch" -> ":catch"
          "as" -> "#each"
          "if" -> ":else"
          else -> null
        }
      }
      PsiTreeUtil.getParentOfType(leaf, XmlAttribute::class.java, false)?.let { attr ->
        if (attr.nameElement == leaf) {
          val prefix = attr.name.substringBefore(':', "")
          return if (prefix in DIRECTIVES) "$prefix:" else null
        }
        return null
      }
      val tag = PsiTreeUtil.getParentOfType(leaf, XmlTag::class.java, false) ?: return null
      if (leaf.parent == tag && leaf.text == tag.name) { // start or end tag name
        return when {
          tag.name.startsWith("svelte:") -> tag.name
          tag.name == "slot" -> "slot"
          else -> null
        }
      }
      return null
    }
  }
}

/** Target of Svelte quick documentation; lives in the Svelte file so this provider is the one asked for the doc. */
class SvelteDocElement(val key: String, private val context: PsiElement) : FakePsiElement() {
  override fun getParent(): PsiElement = context
  override fun getName(): String = key
  override fun getLanguage(): Language = SvelteHtmlLanguage.INSTANCE
  override fun getPresentableText(): String = key
}
