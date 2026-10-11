package io.github.tsessebe.svelte.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.HtmlLexer
import com.intellij.lexer.LexerPosition

/**
 * HtmlLexer over the Svelte flex lexer. HtmlLexer takes care of <script>/<style> embedding (JS/TS/CSS)
 * and, in highlight mode, of the embedded highlighting lexers.
 */
class SvelteLexer(highlightMode: Boolean) :
  // No MergingLexerAdapter: it saves/restores positions by restarting its delegate, which would drop the queued
  // mustache sub-tokens. The flex rules are greedy instead.
  HtmlLexer(SvelteFlexAdapter(highlightMode), true, highlightMode) {

  override fun isHtmlTagState(state: Int): Boolean =
    state == _SvelteLexer.START_TAG_NAME || state == _SvelteLexer.END_TAG_NAME

  /** Mid-mustache positions depend on the queued tokens; never restart there. */
  override fun isRestartableState(state: Int): Boolean =
    super.isRestartableState(state) && (state and BASE_STATE_MASK) != _SvelteLexer.MUSTACHE_PENDING

  override fun getStateForRestartDuringEmbedmentScan(): Int = _SvelteLexer.YYINITIAL

  /**
   * After an embedment HtmlLexer restarts the delegate at the embedment end. Inside a mustache that would drop the
   * queued sub-tokens, so when the embedment is exactly the current delegate token (mustache expressions, and
   * script/style content which is a single merged token) just advance instead.
   */
  override fun restartAfterEmbedment(offset: Int, baseLexerState: Int) {
    if (myDelegate.tokenType != null && myDelegate.tokenEnd == offset) {
      myDelegate.advance()
    }
    else {
      super.restartAfterEmbedment(offset, baseLexerState)
    }
  }

}

class SvelteFlexAdapter(highlightMode: Boolean) : FlexAdapter(_SvelteLexer(highlightMode)) {
  private val svelteFlex: _SvelteLexer get() = flex as _SvelteLexer

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    svelteFlex.clearPending()
    // A restart can never resume a half-emitted mustache; re-lex from data state instead.
    val state = if (initialState == _SvelteLexer.MUSTACHE_PENDING) _SvelteLexer.YYINITIAL else initialState
    super.start(buffer, startOffset, endOffset, state)
    svelteFlex.recoverContext(startOffset, state)
  }

  override fun getCurrentPosition(): LexerPosition =
    SveltePosition(super.getCurrentPosition(), svelteFlex.savePending())

  override fun restore(position: LexerPosition) {
    val p = position as SveltePosition
    super.restore(p.delegate)
    svelteFlex.restorePending(p.pending)
  }

  private class SveltePosition(val delegate: LexerPosition, val pending: Array<Any>?) : LexerPosition {
    override fun getOffset(): Int = delegate.offset
    override fun getState(): Int = delegate.state
  }
}
