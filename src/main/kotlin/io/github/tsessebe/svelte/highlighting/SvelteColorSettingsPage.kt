package io.github.tsessebe.svelte.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.tsessebe.svelte.lang.SvelteFileType
import javax.swing.Icon

class SvelteColorSettingsPage : ColorSettingsPage {
  override fun getDisplayName(): String = "Svelte"
  override fun getIcon(): Icon = SvelteFileType.ICON
  override fun getHighlighter(): SyntaxHighlighter = SvelteSyntaxHighlighter()
  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDemoText(): String = """
<script lang="ts">
  let { items = [], onselect }: { items: string[]; onselect?: (i: string) => void } = ${'$'}props();
  let count = ${'$'}state(0);
  let doubled = ${'$'}derived(count * 2);
</script>

<svelte:head><title>Demo</title></svelte:head>

{#snippet row(item: string)}
  <li class:active={item === 'a'} onclick={() => onselect?.(item)}>{item}</li>
{/snippet}

{#if items.length > 0}
  <ul>
    {#each items as item, i (item)}
      {@render row(item)}
    {/each}
  </ul>
{:else if count > 0}
  <Counter bind:value={count} {doubled} />
{:else}
  <p>Nothing yet</p>
{/if}

<!-- Svelte 4 (legacy) syntax -->
<button on:click|preventDefault={() => count++}>{@html '<b>+</b>'}</button>
""".trimIndent()

  companion object {
    private val DESCRIPTORS = arrayOf(
      AttributesDescriptor("Mustache braces", SvelteHighlighterColors.MUSTACHE_BRACES),
      AttributesDescriptor("Block keyword (#if, :else, /each)", SvelteHighlighterColors.BLOCK_KEYWORD),
      AttributesDescriptor("Special tag (@html, @render, @const)", SvelteHighlighterColors.SPECIAL_TAG),
      AttributesDescriptor("Block inner keyword (as, then, catch)", SvelteHighlighterColors.INNER_KEYWORD),
      AttributesDescriptor("Snippet name", SvelteHighlighterColors.SNIPPET_NAME),
      AttributesDescriptor("Punctuation", SvelteHighlighterColors.PUNCTUATION),
      AttributesDescriptor("Directive prefix (bind:, on:, class:)", SvelteHighlighterColors.DIRECTIVE_PREFIX),
      AttributesDescriptor("Directive name", SvelteHighlighterColors.DIRECTIVE_NAME),
      AttributesDescriptor("Directive modifier (|preventDefault)", SvelteHighlighterColors.DIRECTIVE_MODIFIER),
      AttributesDescriptor("svelte: element", SvelteHighlighterColors.SVELTE_ELEMENT),
      AttributesDescriptor("Component tag", SvelteHighlighterColors.COMPONENT),
      AttributesDescriptor("Component attribute (prop)", SvelteHighlighterColors.COMPONENT_ATTRIBUTE),
      AttributesDescriptor("Used import (non-component)", SvelteHighlighterColors.USED_IMPORT),
    )
  }
}
