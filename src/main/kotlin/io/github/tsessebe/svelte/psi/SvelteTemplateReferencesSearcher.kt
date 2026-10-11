package io.github.tsessebe.svelte.psi

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import io.github.tsessebe.svelte.lang.SvelteFileType

/** Find Usages / usage counts / rename: adds template references to <script> declarations of the same component. */
class SvelteTemplateReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val target = params.elementToSearch
    val file = target.containingFile ?: return
    if (file.fileType !is SvelteFileType || !SvelteScriptScope.isTemplateVisible(target)) return
    for (mustache in PsiTreeUtil.findChildrenOfType(file, SvelteMustacheElement::class.java)) {
      for (ref in mustache.references) {
        if (ref.isReferenceTo(target) && !consumer.process(ref)) return
      }
    }
  }
}
