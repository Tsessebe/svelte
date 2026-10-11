package io.github.tsessebe.svelte.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lang.html.HTMLParser
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.lang.html.HtmlParsing
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.tree.IFileElementType
import io.github.tsessebe.svelte.lexer.SvelteLexer
import io.github.tsessebe.svelte.psi.SvelteMustacheElement

class SvelteParserDefinition : HTMLParserDefinition() {
  override fun createLexer(project: Project?): Lexer = SvelteLexer(false)

  override fun createParser(project: Project?): PsiParser = object : HTMLParser() {
    override fun createHtmlParsing(builder: PsiBuilder): HtmlParsing = SvelteParsing(builder)
  }

  override fun getFileNodeType(): IFileElementType = SvelteElementTypes.FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = HtmlFileImpl(viewProvider, SvelteElementTypes.FILE)

  override fun createElement(node: ASTNode): PsiElement =
    if (node.elementType === SvelteElementTypes.MUSTACHE || SvelteElementTypes.BLOCK_TAGS.contains(node.elementType)) SvelteMustacheElement(node)
    else if (SvelteElementTypes.ALL.contains(node.elementType)) ASTWrapperPsiElement(node)
    else super.createElement(node)
}
