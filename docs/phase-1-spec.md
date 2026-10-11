# Svelte plugin for WebStorm — Phase 1 spec

Working notes. 2026-10-11. Greenfield. Updated same day: all open items closed (see "Resolved").

Reference implementation: JetBrains' **Astro** plugin (`JetBrains/intellij-plugins/Astro`, Apache-2.0) — same shape as Svelte (HTML-ish file, JS expressions, framework LSP + TS plugin). JetBrains' Svelte plugin is *not* in that repo (closed source now; old `tomblachut/svelte-intellij` is history only).

## Why

- Zero Svelte support in the IDE right now; 5–6 Svelte frontends to work on.
- Existing marketplace options: one paid, one widely reported as weak on Svelte 5.
- Second job: help learn Svelte while working in code Claude Code wrote.

## Phase 1 goal

Open any of the frontends in WebStorm and work in them like any other first-class language:
correct highlighting, no false errors, completion / go-to / diagnostics / hover that understand Svelte 5 *and* legacy syntax.

Phase 1 is done when it fully replaces "nothing" — not a demo.

## What the corpus says (scan, 2026-10-11)

| | TyledHive.Lodge.WebApp | GhostNote/frontend |
|---|---|---|
| svelte | 5.57.2 | 5.55.5 |
| .svelte files | 140, all `lang="ts"` | 7, all `lang="ts"` |
| .svelte.ts modules | 34 | 0 |
| mode | runes only, zero legacy | legacy only, zero runes |
| styling | Tailwind v4, zero `<style>` | `<style>` in every file |
| kit | SvelteKit 3, adapter-static, config in `vite.config.ts` (no `svelte.config.js`) | plain Vite |
| .ts importing `.svelte` / `.svelte.ts` | 31 of 81 `.ts` files | — |

TyledHive usage counts (hits): `$state` 155, `$derived` 108, `$props` 109, `$bindable` 64, `$effect` 47,
`onX={}` 62, `{#snippet}` 69, `{@render}` 33, `{#if}` 136, `{#each}` 43, `{@const}` 19, `{#key}` 2,
`bind:` 93, `{...spread}` 56, `<svelte:head>` 24, `<svelte:window>` 2, `<script module>` 3, `generics=` 1,
imports from `svelte/reactivity`, `svelte/elements`.

Not used anywhere yet: `{#await}`, `{@html}`, `{@attach}`, `{@debug}`, `use:`, transitions, `animate:`, `$inspect`, `$host`, `<slot>`, `let:`.

GhostNote legacy: `export let`, `$:`, `on:click`, `createEventDispatcher`, `class:`.

Takeaways:
- Both modes are Phase 1. Same Svelte version, opposite syntax — `package.json` tells you nothing.
- `.svelte.ts` is Phase 1 (34 files would go red otherwise).
- Tailwind class completion in markup matters more than CSS-in-`<style>` for the current app.
- Everything is TS. Plain-JS `<script>` still has to work but isn't the main path.
- 31 `.ts` files import components / rune modules → TS-side Svelte awareness is Phase 1 (see §6).
- SvelteKit 3 generates its tsconfig into `node_modules/$app/tsconfig.json` (`extends: "$app/tsconfig"`), not `.svelte-kit/tsconfig.json`.

## Targets

- WebStorm and IntelliJ IDEA Ultimate, **2026.1+** (since-build 261). Current + one back, no further.
- Kotlin, IntelliJ Platform Gradle Plugin 2.x.
- Depends on bundled: JavaScript (incl. TypeScript), CSS, `com.intellij.modules.ultimate`, `intellij.platform.lsp`.
- Uses the JavaScript plugin's framework-service layer (same as Astro). Some of it is `@ApiStatus.Experimental` → accept; pin to since-build and re-check each IDE major. Personal plugin, so verifier warnings are fine.
- Not targeting open-source IntelliJ builds / Android Studio (no LSP there).

## The split — native vs language server

Rule: **native owns structure and editing, the language server owns semantics.** Never both judging the same thing.

