/*
 * Svelte template lexer.
 * Structure follows the platform's _HtmlLexer.flex (Apache-2.0, JetBrains) so HtmlLexer/HtmlParsing can consume it,
 * plus: mustaches {...} (split by SvelteMustacheScanner), raw <script>/<style> content, and highlight-only splitting
 * of directive attribute names (bind:value|mod) and svelte:/Component tag names.
 */
package io.github.tsessebe.svelte.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import java.util.List;

%%

%unicode

%class _SvelteLexer
%public
%implements FlexLexer
%function advanceInternal
%type IElementType

%ctorarg boolean highlightMode

%init{
  this.highlightMode = highlightMode;
%init}

%{
  private final boolean highlightMode;

  // ---- start tag name tracking (for raw <script>/<style> content) ----
  private int rawKind = RAW_NONE;
  private static final int RAW_NONE = 0;
  private static final int RAW_IN_SCRIPT = 1;
  private static final int RAW_IN_STYLE = 2;
  private static final int IN_COMPONENT_TAG = 3; // not raw: marks attributes as component props (highlight only)

  // ---- queued mustache sub-tokens ----
  private int[] pendingEnds = new int[16];
  private IElementType[] pendingTypes = new IElementType[16];
  private int pendingCount = 0;
  private int pendingIndex = 0;
  private int pendingReturnState = YYINITIAL;

  public _SvelteLexer(boolean highlightMode) {
    this((java.io.Reader)null, highlightMode);
  }

  @Override
  public IElementType advance() throws java.io.IOException {
    if (pendingIndex < pendingCount) {
      zzStartRead = zzMarkedPos;
      zzMarkedPos = pendingEnds[pendingIndex];
      zzCurrentPos = zzMarkedPos;
      IElementType type = pendingTypes[pendingIndex++];
      if (pendingIndex == pendingCount) yybegin(pendingReturnState);
      return type;
    }
    return advanceInternal();
  }

  public void clearPending() {
    pendingCount = 0;
    pendingIndex = 0;
  }

  /**
   * Snapshot for LexerPosition. FlexAdapter.restore() re-creates the current token itself and resets this lexer at the
   * END of the current token, so the snapshot holds only the queued tokens after it.
   */
  public Object[] savePending() {
    return new Object[]{
      java.util.Arrays.copyOfRange(pendingEnds, pendingIndex, pendingCount),
      java.util.Arrays.copyOfRange(pendingTypes, pendingIndex, pendingCount),
      pendingReturnState, rawKind};
  }

  public void restorePending(Object[] saved) {
    clearPending();
    if (saved == null) return;
    int[] ends = (int[])saved[0];
    IElementType[] types = (IElementType[])saved[1];
    ensureCapacity(ends.length);
    System.arraycopy(ends, 0, pendingEnds, 0, ends.length);
    System.arraycopy(types, 0, pendingTypes, 0, types.length);
    pendingCount = ends.length;
    pendingReturnState = (Integer)saved[2];
    rawKind = (Integer)saved[3];
    if (pendingCount > 0) yybegin(MUSTACHE_PENDING);
  }

  private void ensureCapacity(int n) {
    if (pendingEnds.length < n) {
      pendingEnds = java.util.Arrays.copyOf(pendingEnds, n * 2);
      pendingTypes = java.util.Arrays.copyOf(pendingTypes, n * 2);
    }
  }

  /** Lexes a whole mustache starting at the current '{'; returns the first token and queues the rest. */
  private IElementType mustache(int returnState) {
    int start = zzStartRead;
    List<SvelteMustacheScanner.Token> tokens = SvelteMustacheScanner.scan(zzBuffer, start, zzEndRead);
    ensureCapacity(tokens.size());
    clearPending();
    for (int i = 1; i < tokens.size(); i++) {
      SvelteMustacheScanner.Token t = tokens.get(i);
      pendingEnds[pendingCount] = t.end;
      pendingTypes[pendingCount] = SvelteTokenTypes.fromKind(t.kind);
      pendingCount++;
    }
    zzMarkedPos = tokens.get(0).end;
    zzCurrentPos = zzMarkedPos;
    pendingReturnState = returnState;
    if (pendingCount > 0) yybegin(MUSTACHE_PENDING);
    else yybegin(returnState);
    return SvelteTokenTypes.fromKind(tokens.get(0).kind);
  }

  /**
   * Called by the adapter on restart. In tag-header states the start tag name is unknown; recover it from the buffer
   * so a restart inside <script ...> still switches to raw content at '>'.
   */
  public void recoverContext(int startOffset, int state) {
    rawKind = RAW_NONE;
    if (state == BEFORE_TAG_ATTRIBUTES || state == TAG_ATTRIBUTES || state == ATTRIBUTE_VALUE_START
        || state == ATTRIBUTE_VALUE_DQ || state == ATTRIBUTE_VALUE_SQ || state == DIRECTIVE_COLON
        || state == DIRECTIVE_NAME || state == DIRECTIVE_MODIFIERS) {
      int i = Math.min(startOffset, zzBuffer.length()) - 1;
      while (i >= 0 && zzBuffer.charAt(i) != '<') i--;
      if (i >= 0 && i + 1 < zzBuffer.length() && zzBuffer.charAt(i + 1) != '/') {
        int e = i + 1;
        while (e < zzBuffer.length() && !Character.isWhitespace(zzBuffer.charAt(e)) && zzBuffer.charAt(e) != '>' && zzBuffer.charAt(e) != '/') e++;
        setRawKind(zzBuffer.subSequence(i + 1, e));
      }
    }
  }

  private void setRawKind(CharSequence tagName) {
    String n = tagName.toString();
    if (n.equalsIgnoreCase("script")) rawKind = RAW_IN_SCRIPT;
    else if (n.equalsIgnoreCase("style")) rawKind = RAW_IN_STYLE;
    else if (isComponentName(n)) rawKind = IN_COMPONENT_TAG;
    else rawKind = RAW_NONE;
  }

  private static boolean isComponentName(CharSequence n) {
    if (n.length() == 0) return false;
    if (n.length() > 7 && n.subSequence(0, 7).toString().equals("svelte:")) return false;
    if (Character.isUpperCase(n.charAt(0))) return true;
    for (int i = 0; i < n.length(); i++) if (n.charAt(i) == '.') return true;
    return false;
  }

  private IElementType tagEnd() {
    int k = rawKind;
    rawKind = RAW_NONE;
    if (k == RAW_IN_SCRIPT) yybegin(RAW_SCRIPT);
    else if (k == RAW_IN_STYLE) yybegin(RAW_STYLE);
    else yybegin(YYINITIAL);
    return XmlTokenType.XML_TAG_END;
  }

  private static final String[] DIRECTIVES = {
    "bind", "on", "class", "style", "use", "transition", "in", "out", "animate", "let"
  };

  /** In highlight mode, splits "bind:value|mod" into prefix, colon, name, modifiers. */
  private IElementType attributeName() {
    if (highlightMode) {
      CharSequence text = yytext();
      int colon = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == ':') { colon = i; break; }
      }
      if (colon > 0) {
        String prefix = text.subSequence(0, colon).toString();
        for (String d : DIRECTIVES) {
          if (d.equals(prefix)) {
            yypushback(text.length() - colon);
            yybegin(DIRECTIVE_COLON);
            return SvelteTokenTypes.DIRECTIVE_PREFIX;
          }
        }
      }
      if (rawKind == IN_COMPONENT_TAG) return SvelteTokenTypes.COMPONENT_ATTRIBUTE_NAME;
    }
    return XmlTokenType.XML_NAME;
  }

  private IElementType startTagName() {
    CharSequence name = yytext();
    setRawKind(name);
    yybegin(BEFORE_TAG_ATTRIBUTES);
    if (highlightMode) {
      if (name.length() > 7 && name.subSequence(0, 7).toString().equals("svelte:")) return SvelteTokenTypes.SVELTE_ELEMENT_NAME;
      if (isComponentName(name)) return SvelteTokenTypes.COMPONENT_NAME;
    }
    return XmlTokenType.XML_NAME;
  }

  private IElementType endTagName() {
    CharSequence name = yytext();
    yybegin(BEFORE_TAG_ATTRIBUTES);
    if (highlightMode) {
      if (name.length() > 7 && name.subSequence(0, 7).toString().equals("svelte:")) return SvelteTokenTypes.SVELTE_ELEMENT_NAME;
      if (isComponentName(name)) return SvelteTokenTypes.COMPONENT_NAME;
    }
    return XmlTokenType.XML_NAME;
  }
