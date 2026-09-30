package com.amity.socialcloud.uikit.community.compose.feed

import androidx.paging.LoadState
import com.amity.socialcloud.uikit.community.compose.socialhome.AmitySocialHomePageViewModel.AuxiliaryContentState
import com.amity.socialcloud.uikit.community.compose.socialhome.AmitySocialHomePageViewModel.PostListState
import com.amity.socialcloud.uikit.community.compose.socialhome.components.derivePostListState
import com.amity.socialcloud.uikit.community.compose.socialhome.components.shouldRequestNextRenderablePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmityFeedStateTest {

    private val refreshComplete = LoadState.NotLoading(endOfPaginationReached = false)
    private val appendComplete = LoadState.NotLoading(endOfPaginationReached = true)
    private val appendIncomplete = LoadState.NotLoading(endOfPaginationReached = false)

    @Test
    fun `empty is decided by terminal append state`() {
        assertEquals(
            PostListState.EMPTY,
            derivePostListState(refreshComplete, appendComplete, renderableItemCount = 0),
        )
    }

    @Test
    fun `an incomplete source with no renderable content remains loading`() {
        assertEquals(
            PostListState.LOADING,
            derivePostListState(refreshComplete, appendIncomplete, renderableItemCount = 0),
        )
    }

    @Test
    fun `renderable content wins over source errors`() {
        assertEquals(
            PostListState.SUCCESS,
            derivePostListState(
                refreshLoadState = LoadState.Error(IllegalStateException()),
                appendLoadState = appendComplete,
                renderableItemCount = 1,
                auxiliaryContentState = AuxiliaryContentState.ERROR,
            ),
        )
    }

    @Test
    fun `auxiliary loading prevents a premature empty state`() {
        assertEquals(
            PostListState.LOADING,
            derivePostListState(
                refreshComplete,
                appendComplete,
                renderableItemCount = 0,
                auxiliaryContentState = AuxiliaryContentState.LOADING,
            ),
        )
    }

    @Test
    fun `auxiliary failure is an error when no content is visible`() {
        assertEquals(
            PostListState.ERROR,
            derivePostListState(
                refreshComplete,
                appendComplete,
                renderableItemCount = 0,
                auxiliaryContentState = AuxiliaryContentState.ERROR,
            ),
        )
    }

    @Test
    fun `append failure is an error when no content is visible`() {
        assertEquals(
            PostListState.ERROR,
            derivePostListState(
                refreshComplete,
                LoadState.Error(IllegalStateException()),
                renderableItemCount = 0,
            ),
        )
    }

    @Test
    fun `an undrawable page requests the next page`() {
        assertTrue(
            shouldRequestNextRenderablePage(
                refreshComplete,
                appendIncomplete,
                rawItemCount = 10,
                renderableItemCount = 0,
            ),
        )
    }

    @Test
    fun `pagination is not advanced after content or exhaustion`() {
        assertFalse(
            shouldRequestNextRenderablePage(
                refreshComplete,
                appendIncomplete,
                rawItemCount = 10,
                renderableItemCount = 1,
            ),
        )
        assertFalse(
            shouldRequestNextRenderablePage(
                refreshComplete,
                appendComplete,
                rawItemCount = 10,
                renderableItemCount = 0,
            ),
        )
    }

    @Test
    fun `pagination is not advanced when nothing is loaded yet`() {
        // First line of defence for the index read in RequestNextRenderableFeedPage: with no
        // loaded rows there is no last index to touch, and asking for one would be posts[-1].
        assertFalse(
            shouldRequestNextRenderablePage(
                refreshLoadState = LoadState.NotLoading(endOfPaginationReached = false),
                appendLoadState = LoadState.NotLoading(endOfPaginationReached = false),
                rawItemCount = 0,
                renderableItemCount = 0,
            )
        )
    }
}