| Concern | Owner |
|---|---|
| Lexing, parsing, syntax tree, highlighting | native |
| HTML / CSS / JS / TS embedding, brace matching, comments | native |
| Type checking, Svelte compiler diagnostics, a11y warnings | LSP |
| Completion in script + template expressions | LSP (native adds Svelte keywords/blocks/directives) |
| Go-to, find usages, hover, rename across .svelte/.ts | LSP |
| Semantic highlighting (on top of native) | LSP |
| Formatting | out of Phase 1 (Prettier + prettier-plugin-svelte later) |

Consequence: the IDE's own JS/TS inspections must be quiet inside `.svelte` files (and rune-specific ones in `.svelte.ts`), otherwise double/false errors. Mechanism (from Astro): an inspection suppressor extending `TypeScriptDefaultInspectionSuppressor` + mute unused-import / unknown-attribute / unbound-namespace inspections that misfire on Svelte markup. Diagnostics come only through the TS-service layer (§6).

## Components

### 1. Language + file type

- `.svelte` file type, icon.
- Language = dialect of HTML (same pattern as Vue), so HTML features (tags, attributes, Emmet later, Tailwind) apply to markup for free.

### 2. Lexer

Subclass the platform HTML lexer; JFlex sub-lexer for Svelte constructs. States to cover:

- **Mustache expressions** `{expr}` in text and attribute values, incl. mixed `"a {b} c"` and shorthand `{name}`.
- **Expression scanning**: track brace depth; skip strings `'"`, template literals (nested `${}`), regex literals, comments. A `}` inside any of these does not close. This is where Svelte lexers break — test hard.
- **Block tags**: `{#if|each|await|key|snippet …}`, `{:else}`, `{:else if …}`, `{:then x}`, `{:catch e}`, `{/…}`.
- **Special tags**: `{@html|render|const|debug|attach …}`.
- **Directives**: `bind: on: class: style: use: transition: in: out: animate: let:` + `|modifiers` (legacy `on:click|preventDefault`).
- **Event attributes** `onclick={…}` — plain attribute with expression value; no special token needed beyond expression.
- **Spread** `{...props}` in tag attribute position.
- **`<script>` / `<style>`**: raw content; read `lang`, `module` / legacy `context="module"`, `generics`.
- **`svelte:` elements**: `head window document body element component self options fragment boundary`.
- Comments `<!-- -->` incl. `<!-- svelte-ignore … -->` (highlight the directive).

### 3. Parser + syntax tree

- Subclass the platform HTML parser (Vue does the same).
- Block nesting with proper open/branch/close nodes; unclosed / mismatched blocks → parse error, but recover so the rest of the file stays usable.
- Tree must be stable while typing (incremental reparse on expression/block level — lazy-parseable elements for expressions).

### 4. Embedded languages

- `<script lang="ts">` → TypeScript; no `lang` → JavaScript. `<script module>` same.
- `<style>` → CSS (or `lang` value if the IDE knows it).
- Template expressions → JS/TS expression dialect matching the component's script `lang`.
- **Scope**: template expressions resolve names declared in the instance script, plus block-introduced names:
  `{#each xs as x, i (key)}`, `{:then v}`, `{:catch e}`, `{@const a = …}`, `{#snippet name(params)}`, `let:` (legacy).
  Hard part. Needed so IDE-side navigation/highlighting doesn't go red even before LSP answers.
- **Runes**: `$state`, `$state.raw`, `$state.snapshot`, `$derived`, `$derived.by`, `$effect` (+ `.pre .root .tracking`), `$props`, `$props.id`, `$bindable`, `$inspect` (+ `.trace`), `$host` must resolve in `.svelte` scripts and in `.svelte.ts/.svelte.js`.
  Source: `svelte/types/index.d.ts` declares them as **globals** (top-level `declare function $state…` + `declare namespace $state`, lines ~3341–3850 in 5.57.2). They're in the program whenever `svelte` types are — in TyledHive via `$app/types` → `@sveltejs/kit` → `svelte`. No stub needed.
  Belt-and-braces: inspection suppressor treats the rune names as known in `.svelte` / `.svelte.*` so the native engine never flags them even before indexing finishes.
