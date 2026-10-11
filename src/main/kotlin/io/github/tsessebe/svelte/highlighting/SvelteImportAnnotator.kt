package io.github.tsessebe.svelte.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import io.github.tsessebe.svelte.lang.SvelteFileType
import io.github.tsessebe.svelte.lexer.SvelteTokenTypes

/**
 * Colours imported names in .svelte files: unused → greyed out, imports used as tags → component colour,
 * other used imports → "used import" colour.
 *
 * "Used" is decided from the whole component: script identifiers, mustache expressions/bindings, component tags
 * (<Card>, <Card.Header>), directive names (use:tooltip, transition:fade) and $store references.
 * Deliberately lexical: cheap, and never claims something unused that the template uses.
 */
class SvelteImportAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is ES6ImportDeclaration) return
    val file = element.containingFile ?: return
    if (file.fileType !is SvelteFileType) return
    val usage = usage(file)

    val bindings = element.importedBindings.map { it as PsiNamedElement } +
                   element.importSpecifiers.map { spec -> (spec.alias ?: spec) as PsiNamedElement }
    for (binding in bindings) {
      val name = binding.name ?: continue
      // Colour just the local name (for `type Item` the specifier also covers the `type` keyword).
      val target = (binding as? PsiNameIdentifierOwner)?.nameIdentifier
                   ?: PsiTreeUtil.collectElements(binding) { it.elementType === JSTokenTypes.IDENTIFIER && it.text == name }.lastOrNull()
                   ?: binding
      val key: TextAttributesKey = when {
        name !in usage.names -> HighlightInfoType.UNUSED_SYMBOL.attributesKey
        name in usage.tags -> SvelteHighlighterColors.COMPONENT
        else -> SvelteHighlighterColors.USED_IMPORT
      }
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(target).textAttributes(key).create()
    }
  }

  companion object {
    private val WORD = Regex("""[A-Za-z_$][\w$]*""")

    class Usage(val names: Set<String>, val tags: Set<String>)

    fun usage(file: PsiFile): Usage = CachedValuesManager.getCachedValue(file) {
      CachedValueProvider.Result.create(collect(file), PsiModificationTracker.MODIFICATION_COUNT)
    }

    private fun collect(file: PsiFile): Usage {
      val names = HashSet<String>()
      val tags = HashSet<String>()
      fun add(word: String) {
        names += word
        if (word.length > 1 && word[0] == '$') names += word.substring(1) // $store → store
      }
      PsiTreeUtil.processElements(file) { e ->
        when {
          e.elementType === JSTokenTypes.IDENTIFIER ->
            if (PsiTreeUtil.getParentOfType(e, ES6ImportDeclaration::class.java) == null) add(e.text)

          e.elementType === SvelteTokenTypes.EXPRESSION || e.elementType === SvelteTokenTypes.BINDING ->
            WORD.findAll(e.text).forEach { add(it.value) }

          e is XmlTag -> e.name.substringBefore('.').let { add(it); tags += it }

          e is XmlAttribute && e.name.contains(':') ->
            add(e.name.substringAfter(':').substringBefore('|'))
        }
        true
      }
      return Usage(names, tags)
    }
  }
}
