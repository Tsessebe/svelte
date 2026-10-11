package io.github.tsessebe.svelte.kit

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * SvelteKit route files: `+page.svelte`, `+layout.ts`, `+page.server.ts`, `+server.ts`, `+error.svelte`, …
 * inside a `routes` folder (src/routes by default).
 */
object SvelteKitRoutes {
  enum class Kind(val icon: Icon) {
    PAGE(icon("kitPage")), LAYOUT(icon("kitLayout")), ERROR(icon("kitError")),
    LOAD(icon("kitLoad")),     // +page.ts / +layout.ts (universal load)
    SERVER(icon("kitServer")); // +page.server.ts, +layout.server.ts, +server.ts
  }

  private fun icon(name: String): Icon = IconLoader.getIcon("/icons/$name.svg", SvelteKitRoutes::class.java)

  private val EXTENSIONS = setOf("svelte", "ts", "js")

  fun isRouteFile(file: VirtualFile): Boolean = kind(file) != null

  fun kind(file: VirtualFile): Kind? {
    val name = file.name
    if (!name.startsWith("+") || file.extension !in EXTENSIONS || routesRoot(file) == null) return null
    val base = name.substringBefore('.')
    val server = name.contains(".server.") || base == "+server"
    return when {
      server -> Kind.SERVER
      base == "+error" -> Kind.ERROR
      base == "+page" -> if (file.extension == "svelte") Kind.PAGE else Kind.LOAD
      base == "+layout" -> if (file.extension == "svelte") Kind.LAYOUT else Kind.LOAD
      else -> null
    }
  }

  /** The `routes` folder this file lives under (nearest ancestor), or null. */
  fun routesRoot(file: VirtualFile): VirtualFile? {
    var dir = file.parent
    while (dir != null) {
      if (dir.name == "routes") return dir
      dir = dir.parent
    }
    return null
  }

  /** URL-ish route of the file's folder: "/", "/members/member-view", "/(app)/blog/[slug]". */
  fun routePath(file: VirtualFile): String {
    val root = routesRoot(file) ?: return ""
    val segments = mutableListOf<String>()
    var dir = file.parent
    while (dir != null && dir != root) {
      segments.add(0, dir.name)
      dir = dir.parent
    }
    return "/" + segments.joinToString("/")
  }

  /** Route files in the same folder plus layouts/errors of all enclosing folders up to `routes`. */
  fun relatedFiles(file: VirtualFile): List<VirtualFile> {
    val root = routesRoot(file) ?: return emptyList()
    val result = mutableListOf<VirtualFile>()
    file.parent?.children?.filter { it != file && kind(it) != null }?.sortedBy { it.name }?.let(result::addAll)
    var dir = file.parent
    while (dir != null && dir != root) {
      dir = dir.parent ?: break
      dir.children.filter { val k = kind(it); k == Kind.LAYOUT || k == Kind.ERROR || (k == Kind.LOAD && it.name.startsWith("+layout")) || (k == Kind.SERVER && it.name.startsWith("+layout")) }
        .sortedBy { it.name }.let(result::addAll)
    }
    return result
  }
}
