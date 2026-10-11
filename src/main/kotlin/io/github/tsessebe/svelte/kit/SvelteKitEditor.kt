package io.github.tsessebe.svelte.kit

import com.intellij.ide.IconProvider
import com.intellij.ide.projectView.ProjectViewNestingRulesProvider
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import javax.swing.Icon

/** Tabs: "/members (+page)" instead of twenty-two tabs all called "+page.svelte". */
class SvelteKitTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    SvelteKitRoutes.kind(file) ?: return null
    val label = if (file.extension == "svelte") file.nameWithoutExtension else file.name
    return "${SvelteKitRoutes.routePath(file)} ($label)"
  }
}

/** Distinct icons for page / layout / load / server / error route files. */
class SvelteKitIconProvider : IconProvider(), DumbAware {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    val file = (element as? PsiFile)?.virtualFile ?: return null
    return SvelteKitRoutes.kind(file)?.icon
  }
}

/** Project view nests +page.ts / +page.server.ts under +page.svelte (same for +layout). */
class SvelteKitNestingRulesProvider : ProjectViewNestingRulesProvider {
  override fun addFileNestingRules(consumer: ProjectViewNestingRulesProvider.Consumer) {
    for (child in listOf(".ts", ".js", ".server.ts", ".server.js")) consumer.addNestingRule(".svelte", child)
  }
}

/** Navigate | Related Symbol (Ctrl+Alt+Home): the other files of the route and the enclosing layouts. */
class SvelteKitGotoRelatedProvider : GotoRelatedProvider() {
  override fun getItems(psiElement: PsiElement): List<GotoRelatedItem> {
    val file = psiElement.containingFile?.originalFile?.virtualFile ?: return emptyList()
    if (!SvelteKitRoutes.isRouteFile(file)) return emptyList()
    val psiManager = PsiManager.getInstance(psiElement.project)
    return SvelteKitRoutes.relatedFiles(file).mapNotNull { related ->
      psiManager.findFile(related)?.let { GotoRelatedItem(it, "SvelteKit route ${SvelteKitRoutes.routePath(related)}") }
    }
  }
}