%}

%state DOC_TYPE
%state COMMENT
%state START_TAG_NAME
%state END_TAG_NAME
%state BEFORE_TAG_ATTRIBUTES
%state TAG_ATTRIBUTES
%state ATTRIBUTE_VALUE_START
%state ATTRIBUTE_VALUE_DQ
%state ATTRIBUTE_VALUE_SQ
%state PROCESSING_INSTRUCTION
%state TAG_CHARACTERS
%state C_COMMENT_START
%state C_COMMENT_END
%state RAW_SCRIPT
%state RAW_STYLE
%state MUSTACHE_PENDING
%state DIRECTIVE_COLON
%state DIRECTIVE_NAME
%state DIRECTIVE_MODIFIERS

ALPHA=[:letter:]
DIGIT=[0-9]
WHITE_SPACE_CHARS=[ \n\r\t\f\u2028\u2029\u0085]+

TAG_NAME=({ALPHA}|"_"|":")({ALPHA}|{DIGIT}|"_"|":"|"."|"-")*
/* HTML attribute name, minus '{' (mustache) */
ATTRIBUTE_NAME=([^ \n\r\t\f\"\'<>/={])+

DTD_REF= "\"" [^\"]* "\"" | "'" [^']* "'"
DOCTYPE= "<!" (D|d)(O|o)(C|c)(T|t)(Y|y)(P|p)(E|e)
HTML= (H|h)(T|t)(M|m)(L|l)
PUBLIC= (P|p)(U|u)(B|b)(L|l)(I|i)(C|c)
END_COMMENT="-->"
SCRIPT_END="</" [sS][cC][rR][iI][pP][tT]
STYLE_END="</" [sS][tT][yY][lL][eE]

CONDITIONAL_COMMENT_CONDITION=({ALPHA})({ALPHA}|{WHITE_SPACE_CHARS}|{DIGIT}|"."|"("|")"|"|"|"!"|"&")*
%%

/* ---------- Svelte: mustaches ---------- */
<YYINITIAL> "{" { return mustache(YYINITIAL); }
<BEFORE_TAG_ATTRIBUTES, TAG_ATTRIBUTES> "{" { return mustache(TAG_ATTRIBUTES); }
<ATTRIBUTE_VALUE_START> "{" { return mustache(TAG_ATTRIBUTES); }
<ATTRIBUTE_VALUE_DQ> "{" { return mustache(ATTRIBUTE_VALUE_DQ); }
<ATTRIBUTE_VALUE_SQ> "{" { return mustache(ATTRIBUTE_VALUE_SQ); }
/* Should never be lexed from (non-restartable); recover by treating as data. */
<MUSTACHE_PENDING> [^] { yybegin(YYINITIAL); return XmlTokenType.XML_DATA_CHARACTERS; }

/* ---------- Svelte: raw <script>/<style> content ---------- */
<RAW_SCRIPT> {SCRIPT_END} { yybegin(END_TAG_NAME); yypushback(yylength()); }
<RAW_SCRIPT> [^<]+ | "<" { return XmlTokenType.XML_DATA_CHARACTERS; }
<RAW_STYLE> {STYLE_END} { yybegin(END_TAG_NAME); yypushback(yylength()); }
<RAW_STYLE> [^<]+ | "<" { return XmlTokenType.XML_DATA_CHARACTERS; }

/* ---------- Svelte: directive attribute names (highlight mode only) ---------- */
<DIRECTIVE_COLON> ":" { yybegin(DIRECTIVE_NAME); return SvelteTokenTypes.DIRECTIVE_COLON; }
<DIRECTIVE_NAME> [^ \n\r\t\f\"\'<>/={|]+ { yybegin(DIRECTIVE_MODIFIERS); return SvelteTokenTypes.DIRECTIVE_NAME; }
<DIRECTIVE_NAME> [^] { yybegin(TAG_ATTRIBUTES); yypushback(1); break; }
<DIRECTIVE_MODIFIERS> "|" [^ \n\r\t\f\"\'<>/={|]* { return SvelteTokenTypes.DIRECTIVE_MODIFIER; }
<DIRECTIVE_MODIFIERS> "=" { yybegin(ATTRIBUTE_VALUE_START); return XmlTokenType.XML_EQ; }
<DIRECTIVE_MODIFIERS> [^] { yybegin(TAG_ATTRIBUTES); yypushback(1); break; }
<DIRECTIVE_COLON> [^] { yybegin(TAG_ATTRIBUTES); yypushback(1); break; }

/* ---------- HTML (from _HtmlLexer.flex) ---------- */
<YYINITIAL> "<?" { yybegin(PROCESSING_INSTRUCTION); return XmlTokenType.XML_PI_START; }
<PROCESSING_INSTRUCTION> "?"? ">" { yybegin(YYINITIAL); return XmlTokenType.XML_PI_END; }
<PROCESSING_INSTRUCTION> ([^\?\>] | (\?[^\>]))* { return XmlTokenType.XML_PI_TARGET; }

<YYINITIAL> {DOCTYPE} { yybegin(DOC_TYPE); return XmlTokenType.XML_DOCTYPE_START; }
<DOC_TYPE> {HTML} { return XmlTokenType.XML_NAME; }
<DOC_TYPE> {PUBLIC} { return XmlTokenType.XML_DOCTYPE_PUBLIC; }
<DOC_TYPE> {DTD_REF} { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOC_TYPE> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_DOCTYPE_END; }
<YYINITIAL> {WHITE_SPACE_CHARS} { return XmlTokenType.XML_REAL_WHITE_SPACE; }
<DOC_TYPE,TAG_ATTRIBUTES,ATTRIBUTE_VALUE_START,PROCESSING_INSTRUCTION, START_TAG_NAME, END_TAG_NAME, TAG_CHARACTERS> {WHITE_SPACE_CHARS} { return XmlTokenType.XML_WHITE_SPACE; }
<YYINITIAL> "<" {TAG_NAME} { yybegin(START_TAG_NAME); yypushback(yylength()); }
<START_TAG_NAME, TAG_CHARACTERS> "<" { return XmlTokenType.XML_START_TAG_START; }

<YYINITIAL> "</" {TAG_NAME} { yybegin(END_TAG_NAME); yypushback(yylength()); }
<YYINITIAL, END_TAG_NAME> "</" { return XmlTokenType.XML_END_TAG_START; }

<YYINITIAL> "<!--" { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_START; }
<COMMENT> "[" { yybegin(C_COMMENT_START); return XmlTokenType.XML_CONDITIONAL_COMMENT_START; }
<COMMENT> "<![" { yybegin(C_COMMENT_END); return XmlTokenType.XML_CONDITIONAL_COMMENT_END_START; }
<COMMENT> {END_COMMENT} | "<!-->" { yybegin(YYINITIAL); return XmlTokenType.XML_COMMENT_END; }
<COMMENT> "<!--" { return XmlTokenType.XML_BAD_CHARACTER; }
<COMMENT> "<!--->" | "--!>" { yybegin(YYINITIAL); return XmlTokenType.XML_BAD_CHARACTER; }
<COMMENT> ">" {
  int loc = getTokenStart();
  char prev = loc > 0 ? zzBuffer.charAt(loc - 1) : ' ';
  char prevPrev = loc > 1 ? zzBuffer.charAt(loc - 2) : ' ';
  if (prev == '-' && prevPrev == '-') {
    yybegin(YYINITIAL); return XmlTokenType.XML_BAD_CHARACTER;
  }
  return XmlTokenType.XML_COMMENT_CHARACTERS;
}
<COMMENT> [^\-<>\[!]+ { return XmlTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> [^] { return XmlTokenType.XML_COMMENT_CHARACTERS; }

<C_COMMENT_START,C_COMMENT_END> {CONDITIONAL_COMMENT_CONDITION} { return XmlTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> [^] { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> "]>" { yybegin(COMMENT); return XmlTokenType.XML_CONDITIONAL_COMMENT_START_END; }
<C_COMMENT_START,C_COMMENT_END> {END_COMMENT} { yybegin(YYINITIAL); return XmlTokenType.XML_COMMENT_END; }
<C_COMMENT_END> "]" { yybegin(COMMENT); return XmlTokenType.XML_CONDITIONAL_COMMENT_END; }
<C_COMMENT_END> [^] { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_CHARACTERS; }

<START_TAG_NAME> {TAG_NAME} { return startTagName(); }
<END_TAG_NAME> {TAG_NAME} { return endTagName(); }

<BEFORE_TAG_ATTRIBUTES, TAG_ATTRIBUTES, TAG_CHARACTERS> ">" { return tagEnd(); }
<BEFORE_TAG_ATTRIBUTES, TAG_ATTRIBUTES, TAG_CHARACTERS> "/>" { rawKind = RAW_NONE; yybegin(YYINITIAL); return XmlTokenType.XML_EMPTY_ELEMENT_END; }
<BEFORE_TAG_ATTRIBUTES> {WHITE_SPACE_CHARS} { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_WHITE_SPACE;}
<TAG_ATTRIBUTES> {ATTRIBUTE_NAME} { return attributeName(); }
<TAG_ATTRIBUTES> "=" { yybegin(ATTRIBUTE_VALUE_START); return XmlTokenType.XML_EQ; }
<BEFORE_TAG_ATTRIBUTES, TAG_ATTRIBUTES, START_TAG_NAME, END_TAG_NAME> [^] { rawKind = RAW_NONE; yybegin(YYINITIAL); yypushback(1); break; }

<TAG_CHARACTERS> [^<>/]+ | [^] { return XmlTokenType.XML_TAG_CHARACTERS; }

<ATTRIBUTE_VALUE_START> ">" { return tagEnd(); }
<ATTRIBUTE_VALUE_START> "/>" { rawKind = RAW_NONE; yybegin(YYINITIAL); return XmlTokenType.XML_EMPTY_ELEMENT_END; }

<ATTRIBUTE_VALUE_START> [^ \n\r\t\f'\"\>{]([^ \n\r\t\f\>{]|(\/[^\>]))* { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; }
<ATTRIBUTE_VALUE_START> "\"" { yybegin(ATTRIBUTE_VALUE_DQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }
<ATTRIBUTE_VALUE_START> "'" { yybegin(ATTRIBUTE_VALUE_SQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }

<ATTRIBUTE_VALUE_DQ> {
  "\"" { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
  [^\"{&]+ { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
  [^] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<ATTRIBUTE_VALUE_SQ> {
  "'" { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
  [^'{&]+ { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
  [^] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<YYINITIAL, ATTRIBUTE_VALUE_DQ, ATTRIBUTE_VALUE_SQ> {
"&lt;" |
"&gt;" |
"&apos;" |
"&quot;" |
"&nbsp;" |
"&amp;" |
"&#"{DIGIT}+";" |
"&#"[xX]({DIGIT}|[a-fA-F])+";" { return XmlTokenType.XML_CHAR_ENTITY_REF; }
"&"{TAG_NAME}";" { return XmlTokenType.XML_ENTITY_REF_TOKEN; }
}

<YYINITIAL> ([^<&{ \n\r\t\f])+ { return XmlTokenType.XML_DATA_CHARACTERS; }
<YYINITIAL> [^] { return XmlTokenType.XML_DATA_CHARACTERS; }
[^] { return XmlTokenType.XML_BAD_CHARACTER; }