- **Legacy**: `$:` labels, `$store` auto-subscriptions, `$$props` / `$$restProps` / `$$slots` must not error.

### 5. `.svelte.ts` / `.svelte.js`

- Stay normal TS/JS files (IDE already handles the extension) — no new file type.
- Plugin's job: runes resolve (globals, above), no false "unresolved" / "unused" on rune code.
- **Not** attached to the Svelte LSP — handled by tsserver + `typescript-svelte-plugin` (§6.2), exactly like VS Code.

### 6. Language services (two of them — Astro pattern)

Not a bare LSP client. Plug into the JavaScript plugin's TypeScript-service layer so results replace the IDE's own TS analysis instead of duplicating it.

- `JavaScript.languageServiceProvider` (order first) → a `TypeScriptServiceProvider` exposing two services:
  1. **Svelte LSP service** — `svelte-language-server` over the framework LSP layer (`JSFrameworkLspIntegrationProvider` / `JSFrameworkLspClientDescriptor` / `JSFrameworkLspTypeScriptService`). Activation: `.svelte` files only (the server's own document selector is `.svelte` only).
  2. **TS plugin service** — `PluggableTypeScriptService` loading **`typescript-svelte-plugin`** into the IDE's tsserver. This is what makes `.ts` files see real component types for `import X from './X.svelte'` and makes `.svelte.ts` modules first-class. Same as VS Code (`typescriptServerPlugins`) and Astro (`@astrojs/ts-plugin`).
- Package resolution (both): project `node_modules` → plugin-bundled pinned version (`PackageVersion.bundled`) → settings override path. Settings page under Languages & Frameworks | TypeScript | Svelte with Enabled / Disabled.
- Versions checked: `svelte-language-server` 0.18.4 peer `typescript ^5.9.2 || ^6.0.2` → TyledHive's TS 6.0.3 fine. `svelte-check` 4.7.6 (same toolchain, svelte2tsx 0.7.61) already runs on TS 6 in TyledHive.
- LSP features the IDE takes from the server vs disables: start like Astro — route diagnostics/hover/navigation through the TS-service layer, disable the raw LSP duplicates (semantic tokens, hover, go-to, diagnostics, formatting, folding…) and merge completion. Then turn individual raw features back on only if the TS-service path lacks them.
- Config: SvelteKit 3 has no `svelte.config.js`; server 0.18.x loads config via `@sveltejs/load-config`. Smoke test on TyledHive in M3.
- Node: IDE's configured Node interpreter. Status-bar widget item + restart (comes with the layer).

### 7. Highlighting

- Distinct colours: block keywords (`#if`, `:else`, `/if`), special tags (`@render`…), directive prefix vs name vs modifier, mustache braces, `svelte:` element names, rune calls (`$state` etc.), `svelte-ignore` comment.
- Colour settings page with a Svelte demo snippet showing both modes.
- LSP semantic tokens layered on top.

### 8. Tailwind

- WebStorm's Tailwind support covers HTML class attributes and JS string literals; other languages map in via its `includeLanguages` setting.
- Plan: markup is an HTML dialect → `class="…"` works; `class={cn(…)}` / `tv(…)` strings work as JS strings (+ Tailwind's `classFunctions` setting for `cn`, `tv`).
- Fallback, zero code: `"includeLanguages": { "svelte": "html" }` in Settings | Languages & Frameworks | Style Sheets | Tailwind CSS. M4 test decides whether the fallback is ever needed.

## Explicitly out of Phase 1

Formatter; typing assists (auto-close blocks); Emmet tuning; folding beyond tags/blocks; structure view; SvelteKit route awareness; refactorings beyond LSP rename; learning layer (hover docs, runes/legacy marker, migrate quick-fix) — that's Phase 2.

## Milestones inside Phase 1

1. **M1 — see it.** File type, lexer, highlighter, colour page. Open both apps: every file highlights right. No parser yet beyond HTML default.
2. **M2 — structure.** Parser + tree, embedding of script/style/expressions, scope for block bindings, runes resolve. Both apps: zero native false errors.
3. **M3 — semantics.** Both language services (Svelte LS + `typescript-svelte-plugin`), package lookup, diagnostics/completion/hover/go-to/rename through the TS-service layer. Suppressor live. All 31 `.ts` → `.svelte` imports typed.
4. **M4 — `.svelte.ts` + Tailwind + polish.** All 34 rune modules clean; Tailwind completion in markup; settings page; crash/restart handling.

## Test strategy

- **Oracle tests**: run `svelte/compiler` `parse()` (modern AST) over fixture files, reduce to a simple node/range tree, compare with our tree reduced the same way. Mismatch = bug in us.
- **Fixtures**: Svelte repo parser test samples (modern + legacy) → covers what the corpus doesn't use (`{#await}`, `{@attach}`, transitions, `use:` …).
- **Corpus**: TyledHive (140 + 34) and GhostNote (7) as read-only smoke set — "opens clean, highlights right, zero native errors".
- **Lexer torture file**: `}` in strings / template literals / regex / comments inside expressions; nested template literals; attribute values with multiple mustaches; unclosed blocks while typing.
- Platform test framework: parsing tests (tree dumps), highlighting tests, completion tests for keywords/blocks.

## Resolved (was "open items")

| Question | Answer | How it was checked |
|---|---|---|
| Where are runes declared? | Globals in `svelte/types/index.d.ts` | Read TyledHive's node_modules (svelte 5.57.2) |
| Do they reach `.svelte.ts`? | Yes, via `$app/types` → kit → svelte; suppressor as backup | Read `node_modules/$app/tsconfig.json` + `types/index.d.ts` |
| `.ts` importing `.svelte`? | Needs `typescript-svelte-plugin` in tsserver → Phase 1 (§6.2). 31 files in TyledHive | VS Code extension manifest (`typescriptServerPlugins`), Astro plugin does the same; grep of TyledHive |
| LS + TypeScript 6? | Supported: peer `^5.9.2 \|\| ^6.0.2` | `svelte-language-server` 0.18.4 package.json |
| Who owns `.svelte.ts`? | tsserver + TS plugin, not the Svelte LS | Svelte LS document selector = `.svelte` only |
| Extension points | Astro's set: HTML-compatible language, parser definition, `html.embeddedContentSupport`, `xml.xmlExtension`, `JavaScript.languageServiceProvider`, `platform.lsp.integrationProvider`, dialect handler factories, suppressor | Astro `plugin.xml` + sources |
| LSP gaps in 2026.1 | None that matter: framework layer routes through TS service; raw LSP has everything the server offers bar on-type formatting (off by default) | Platform LSP feature table |
| Tailwind hook | HTML dialect + JS strings; `includeLanguages` fallback | WebStorm Tailwind docs |
| SvelteKit 3 config | `vite.config.ts`, no `svelte.config.js`; LS uses `@sveltejs/load-config` | TyledHive vite.config.ts comment + LS deps |

Remaining risk (not a question): framework-service classes are partly `@ApiStatus.Experimental` → can shift between IDE majors.

## Reference: Astro plugin (copy the shape, not the code)

- Look at: `AstroLanguageServiceProvider` (two services), `AstroServices.kt` (package loaders, bundled versions, activation rules), `AstroLspIntegrationProvider` (which raw LSP features to disable), `AstroDefaultInspectionSuppressor`, `Astro.flex` + `AstroLexer`, `AstroParser`, `AstroEmbeddedContentSupport`, lexer/parser tests.
- Differences: Astro makes the whole file a JS dialect (TSX frontmatter); Svelte = HTML dialect + JS/TS expression dialects (Vue's shape). Svelte block syntax (`{#each … as x (key)}`) needs its own expression grammar on top of JS.
- Avoid (JetBrains Svelte plugin complaints): stale pinned server with no override; native semantic checks disagreeing with the compiler.

## Later phases (headline only)

- **Phase 2 — learning + editing comfort**: hover docs for every rune/block/directive/`svelte:` element (bundled docs, offline); runes-vs-legacy marker per component; "Svelte 4 syntax" inspections with migrate quick-fix (via compiler `migrate()`); compiler warnings linked to docs; typing assists; folding; structure view; Emmet.
- **Phase 3 — SvelteKit**: route file icons/nesting, `+page`/`+layout` navigation, `$lib`/`$app` awareness, Prettier default wiring.

## Status — 2026-10-11 (end of day 1)

Done and committed (repo: `D:\Projects\Tools\JetBrains\Plugins`, 25 tests green, all 140 TyledHive files parse with 0 errors):

- **M1** lexer + highlighting: mustaches split JS-aware, raw script/style, directive split, TS colours inside `{...}`; Blazor-style colours (components green, props lilac, `svelte:` orange).
- **M2 (blocks)**: nested `{#if/#each/#await/#key/#snippet}` with branch/close validation, folding per segment, auto-insert `{/block}`.
- **M3**: svelte-language-server 0.18.4 via platform LSP API (project node_modules → managed npm install in IDE system dir); typescript-svelte-plugin 0.3.52 via JS plugin's PluggableTypeScriptService (Astro pattern); WebStorm JS/TS inspections suppressed in .svelte (LS owns diagnostics); settings page (TypeScript | Svelte), restart action, status bar entry.
- **Polish**: used/unused import colours (lexical usage; tags → component colour).

Target changed from the plan: WebStorm **2026.2.3**, Java 25, since-build 262, built against the local install (`localIde` in gradle.properties).

Lessons:
- FlexAdapter.restore() re-creates the current token and resets the lexer at its END — queued sub-tokens must be saved from the next one.
- No MergingLexerAdapter over a queued lexer (it restores by restarting).
- HtmlEmbeddedContentSupport providers are created in the BaseHtmlLexer constructor — `isHighlighting` isn't set yet.
- Sync: reusing a staged upload path delivers a stale copy; always fresh path + checksum check.

Next candidates (end of day 1): Phase 2 learning layer; SvelteKit route awareness.

## Status — 2026-10-11 (afternoon)

- **Phase 2 learning layer**: hover docs (runes, blocks, directives, `svelte:` elements → svelte.dev links); Svelte 4 syntax inspection with `on:` → attribute fix and whole-component migrate (compiler `migrate()` via node).
- **SvelteKit route awareness**: tab titles (`/members (+page)`), route file icons, nesting (`.svelte` → `.ts/.js/.server.*`), related files (Ctrl+Alt+Home).
- **Template ↔ script references** (native, was "not done" above): go to declaration, usages (inlay shows WebStorm's "Show usages" = ≥1 found), rename, `$store` prefix, block-local shadowing.
- 41 tests green.
- **Packaging**: renamed to **Svelte 5 Toolkit**, ID `io.github.tsessebe.svelte` (personal GitHub: Tsessebe), MIT, icon, description, change notes, `incompatible-with dev.blachut.svelte.lang`.
- **verifyPlugin** (local WebStorm 262): Compatible, 0 internal API (removed `PackageVersion.bundled` → `downloadable`, dropped `isServiceFallbackResolveEnabled` override), 39 experimental (TS-plugin layer, HtmlParsing hooks), 2 deprecated (Kotlin bridge for tab tooltip).
- **Installed in real WebStorm** from `build/distributions/svelte-plugin-0.1.0.zip`; working.

Marketplace, if/when: public repo on github.com/Tsessebe (link in description), vendor email decision, trader/non-trader declaration, sign (optional, avoids install warning), first upload manual.

Update loop: `gradlew clean test verifyPlugin buildPlugin` → Install Plugin from Disk. Re-run verifyPlugin after each WebStorm major.
