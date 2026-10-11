package io.github.tsessebe.svelte.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import io.github.tsessebe.svelte.lang.SvelteFileType

/**
 * In .svelte files: JS/TS inspections are left to the Svelte language server, and HTML inspections must not flag
 * Svelte constructs:
 * component tags (<Card>, <Foo.Bar>), svelte: elements, directive attributes (bind:x, on:click, class:active).
 * Plain HTML tags/attributes are still checked.
 */
class SvelteInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element.containingFile?.fileType !is SvelteFileType) return false
    // JS/TS semantics in .svelte files belong to the Svelte language server (it understands runes, <script module>
    // exports, props types…). WebStorm's own analysis of the embedded script gives false errors there.
    if (isJsSemanticInspection(toolId)) return true
    // WebStorm's CSS support doesn't know Svelte's :global(...), so custom properties declared inside it don't
    // resolve and the pseudo-class is "invalid". The Svelte compiler checks styles itself.
    if (toolId in CSS_SVELTE_FALSE_POSITIVES) return true
    return isSuppressedHtml(element, toolId)
  }

  private fun isSuppressedHtml(element: PsiElement, toolId: String): Boolean = when (toolId) {
    // CheckEmptyScriptTag = "Empty tag doesn't work in some browsers"; self-closing components are normal Svelte.
    "HtmlUnknownTag", "HtmlExtraClosingTag", "CheckEmptyScriptTag" ->
      PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)?.let { isSvelteTag(it.name) } ?: false

    "HtmlUnknownAttribute", "HtmlUnknownBooleanAttribute" -> {
      val attr = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
      val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
      (attr != null && (isDirective(attr.name) || isSvelteScriptAttribute(tag, attr.name))) || (tag != null && isSvelteTag(tag.name))
    }

    // disabled={busy}, aria-busy={x}, href="/a/{id}": the HTML checks see the literal {…} text, not the value.
    "HtmlWrongAttributeValue", "HtmlUnknownTarget", "HtmlUnknownAnchorTarget" ->
      PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)?.value?.contains('{') ?: false

    "XmlUnboundNsPrefix" -> {
      val attr = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
      val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
      (attr != null && isDirective(attr.name)) || (tag != null && tag.name.startsWith("svelte:"))
    }

    else -> false
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY

  companion object {
    private val CSS_SVELTE_FALSE_POSITIVES = setOf("CssUnresolvedCustomProperty", "CssInvalidPseudoSelector")

    /** JS/TS inspections are prefixed like this (TypeScriptValidateTypes, JSUnresolvedReference, ES6UnusedImports…). */
    private val JS_PREFIXES = listOf("TypeScript", "JS", "ES6", "Js", "TsLint", "ESLint", "Eslint")

    fun isJsSemanticInspection(toolId: String): Boolean = JS_PREFIXES.any { toolId.startsWith(it) }

    /** <script module>, <script context="module">, <script generics="T">, <style global>. */
    private fun isSvelteScriptAttribute(tag: XmlTag?, name: String): Boolean = when (tag?.name?.lowercase()) {
      "script" -> name in setOf("module", "context", "generics", "lang")
      "style" -> name in setOf("global", "lang")
      else -> false
    }

    private val DIRECTIVES = setOf("bind", "on", "class", "style", "use", "transition", "in", "out", "animate", "let")

    fun isSvelteTag(name: String): Boolean =
      name.startsWith("svelte:") || name.contains('.') || (name.isNotEmpty() && name[0].isUpperCase())

    fun isDirective(name: String): Boolean = name.substringBefore(':', "").let { it.isNotEmpty() && it in DIRECTIVES }
  }
}
