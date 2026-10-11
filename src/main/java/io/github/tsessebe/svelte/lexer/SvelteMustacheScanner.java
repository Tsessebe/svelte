package io.github.tsessebe.svelte.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a Svelte mustache ({@code {...}}) into sub-tokens.
 * <p>
 * Pure logic, no IntelliJ dependencies, so it can be tested in isolation.
 * The JS-aware part ({@link #findTopLevel}) skips strings, template literals (with nested
 * {@code ${}}), comments and regex literals, and tracks bracket depth, so a {@code }} inside any
 * of those never closes the mustache.
 */
public final class SvelteMustacheScanner {

  public enum Kind {
    LBRACE,            // {
    RBRACE,            // }
    BLOCK_KEYWORD,     // #if  :else  /if  #each ...
    SPECIAL_KEYWORD,   // @html @render @const @debug @attach
    INNER_KEYWORD,     // as, then, catch, the "if" in ":else if"
    EXPRESSION,        // JS/TS expression
    BINDING,           // binding pattern / parameters (each item, then value, snippet params)
    SNIPPET_NAME,      // name in {#snippet name(...)}
    SPREAD,            // ... in {...props}
    COMMA,             // , in {#each xs as x, i}
    LPAREN,            // ( around each key
    RPAREN,            // )
    WHITESPACE
  }

  public static final class Token {
    public final int start;
    public final int end;
    public final Kind kind;

    Token(int start, int end, Kind kind) {
      this.start = start;
      this.end = end;
      this.kind = kind;
    }

    @Override
    public String toString() {
      return kind + "[" + start + "," + end + ")";
    }
  }

  private final CharSequence s;
  private final List<Token> out = new ArrayList<>();

  private SvelteMustacheScanner(CharSequence s) {
    this.s = s;
  }

  /**
   * @param s     buffer
   * @param start offset of the opening '{'
   * @param end   end of buffer (exclusive)
   * @return tokens covering [start, mustacheEnd) contiguously, never empty
   */
  public static List<Token> scan(CharSequence s, int start, int end) {
    SvelteMustacheScanner sc = new SvelteMustacheScanner(s);
    sc.scanMustache(start, end);
    return sc.out;
  }

  // ---------------------------------------------------------------------------------------------

  private void scanMustache(int start, int end) {
    add(start, start + 1, Kind.LBRACE);
    int from = start + 1;
    // Block / special tag keyword comes first, so a leading '/' ({/if}) is never mistaken for a regex.
    // Like the Svelte parser, whitespace is allowed between '{' and the sigil.
    int kwStart = skipWs(from, end);
    int kwEnd = from;
    if (kwStart + 1 < end) {
      char c = s.charAt(kwStart);
      if ((c == '#' || c == ':' || c == '/' || c == '@') && isIdentStart(s.charAt(kwStart + 1))) {
        kwEnd = kwStart + 1;
        while (kwEnd < end && isIdentPart(s.charAt(kwEnd))) kwEnd++;
      }
    }
    int close = findTopLevel(s, kwEnd, end, NONE);
    boolean closed = close >= 0;
    int limit;
    if (closed) {
      limit = close;
    }
    else {
      // Unclosed while typing: don't swallow the rest of the file, stop at end of line.
      limit = kwEnd;
      while (limit < end && s.charAt(limit) != '\n' && s.charAt(limit) != '\r') limit++;
    }
    scanContent(from, kwEnd, limit);
    if (closed) add(close, close + 1, Kind.RBRACE);
  }

  private void scanContent(int from, int kwEnd, int limit) {
    if (from >= limit) return;
    if (kwEnd > from) {
      int kwStart = skipWs(from, kwEnd);
      ws(from, kwStart);
      char c = s.charAt(kwStart);
      String word = s.subSequence(kwStart + 1, kwEnd).toString();
      add(kwStart, kwEnd, c == '@' ? Kind.SPECIAL_KEYWORD : Kind.BLOCK_KEYWORD);
      int rest = kwEnd;
      if (c == '#') {
        switch (word) {
          case "each" -> eachRest(rest, limit);
          case "await" -> awaitRest(rest, limit);
          case "snippet" -> snippetRest(rest, limit);
          default -> part(rest, limit, Kind.EXPRESSION);
        }
      }
      else if (c == ':') {
        switch (word) {
          case "else" -> elseRest(rest, limit);
          case "then", "catch" -> part(rest, limit, Kind.BINDING);
          default -> part(rest, limit, Kind.EXPRESSION);
        }
      }
      else {
        part(rest, limit, Kind.EXPRESSION);
      }
      return;
    }
    int i = skipWs(from, limit);
    if (i + 3 <= limit && s.charAt(i) == '.' && s.charAt(i + 1) == '.' && s.charAt(i + 2) == '.') {
      ws(from, i);
      add(i, i + 3, Kind.SPREAD);
      part(i + 3, limit, Kind.EXPRESSION);
      return;
    }
    part(from, limit, Kind.EXPRESSION);
  }

