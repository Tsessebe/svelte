package io.github.tsessebe.svelte.psi

import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/** Top-level declarations of a component's <script> blocks (instance + module): what the template can see. */
object SvelteScriptScope {

  fun declarations(file: PsiFile): Map<String, PsiNamedElement> = CachedValuesManager.getCachedValue(file) {
    CachedValueProvider.Result.create(collect(file), PsiModificationTracker.MODIFICATION_COUNT)
  }

  fun isTemplateVisible(element: PsiElement): Boolean {
    val file = element.containingFile ?: return false
    val name = (element as? PsiNamedElement)?.name ?: return false
    return declarations(file)[name] == element
  }

  private fun collect(file: PsiFile): Map<String, PsiNamedElement> {
    val result = LinkedHashMap<String, PsiNamedElement>()
    val scripts = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).filter { it.name.equals("script", ignoreCase = true) }
    for (script in scripts) {
      // imports
      for (import in PsiTreeUtil.findChildrenOfType(script, ES6ImportDeclaration::class.java)) {
        for (b in import.importedBindings) b.name?.let { result.putIfAbsent(it, b) }
        for (s in import.importSpecifiers) ((s.alias ?: s) as PsiNamedElement).let { n -> n.name?.let { result.putIfAbsent(it, n) } }
      }
      // let/const/var (incl. destructured $props()), functions, classes — top level only
      for (decl in PsiTreeUtil.findChildrenOfAnyType(script, JSVariable::class.java, JSFunction::class.java, JSClass::class.java)) {
        if (decl is JSVariable && PsiTreeUtil.getParentOfType(decl, JSFunction::class.java, JSClass::class.java) != null) continue
        if (decl !is JSVariable && PsiTreeUtil.getParentOfType(decl, JSFunction::class.java, JSClass::class.java) != null) continue
        if (decl is JSFunction && decl.name == null) continue
        decl.name?.let { result.putIfAbsent(it, decl) }
      }
    }
    return result
  }
}
