package com.amity.socialcloud.uikit.community.compose.feed

import com.amity.socialcloud.sdk.model.core.pin.AmityPinnedPost
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.uikit.common.ad.AmityListItem
import com.amity.socialcloud.uikit.community.compose.paging.feed.global.isRenderableFeedItem
import com.amity.socialcloud.uikit.community.compose.paging.feed.global.isRenderableInFeed
import com.amity.socialcloud.uikit.community.compose.paging.feed.global.renderableFeedItemCount
import com.amity.socialcloud.uikit.community.compose.paging.feed.global.renderablePinnedPosts
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The empty state must be driven by RENDERABLE content. These tests pin the one predicate that
 * both the renderers and the empty-state counts delegate to, so a source holding items the UI
 * cannot draw contributes nothing to "this feed has content".
 *
 * Deliberately no fabricated Paging LoadState anywhere: the state machine itself is unchanged
 * from trunk, and a test that hand-builds a load state proves nothing about how the components
 * are wired to it.
 */
class AmityFeedRenderabilityTest {

    private fun post(
        id: String,
        supported: Boolean = true,
        deleted: Boolean = false,
    ): AmityPost = mockk<AmityPost>(relaxed = true).also { post ->
        every { post.getPostId() } returns id
        every { post.isDeleted() } returns deleted
        // isSupportedDataTypes() reads getChildren(): no children => supported; a FILE child is
        // one of the types this UIKit cannot draw (matching iOS canRenderPost, which excludes
        // file/audio/mixed).
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

    private fun pinned(post: AmityPost?, id: String = "pinned"): AmityPinnedPost =
        mockk<AmityPinnedPost>(relaxed = true).also {
            every { it.post } returns post
            every { it.postId } returns (post?.getPostId() ?: id)
        }

    @Test
    fun `a supported undeleted post is renderable`() {
        assertTrue(post("p1").isRenderableInFeed())
    }

    @Test
    fun `an unsupported data type is not renderable`() {
        assertFalse(post("p1", supported = false).isRenderableInFeed())
    }

    @Test
    fun `a deleted post is not renderable`() {
        // NEW vs trunk for the paginated + created sources, which previously checked type only.
        assertFalse(post("p1", deleted = true).isRenderableInFeed())
    }

    @Test
    fun `renderablePinnedPosts drops entries the renderer would skip and keeps order`() {
        val keep1 = pinned(post("keep1"))
        val keep2 = pinned(post("keep2"))
        val list = listOf(
            pinned(null, id = "nullpost"),
            keep1,
            pinned(post("unsupported", supported = false)),
            pinned(post("deleted", deleted = true)),
            keep2,
        )

        assertEquals(listOf(keep1, keep2), list.renderablePinnedPosts())
    }

    @Test
    fun `a renderable paginated post counts`() {
        val item = AmityListItem.PostItem(post("p1"))
        assertTrue(item.isRenderableFeedItem(emptySet(), emptySet()))
    }

    @Test
    fun `a paginated post already shown as pinned does not count twice`() {
        val item = AmityListItem.PostItem(post("p1"))
        assertFalse(item.isRenderableFeedItem(setOf("p1"), emptySet()))
    }

    @Test
    fun `a paginated post already shown as locally created does not count twice`() {
        val item = AmityListItem.PostItem(post("p1"))
        assertFalse(item.isRenderableFeedItem(emptySet(), setOf("p1")))
    }

    @Test
    fun `an unsupported paginated post does not count`() {
        val item = AmityListItem.PostItem(post("p1", supported = false))
        assertFalse(item.isRenderableFeedItem(emptySet(), emptySet()))
    }

    @Test
    fun `an ad always counts as renderable content`() {
        assertTrue(AmityListItem.AdItem(mockk(relaxed = true)).isRenderableFeedItem(emptySet(), emptySet()))
    }

    @Test
    fun `a paging placeholder is not content`() {
        val placeholder: AmityListItem? = null
        assertFalse(placeholder.isRenderableFeedItem(emptySet(), emptySet()))
    }

    @Test
    fun `a separator is not content`() {
        assertFalse(AmityListItem.Separator.isRenderableFeedItem(emptySet(), emptySet()))
    }

    @Test
    fun `renderableFeedItemCount counts only what the renderer would draw`() {
        val items = listOf<AmityListItem?>(
            AmityListItem.PostItem(post("keep")),
            AmityListItem.PostItem(post("unsupported", supported = false)),
            AmityListItem.PostItem(post("deleted", deleted = true)),
            AmityListItem.PostItem(post("dupePinned")),
            AmityListItem.AdItem(mockk(relaxed = true)),
            AmityListItem.Separator,
            null,
        )

        val count = items.renderableFeedItemCount(
            pinnedPostIds = setOf("dupePinned"),
            createdPostIds = emptySet(),
        )

        assertEquals(2, count) // "keep" + the ad
    }

    @Test
    fun `a page of only undrawable posts counts as no content`() {
        // The regression this whole change exists for: a non-empty source whose items the UI
        // cannot draw must read as EMPTY, not as a silently blank SUCCESS.
        val items = listOf<AmityListItem?>(
            AmityListItem.PostItem(post("a", supported = false)),
            AmityListItem.PostItem(post("b", deleted = true)),
        )

        assertEquals(0, items.renderableFeedItemCount(emptySet(), emptySet()))
    }
}