  private void elseRest(int from, int limit) {
    int i = skipWs(from, limit);
    if (isWordAt(s, i, "if", limit)) {
      ws(from, i);
      add(i, i + 2, Kind.INNER_KEYWORD);
      part(i + 2, limit, Kind.EXPRESSION);
    }
    else {
      part(from, limit, Kind.EXPRESSION);
    }
  }

  private void eachRest(int from, int limit) {
    int as = findTopLevel(s, from, limit, (b, i, lim) -> isWordAt(b, i, "as", lim) && i > 0 && Character.isWhitespace(b.charAt(i - 1)));
    if (as < 0) {
      // {#each expr} or {#each expr, i}
      int comma = findTopLevel(s, from, limit, (b, i, lim) -> b.charAt(i) == ',');
      if (comma < 0) {
        part(from, limit, Kind.EXPRESSION);
      }
      else {
        part(from, comma, Kind.EXPRESSION);
        add(comma, comma + 1, Kind.COMMA);
        part(comma + 1, limit, Kind.BINDING);
      }
      return;
    }
    part(from, as, Kind.EXPRESSION);
    add(as, as + 2, Kind.INNER_KEYWORD);
    int p = as + 2;
    int stop = findTopLevel(s, p, limit, (b, i, lim) -> b.charAt(i) == ',' || b.charAt(i) == '(');
    if (stop < 0) {
      part(p, limit, Kind.BINDING);
      return;
    }
    part(p, stop, Kind.BINDING);
    if (s.charAt(stop) == ',') {
      add(stop, stop + 1, Kind.COMMA);
      int idxStop = findTopLevel(s, stop + 1, limit, (b, i, lim) -> b.charAt(i) == '(');
      part(stop + 1, idxStop < 0 ? limit : idxStop, Kind.BINDING);
      stop = idxStop;
      if (stop < 0) return;
    }
    // key: ( expr )
    add(stop, stop + 1, Kind.LPAREN);
    int rp = findTopLevel(s, stop + 1, limit, (b, i, lim) -> b.charAt(i) == ')');
    if (rp < 0) {
      part(stop + 1, limit, Kind.EXPRESSION);
      return;
    }
    part(stop + 1, rp, Kind.EXPRESSION);
    add(rp, rp + 1, Kind.RPAREN);
    part(rp + 1, limit, Kind.EXPRESSION);
  }

  private void awaitRest(int from, int limit) {
    int kw = findTopLevel(s, from, limit,
                          (b, i, lim) -> (isWordAt(b, i, "then", lim) || isWordAt(b, i, "catch", lim))
                                         && i > 0 && Character.isWhitespace(b.charAt(i - 1)));
    if (kw < 0) {
      part(from, limit, Kind.EXPRESSION);
      return;
    }
    int kwEnd = kw + (s.charAt(kw) == 't' ? 4 : 5);
    part(from, kw, Kind.EXPRESSION);
    add(kw, kwEnd, Kind.INNER_KEYWORD);
    part(kwEnd, limit, Kind.BINDING);
  }

  private void snippetRest(int from, int limit) {
    int i = skipWs(from, limit);
    int nameEnd = i;
    while (nameEnd < limit && isIdentPart(s.charAt(nameEnd))) nameEnd++;
    if (nameEnd == i) {
      part(from, limit, Kind.BINDING);
      return;
    }
    ws(from, i);
    add(i, nameEnd, Kind.SNIPPET_NAME);
    part(nameEnd, limit, Kind.BINDING);
  }

  // ---------------------------------------------------------------------------------------------
  // token emission

  /** Emits leading whitespace, the trimmed core as {@code kind}, trailing whitespace. */
  private void part(int from, int to, Kind kind) {
    if (from >= to) return;
    int a = from;
    while (a < to && Character.isWhitespace(s.charAt(a))) a++;
    int b = to;
    while (b > a && Character.isWhitespace(s.charAt(b - 1))) b--;
    ws(from, a);
    if (a < b) add(a, b, kind);
    ws(b, to);
  }

  private void ws(int from, int to) {
    if (from < to) add(from, to, Kind.WHITESPACE);
  }

  private void add(int start, int end, Kind kind) {
    if (start < end) out.add(new Token(start, end, kind));
  }

  private int skipWs(int i, int limit) {
    while (i < limit && Character.isWhitespace(s.charAt(i))) i++;
    return i;
  }

  // ---------------------------------------------------------------------------------------------
  // JS-aware scanning

  @FunctionalInterface
  public interface StopAt {
    boolean test(CharSequence s, int i, int limit);
  }

  public static final StopAt NONE = (s, i, lim) -> false;

