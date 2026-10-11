package io.github.tsessebe.svelte.docs

/** Short, offline explanations of Svelte template constructs, each linked to the official docs. */
object SvelteDocs {
  data class Entry(val title: String, val body: String, val slug: String, val legacy: Boolean = false, val svelte5: String? = null)

  private const val BASE = "https://svelte.dev/docs/svelte/"

  fun url(entry: Entry): String = BASE + entry.slug

  val entries: Map<String, Entry> = mapOf(
    // ---- blocks
    "#if" to Entry("{#if condition}…{:else if other}…{:else}…{/if}",
      "Renders its content only while the condition is truthy. Chain <code>{:else if …}</code> and finish with an optional <code>{:else}</code>.", "if"),
    ":else" to Entry("{:else} / {:else if condition}",
      "Branch of an <code>{#if}</code> block, or the fallback of an <code>{#each}</code> block when the list is empty.", "if"),
    "#each" to Entry("{#each items as item, index (key)}…{/each}",
      "Renders its content once per item. <code>index</code> is optional. The keyed form <code>(key)</code> lets Svelte move existing DOM nodes when the list reorders instead of re-rendering them — use it for lists that change. <code>{:else}</code> renders when the list is empty.", "each"),
    "#await" to Entry("{#await promise}…{:then value}…{:catch error}…{/await}",
      "Shows different content while a promise is pending, after it resolves (<code>{:then}</code>) and if it rejects (<code>{:catch}</code>). Short forms: <code>{#await promise then value}</code> skips the pending state.", "await"),
    ":then" to Entry("{:then value}", "Branch of an <code>{#await}</code> block, rendered when the promise resolves; <code>value</code> is the result.", "await"),
    ":catch" to Entry("{:catch error}", "Branch of an <code>{#await}</code> block, rendered when the promise rejects; <code>error</code> is the reason.", "await"),
    "#key" to Entry("{#key expression}…{/key}",
      "Destroys and re-creates its content whenever the expression changes. Useful to replay an intro transition or reset a component's state.", "key"),
    "#snippet" to Entry("{#snippet name(params)}…{/snippet}",
      "Declares a reusable piece of markup — like a local function that returns markup. Render it with <code>{@render name(args)}</code>, or pass it to a component as a prop. Replaces Svelte 4 slots.", "snippet"),
    // ---- special tags
    "@render" to Entry("{@render snippet(args)}",
      "Renders a snippet. For an optional snippet prop use <code>{@render children?.()}</code>. <code>children</code> is the snippet made from content placed between a component's tags.", "@render"),
    "@html" to Entry("{@html expression}",
      "Inserts a raw HTML string without escaping. Never use it with untrusted input — it is an XSS hole. Scoped styles don't apply to the inserted markup.", "@html"),
    "@attach" to Entry("{@attach fn}",
      "Runs <code>fn(element)</code> when the element is mounted, re-runs it when its dependencies change, and calls the returned cleanup function. The Svelte 5 successor to <code>use:</code> actions.", "@attach"),
    "@const" to Entry("{@const name = expression}",
      "Declares a local constant inside a block (<code>{#each}</code>, <code>{#if}</code>, <code>{#snippet}</code>, …), recomputed when its inputs change.", "@const"),
    "@debug" to Entry("{@debug var1, var2}",
      "Logs the variables whenever they change and pauses in the debugger if devtools are open.", "@debug"),
    // ---- directives
    "bind:" to Entry("bind:property={variable}",
      "Two-way binding: changes flow from the variable to the element/component and back. <code>bind:value</code> on inputs, <code>bind:this</code> for the DOM node. On a component, the prop must be declared with <code>\$bindable()</code>.", "bind"),
    "class:" to Entry("class:name={condition}",
      "Adds the class while the condition is truthy. Svelte 5 also accepts objects and arrays in <code>class={…}</code>, which is usually clearer.", "class"),
    "style:" to Entry("style:property={value}", "Sets a single CSS property, e.g. <code>style:color={c}</code>. Add <code>|important</code> for !important.", "style"),
    "use:" to Entry("use:action={parameter}",
      "Calls <code>action(node, parameter)</code> when the element mounts. Still supported, but <code>{@attach …}</code> is the Svelte 5 way.", "use"),
    "transition:" to Entry("transition:fn={params}", "Plays the transition when the element enters and leaves (e.g. <code>transition:fade</code> from svelte/transition).", "transition"),
    "in:" to Entry("in:fn={params}", "Transition played only when the element enters.", "in-and-out"),
    "out:" to Entry("out:fn={params}", "Transition played only when the element leaves.", "in-and-out"),
    "animate:" to Entry("animate:fn={params}", "Animates an element of a keyed <code>{#each}</code> block when it moves (e.g. <code>animate:flip</code>).", "animate"),
    "on:" to Entry("on:event={handler}",
      "Svelte 4 event directive. Still works in legacy mode.", "legacy-on", legacy = true,
      svelte5 = "Use the plain attribute: <code>onclick={handler}</code>. Modifiers like <code>|preventDefault</code> become code in the handler. Component events become callback props."),
    "let:" to Entry("let:name", "Svelte 4: receives a value from a slot.", "legacy-let", legacy = true,
      svelte5 = "Use snippets: the child calls <code>{@render children(value)}</code> and the parent writes <code>{#snippet children(value)}…{/snippet}</code>."),
    // ---- special elements
    "svelte:head" to Entry("<svelte:head>", "Inserts its content into <code>document.head</code> — page title, meta tags, links.", "svelte-head"),
    "svelte:window" to Entry("<svelte:window>", "Adds event listeners to <code>window</code> (removed automatically) and binds to window properties like <code>bind:innerWidth</code>.", "svelte-window"),
    "svelte:document" to Entry("<svelte:document>", "Event listeners and bindings on <code>document</code>.", "svelte-document"),
    "svelte:body" to Entry("<svelte:body>", "Event listeners and actions on <code>document.body</code>.", "svelte-body"),
    "svelte:element" to Entry("<svelte:element this={tag}>", "Renders an element whose tag name is only known at runtime.", "svelte-element"),
    "svelte:options" to Entry("<svelte:options …>", "Compiler options for this component, e.g. <code>runes={true}</code> or <code>customElement=\"my-el\"</code>.", "svelte-options"),
    "svelte:boundary" to Entry("<svelte:boundary>", "Error boundary: catches errors thrown while rendering its content and shows the <code>failed</code> snippet instead. Also shows <code>pending</code> while awaiting.", "svelte-boundary"),
    "svelte:component" to Entry("<svelte:component this={Component}>", "Svelte 4: renders a dynamic component.", "legacy-svelte-component", legacy = true,
      svelte5 = "Components are dynamic by default: <code>&lt;Thing /&gt;</code> where <code>Thing</code> is a variable."),
    "svelte:self" to Entry("<svelte:self>", "Svelte 4: renders the component recursively.", "legacy-svelte-self", legacy = true,
      svelte5 = "Import the component into itself: <code>import Self from './Self.svelte'</code>."),
    "svelte:fragment" to Entry("<svelte:fragment slot=\"name\">", "Svelte 4: fills a named slot without a wrapper element.", "legacy-svelte-fragment", legacy = true,
      svelte5 = "Use a snippet passed as a prop."),
    "slot" to Entry("<slot>", "Svelte 4: where the parent's content goes.", "legacy-slots", legacy = true,
      svelte5 = "Declare <code>let { children } = \$props()</code> and write <code>{@render children?.()}</code>; named slots become snippet props."),
    // ---- legacy script syntax
    "export let" to Entry("export let prop", "Svelte 4 prop declaration.", "legacy-export-let", legacy = true,
      svelte5 = "<code>let { prop = default } = \$props();</code>"),
    "\$:" to Entry("\$: statement", "Svelte 4 reactive statement.", "legacy-reactive-assignments", legacy = true,
      svelte5 = "Derived values: <code>let x = \$derived(…)</code>. Side effects: <code>\$effect(() => { … })</code>."),
    "createEventDispatcher" to Entry("createEventDispatcher()", "Svelte 4 component events.", "legacy-on", legacy = true,
      svelte5 = "Accept callback props instead: <code>let { onsave } = \$props(); onsave?.(data);</code>"),
    "\$\$props" to Entry("\$\$props / \$\$restProps", "Svelte 4 access to all props / undeclared props.", "legacy-\$\$props-and-\$\$restProps", legacy = true,
      svelte5 = "<code>let { a, b, ...rest } = \$props();</code>"),
  )

  fun html(entry: Entry): String = buildString {
    append("<div class='definition'><pre>").append(escape(entry.title)).append("</pre></div>")
    append("<div class='content'>")
    if (entry.legacy) append("<p><b>Svelte 4 (legacy) syntax.</b></p>")
    append("<p>").append(entry.body).append("</p>")
    entry.svelte5?.let { append("<p><b>Svelte 5:</b> ").append(it).append("</p>") }
    append("</div>")
    append("<div class='bottom'><a href='").append(url(entry)).append("'>svelte.dev/docs/svelte/").append(entry.slug).append("</a></div>")
  }

  private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
