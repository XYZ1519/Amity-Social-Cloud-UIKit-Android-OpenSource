package com.amity.socialcloud.uikit.community.compose.socialhome.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.amity.socialcloud.uikit.common.ad.AmityListItem
import com.amity.socialcloud.uikit.community.compose.socialhome.AmitySocialHomePageViewModel.AuxiliaryContentState
import com.amity.socialcloud.uikit.community.compose.socialhome.AmitySocialHomePageViewModel.PostListState

internal fun derivePostListState(
    refreshLoadState: LoadState,
    appendLoadState: LoadState,
    renderableItemCount: Int,
    auxiliaryContentState: AuxiliaryContentState = AuxiliaryContentState.READY,
): PostListState {
    if (renderableItemCount > 0) return PostListState.SUCCESS

    return when {
        refreshLoadState is LoadState.Loading ||
            auxiliaryContentState == AuxiliaryContentState.LOADING -> PostListState.LOADING

        refreshLoadState is LoadState.Error ||
            appendLoadState is LoadState.Error ||
            auxiliaryContentState == AuxiliaryContentState.ERROR -> PostListState.ERROR

        refreshLoadState is LoadState.NotLoading &&
            appendLoadState is LoadState.NotLoading &&
            appendLoadState.endOfPaginationReached -> PostListState.EMPTY

        else -> PostListState.LOADING
    }
}

internal fun shouldRequestNextRenderablePage(
    refreshLoadState: LoadState,
    appendLoadState: LoadState,
    rawItemCount: Int,
    renderableItemCount: Int,
): Boolean {
    return renderableItemCount == 0 &&
        rawItemCount > 0 &&
        refreshLoadState is LoadState.NotLoading &&
        appendLoadState is LoadState.NotLoading &&
        !appendLoadState.endOfPaginationReached
}

/**
 * Keep paging until a drawable row is found or the source confirms that it is exhausted.
 *
 * This is load-bearing rather than an optimisation. When no row is renderable the feed shows the
 * empty component *instead of* the list, so nothing is on screen to touch a paging index and
 * ordinary scroll-driven pagination can never resume. Without this the feed would sit on a
 * permanent empty state while drawable content waited on the next page.
 *
 * It walks at most the whole feed, one page per pass, and stops as soon as a drawable row appears
 * or the source reports exhaustion. That upper bound is inherent: a feed cannot be known to hold
 * nothing drawable without looking at all of it.
 */
@Composable
internal fun RequestNextRenderableFeedPage(
    posts: LazyPagingItems<AmityListItem>,
    renderableItemCount: Int,
) {
    val shouldRequestNextPage = shouldRequestNextRenderablePage(
        refreshLoadState = posts.loadState.refresh,
        appendLoadState = posts.loadState.append,
        rawItemCount = posts.itemCount,
        renderableItemCount = renderableItemCount,
    )

    // Keyed on the append state too. A page that arrives holding nothing new leaves itemCount
    // unchanged -- an empty page, or placeholders, where itemCount is the total and never moves --
    // and shouldRequestNextPage stays true, so no key changes, this effect never runs again, and
    // the feed parks on EMPTY with endOfPaginationReached still false. The append state changes on
    // every completed load, with or without rows.
    LaunchedEffect(shouldRequestNextPage, posts.itemCount, posts.loadState.append) {
        if (!shouldRequestNextPage) return@LaunchedEffect

        // Re-read the count here rather than trusting the one this effect was composed with: a
        // refresh can empty the list in between, and posts[-1] would throw.
        val lastLoadedIndex = posts.itemCount - 1
        if (lastLoadedIndex >= 0) {
            posts[lastLoadedIndex]
        }
    }
}
