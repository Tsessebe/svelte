package io.github.tsessebe.svelte

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.tsessebe.svelte.kit.SvelteKitRoutes
import io.github.tsessebe.svelte.kit.SvelteKitRoutes.Kind
import io.github.tsessebe.svelte.kit.SvelteKitTabTitleProvider

class SvelteKitTest : BasePlatformTestCase() {

  private fun file(path: String) = myFixture.addFileToProject(path, "").virtualFile

  fun testKindsAndRoutes() {
    val page = file("src/routes/members/member-view/+page.svelte")
    assertEquals(Kind.PAGE, SvelteKitRoutes.kind(page))
    assertEquals("/members/member-view", SvelteKitRoutes.routePath(page))
    assertEquals(Kind.LAYOUT, SvelteKitRoutes.kind(file("src/routes/+layout.svelte")))
    assertEquals(Kind.LOAD, SvelteKitRoutes.kind(file("src/routes/+layout.ts")))
    assertEquals(Kind.SERVER, SvelteKitRoutes.kind(file("src/routes/api/+server.ts")))
    assertEquals(Kind.SERVER, SvelteKitRoutes.kind(file("src/routes/members/+page.server.ts")))
    assertEquals(Kind.ERROR, SvelteKitRoutes.kind(file("src/routes/+error.svelte")))
    assertNull(SvelteKitRoutes.kind(file("src/routes/layout.test.ts")))
    assertNull(SvelteKitRoutes.kind(file("src/lib/+page.svelte")))
    assertEquals("/", SvelteKitRoutes.routePath(file("src/routes/+page.svelte")))
  }

  fun testTabTitle() {
    val page = file("src/routes/members/+page.svelte")
    val load = file("src/routes/members/+page.server.ts")
    val provider = SvelteKitTabTitleProvider()
    assertEquals("/members (+page)", provider.getEditorTabTitle(project, page))
    assertEquals("/members (+page.server.ts)", provider.getEditorTabTitle(project, load))
    assertNull(provider.getEditorTabTitle(project, file("src/lib/Card.svelte")))
  }

  fun testRelatedFiles() {
    val page = file("src/routes/members/member-view/+page.svelte")
    file("src/routes/members/member-view/+page.ts")
    file("src/routes/members/+layout.svelte")
    file("src/routes/+layout.svelte")
    file("src/routes/+error.svelte")
    file("src/routes/members/other/+page.svelte")
    val names = SvelteKitRoutes.relatedFiles(page).map { SvelteKitRoutes.routePath(it) + "/" + it.name }
    assertEquals(listOf("/members/member-view/+page.ts", "/members/+layout.svelte", "//+error.svelte", "//+layout.svelte"), names)
  }
}
