package io.github.tsessebe.svelte.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import io.github.tsessebe.svelte.docs.SvelteDocs
import io.github.tsessebe.svelte.lang.SvelteFileType

/**
 * Learning aid: points at Svelte 4 syntax in a Svelte 5 project, says what the Svelte 5 equivalent is,
 * and offers fixes (on:click → onclick, or migrate the whole component with the Svelte compiler).
 */
class SvelteLegacySyntaxInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (file.fileType !is SvelteFileType || !isSvelte5Project(file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : XmlElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val name = tag.name
        when {
          name == "script" -> if (isInstanceScript(tag)) checkScript(tag, holder)
          name == "slot" -> report(holder, tag.firstNameElement() ?: tag, "slot", "<slot> is Svelte 4 syntax")
          name == "svelte:component" || name == "svelte:self" || name == "svelte:fragment" ->
            report(holder, tag.firstNameElement() ?: tag, name, "<$name> is Svelte 4 syntax")
        }
      }

      override fun visitXmlAttribute(attribute: XmlAttribute) {
        val name = attribute.name
        val nameElement = attribute.nameElement ?: return
        when {
          name.startsWith("on:") -> {
            val event = name.removePrefix("on:")
            val tag = attribute.parent
            val canQuickFix = !event.contains('|') && tag != null && tag.name.firstOrNull()?.isLowerCase() == true &&
                              !tag.name.contains(':') && attribute.valueElement?.text?.startsWith("{") == true
            val fixes = if (canQuickFix) arrayOf<LocalQuickFix>(OnDirectiveToAttributeFix(event), MigrateComponentFix()) else arrayOf(MigrateComponentFix())
            holder.registerProblem(nameElement, message("on:", "$name is Svelte 4 syntax"), ProblemHighlightType.WEAK_WARNING, *fixes)
          }
          name.startsWith("let:") -> report(holder, nameElement, "let:", "$name is Svelte 4 syntax")
        }
      }
    }
  }

  private fun checkScript(script: XmlTag, holder: ProblemsHolder) {
    val content = script.value.textRange
    if (content.isEmpty) return
    val file = script.containingFile
    val text = content.subSequence(file.text)
    fun find(regex: Regex, key: String, what: String) {
      regex.findAll(text).forEach { m ->
        val group = m.groups[1] ?: return@forEach
        val range = TextRange(content.startOffset + group.range.first, content.startOffset + group.range.last + 1)
        holder.registerProblem(holder.manager.createProblemDescriptor(
          file, range, message(key, what), ProblemHighlightType.WEAK_WARNING, holder.isOnTheFly, MigrateComponentFix()))
      }
    }
    find(Regex("""(?m)^\s*(export)\s+let\b"""), "export let", "'export let' props are Svelte 4 syntax")
    find(Regex("""(?m)^\s*(\$:)"""), "\$:", "'\$:' reactive statements are Svelte 4 syntax")
    find(Regex("""\b(createEventDispatcher)\b"""), "createEventDispatcher", "createEventDispatcher is Svelte 4 syntax")
    find(Regex("""(\$\$(?:props|restProps|slots))\b"""), "\$\$props", "\$\$props / \$\$restProps / \$\$slots are Svelte 4 syntax")
  }

  private fun report(holder: ProblemsHolder, element: PsiElement, key: String, what: String) {
    holder.registerProblem(element, message(key, what), ProblemHighlightType.WEAK_WARNING, MigrateComponentFix())
  }

  private fun message(key: String, what: String): String {
    val svelte5 = SvelteDocs.entries[key]?.svelte5?.replace(Regex("<[^>]+>"), "")?.replace("&lt;", "<")?.replace("&gt;", ">")
    return if (svelte5 != null) "$what. Svelte 5: $svelte5" else what
  }

  companion object {
    /** <script> without module / context="module". */
    fun isInstanceScript(tag: XmlTag): Boolean =
      tag.getAttribute("module") == null && tag.getAttributeValue("context") != "module"

    /** Svelte 5+ in the nearest package.json; no package.json → assume 5. */
    fun isSvelte5Project(file: PsiFile): Boolean {
      val vFile = file.originalFile.virtualFile ?: return true
      val packageJson = PackageJsonUtil.findUpPackageJson(vFile) ?: return true
      val version = PackageJsonData.getOrCreate(packageJson).allDependencyEntries["svelte"]?.versionRange ?: return true
      val major = Regex("""\d+""").find(version)?.value?.toIntOrNull() ?: return true
      return major >= 5
    }

    private fun XmlTag.firstNameElement(): PsiElement? = node.findChildByType(com.intellij.psi.xml.XmlTokenType.XML_NAME)?.psi
  }
}

/** on:click={handler} → onclick={handler} (plain DOM elements, no modifiers). */
class OnDirectiveToAttributeFix(private val event: String) : LocalQuickFix {
  override fun getFamilyName(): String = "Replace with Svelte 5 event attribute"
  override fun getName(): String = "Replace 'on:$event' with 'on$event'"

  // Plain text edit of the name: XmlAttribute.setName() rebuilds the attribute through the HTML factory,
  // which doesn't know Svelte's {expr} values.
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val nameElement = descriptor.psiElement ?: return
    val document = PsiDocumentManager.getInstance(project).getDocument(nameElement.containingFile) ?: return
    val range = nameElement.textRange
    document.replaceString(range.startOffset, range.endOffset, "on$event")
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }
}
