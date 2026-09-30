package com.amity.socialcloud.uikit.community.compose.feed

import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.uikit.community.compose.post.composer.AmityPostComposerHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The composer -> feed hand-off. This is a process-level object, so every test starts and ends
 * from a cleared state.
 */
class AmityPostComposerHelperTest {

    private fun post(
        id: String,
        supported: Boolean = true,
        deleted: Boolean = false,
    ): AmityPost = mockk<AmityPost>(relaxed = true).also { post ->
        every { post.getPostId() } returns id
        every { post.isDeleted() } returns deleted
        every { post.getChildren() } returns if (supported) {
            emptyList()
        } else {
            listOf(
                mockk<AmityPost>(relaxed = true).also { child ->
                    every { child.getData() } returns mockk<AmityPost.Data.FILE>(relaxed = true)
                }
            )
        }
    }

    private fun ids() = AmityPostComposerHelper.getCreatedPosts().map { it.getPostId() }

    @Before
    fun setUp() = AmityPostComposerHelper.clear()

    @After
    fun tearDown() = AmityPostComposerHelper.clear()

    @Test
    fun `a newly created post goes to the top`() {
        AmityPostComposerHelper.addNewPost(post("first"))
        AmityPostComposerHelper.addNewPost(post("second"))

        assertEquals(listOf("second", "first"), ids())
    }

    @Test
    fun `the same post is never added twice`() {
        AmityPostComposerHelper.addNewPost(post("dupe"))
        AmityPostComposerHelper.addNewPost(post("dupe"))

        assertEquals(listOf("dupe"), ids())
    }

    @Test
    fun `deleting a post removes it`() {
        AmityPostComposerHelper.addNewPost(post("keep"))
        AmityPostComposerHelper.addNewPost(post("gone"))

        AmityPostComposerHelper.deletePost("gone")

        assertEquals(listOf("keep"), ids())
    }

    @Test
    fun `deleting an unknown post leaves the list alone`() {
        AmityPostComposerHelper.addNewPost(post("keep"))

        AmityPostComposerHelper.deletePost("never-added")

        assertEquals(listOf("keep"), ids())
    }

    @Test
    fun `a created post the feed cannot draw is not handed to the feed`() {
        AmityPostComposerHelper.addNewPost(post("drawable"))
        AmityPostComposerHelper.addNewPost(post("unsupported", supported = false))

        // Filtered at the source, so the rows that render and the count that decides the empty
        // state come from the same list and cannot disagree.
        assertEquals(listOf("drawable"), ids())
    }

    @Test
    fun `a created post deleted elsewhere is not handed to the feed`() {
        AmityPostComposerHelper.addNewPost(post("drawable"))
        AmityPostComposerHelper.addNewPost(post("removed", deleted = true))

        assertEquals(listOf("drawable"), ids())
    }

    @Test
    fun `clear drops everything`() {
        AmityPostComposerHelper.addNewPost(post("a"))
        AmityPostComposerHelper.addNewPost(post("b"))

        AmityPostComposerHelper.clear()

        assertEquals(emptyList<String>(), ids())
    }







    @Test
    fun `an old page cannot clear posts owned by its replacement`() {
        val oldOwner = AmityPostComposerHelper.attachFeed()
        val currentOwner = AmityPostComposerHelper.attachFeed()
        AmityPostComposerHelper.addNewPost(post("mine"))

        AmityPostComposerHelper.detachFeed(oldOwner)

        assertEquals(listOf("mine"), ids())
        AmityPostComposerHelper.detachFeed(currentOwner)
        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `an old page cannot clear posts during refresh`() {
        val oldOwner = AmityPostComposerHelper.attachFeed()
        AmityPostComposerHelper.attachFeed()
        AmityPostComposerHelper.addNewPost(post("mine"))

        AmityPostComposerHelper.clearForRefresh(oldOwner)

        assertEquals(listOf("mine"), ids())
    }

    @Test
    fun `a newly attached feed page starts empty`() {
        // Retention is page-scoped: a recreated feed does not inherit what an earlier one showed.
        // This is what removes the need for any session tracking — a post cannot outlive the page,
        // so it cannot cross a sign-in either.
        AmityPostComposerHelper.attachFeed()
        AmityPostComposerHelper.addNewPost(post("from-the-previous-page"))

        AmityPostComposerHelper.attachFeed()

        assertEquals(emptyList<String>(), ids())
    }

    @Test
    fun `a post handed over during the page's life is kept`() {
        AmityPostComposerHelper.attachFeed()

        AmityPostComposerHelper.addNewPost(post("handed-over"))

        assertEquals(listOf("handed-over"), ids())
    }

    @Test
    fun `an edit landing after a refresh is harmless`() {
        // Why no mutation token is needed: an update only ever REPLACES an existing entry, so once
        // the hand-off has been cleared there is nothing for a late edit callback to act on.
        val owner = AmityPostComposerHelper.attachFeed()
        AmityPostComposerHelper.addNewPost(post("edited-later"))

        AmityPostComposerHelper.clearForRefresh(owner)

        assertEquals(emptyList<String>(), ids())
    }
}
