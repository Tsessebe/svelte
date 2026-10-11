package io.github.tsessebe.svelte.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.github.tsessebe.svelte.lang.SvelteFileType

/**
 * Typing the '}' that completes {#if …}, {#each …}, {#await …}, {#key …} or {#snippet …}
 * inserts the matching {/name} after the caret (unless it is already there).
 */
class SvelteTypedHandler : TypedHandlerDelegate() {

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (c != '}' || file.fileType !is SvelteFileType) return Result.CONTINUE
    val document = editor.document
    val caret = editor.caretModel.offset
    val text = document.charsSequence
    val name = openedBlockName(text, caret) ?: return Result.CONTINUE

    val close = "{/$name}"
    var i = caret
    while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
    if (text.startsWith(close, i)) return Result.CONTINUE

    document.insertString(caret, close)
    return Result.STOP
  }

  companion object {
    private val BLOCKS = setOf("if", "each", "await", "key", "snippet")

    /** If text just before [caret] ends a `{#name …}` tag on the same line, returns name. */
    fun openedBlockName(text: CharSequence, caret: Int): String? {
      if (caret < 2 || text[caret - 1] != '}') return null
      var i = caret - 2
      var depth = 0
      while (i >= 0 && text[i] != '\n') {
        when (text[i]) {
          '}' -> depth++
          '{' -> if (depth == 0) break else depth--
        }
        i--
      }
      if (i < 0 || text[i] != '{') return null
      var k = i + 1
      while (k < caret && text[k].isWhitespace()) k++
      if (k >= caret || text[k] != '#') return null
      val nameStart = k + 1
      var e = nameStart
      while (e < caret && text[e].isLetter()) e++
      val name = text.subSequence(nameStart, e).toString()
      return name.takeIf { it in BLOCKS }
    }
  }
}