  /**
   * Scans JS from {@code from} until {@code limit}. Returns the offset of the first depth-0 position where
   * {@code stop} matches, or of the first unmatched '}' at depth 0. Returns -1 if neither is found.
   * {@code stop} is checked before brackets are processed, so it can match '(' or ')' at depth 0.
   */
  public static int findTopLevel(CharSequence s, int from, int limit, StopAt stop) {
    int depth = 0;
    int i = from;
    boolean regexAllowed = true;
    while (i < limit) {
      char c = s.charAt(i);
      if (depth == 0 && stop.test(s, i, limit)) return i;
      switch (c) {
        case '\'', '"' -> {
          i = skipString(s, i, limit, c);
          regexAllowed = false;
          continue;
        }
        case '`' -> {
          i = skipTemplate(s, i, limit);
          regexAllowed = false;
          continue;
        }
        case '/' -> {
          if (i + 1 < limit && s.charAt(i + 1) == '/') {
            while (i < limit && s.charAt(i) != '\n') i++;
            continue;
          }
          if (i + 1 < limit && s.charAt(i + 1) == '*') {
            int e = indexOf(s, "*/", i + 2, limit);
            i = e < 0 ? limit : e + 2;
            continue;
          }
          if (regexAllowed) {
            i = skipRegex(s, i, limit);
            regexAllowed = false;
            continue;
          }
          regexAllowed = true;
          i++;
          continue;
        }
        case '(', '[', '{' -> {
          depth++;
          regexAllowed = true;
          i++;
          continue;
        }
        case ')', ']' -> {
          if (depth > 0) depth--;
          regexAllowed = false;
          i++;
          continue;
        }
        case '}' -> {
          if (depth == 0) return i;
          depth--;
          regexAllowed = true; // end of block; good enough
          i++;
          continue;
        }
        default -> {
          if (Character.isWhitespace(c)) {
            i++;
            continue;
          }
          if (isIdentPart(c)) {
            int w = i;
            while (i < limit && isIdentPart(s.charAt(i))) i++;
            regexAllowed = isRegexKeyword(s, w, i);
            continue;
          }
          // operators and punctuation: , ; = + - * % ! ~ ? : & | ^ < > .
          regexAllowed = c != '.';
          i++;
        }
      }
    }
    return -1;
  }

  private static int skipString(CharSequence s, int i, int limit, char q) {
    i++;
    while (i < limit) {
      char c = s.charAt(i);
      if (c == '\\') { i += 2; continue; }
      if (c == q) return i + 1;
      if (c == '\n') return i; // unterminated
      i++;
    }
    return limit;
  }

  private static int skipTemplate(CharSequence s, int i, int limit) {
    i++;
    while (i < limit) {
      char c = s.charAt(i);
      if (c == '\\') { i += 2; continue; }
      if (c == '`') return i + 1;
      if (c == '$' && i + 1 < limit && s.charAt(i + 1) == '{') {
        int close = findTopLevel(s, i + 2, limit, NONE);
        if (close < 0) return limit;
        i = close + 1;
        continue;
      }
      i++;
    }
    return limit;
  }

  private static int skipRegex(CharSequence s, int i, int limit) {
    i++;
    boolean inClass = false;
    while (i < limit) {
      char c = s.charAt(i);
      if (c == '\\') { i += 2; continue; }
      if (c == '\n') return i;
      if (inClass) {
        if (c == ']') inClass = false;
      }
      else if (c == '[') inClass = true;
      else if (c == '/') {
        i++;
        while (i < limit && isIdentPart(s.charAt(i))) i++; // flags
        return i;
      }
      i++;
    }
    return limit;
  }

  private static final String[] REGEX_KEYWORDS = {
    "return", "typeof", "instanceof", "in", "of", "new", "delete", "void", "throw", "case", "do", "else", "yield", "await"
  };

  private static boolean isRegexKeyword(CharSequence s, int from, int to) {
    int len = to - from;
    for (String k : REGEX_KEYWORDS) {
      if (k.length() == len && regionMatches(s, from, k)) return true;
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // char helpers

  static boolean isIdentStart(char c) {
    return Character.isLetter(c) || c == '_' || c == '$';
  }

  static boolean isIdentPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  static boolean isWordAt(CharSequence s, int i, String word, int limit) {
    int e = i + word.length();
    if (e > limit || !regionMatches(s, i, word)) return false;
    if (i > 0 && (isIdentPart(s.charAt(i - 1)) || s.charAt(i - 1) == '.')) return false;
    return e >= limit || !isIdentPart(s.charAt(e));
  }

  private static boolean regionMatches(CharSequence s, int i, String w) {
    if (i + w.length() > s.length()) return false;
    for (int k = 0; k < w.length(); k++) {
      if (s.charAt(i + k) != w.charAt(k)) return false;
    }
    return true;
  }

  private static int indexOf(CharSequence s, String w, int from, int limit) {
    for (int i = from; i + w.length() <= limit; i++) {
      if (regionMatches(s, i, w)) return i;
    }
    return -1;
  }
}
