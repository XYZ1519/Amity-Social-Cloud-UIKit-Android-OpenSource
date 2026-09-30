package com.amity.socialcloud.uikit.community.compose.paging.feed.global

import com.amity.socialcloud.sdk.model.core.pin.AmityPinnedPost
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.uikit.common.ad.AmityListItem
import com.amity.socialcloud.uikit.common.utils.isSupportedDataTypes

/**
 * The single source of truth for "can this post be shown in a global-style feed".
 *
 * The empty state must be driven by RENDERABLE content, not by raw source sizes: a source
 * that holds items the renderer skips (an unsupported data type, a deleted post) contributes
 * nothing on screen and must therefore contribute nothing to the count. Before this existed the
 * three render paths disagreed — pinned checked type + deleted, the paginated path checked type
 * only, and locally created posts were checked not at all — so any count written beside a
 * renderer was guaranteed to drift from it.
 *
 * Everything that renders, and everything that counts, goes through the functions below. A
 * future change to what a feed skips belongs HERE, and the counts follow automatically.
 *
 * Deliberately `internal`: [isSupportedDataTypes] lives in common-compose and is public because
 * it is used across modules, but this predicate is only meaningful to the global feed surfaces,
 * so it adds no public API surface to the UIKit.
 */
internal fun AmityPost.isRenderableInFeed(): Boolean {
    return isSupportedDataTypes() && !isDeleted()
}

/** The pinned posts a feed can actually show, in order, with unrenderable entries dropped. */
internal fun List<AmityPinnedPost>.renderablePinnedPosts(): List<AmityPinnedPost> {
    return filter { pinned -> pinned.post?.isRenderableInFeed() == true }
}

/**
 * A paginated row is renderable when it is a supported, undeleted post that is not already
 * shown by one of the auxiliary sections above the paginated list.
 *
 * Ads always render, matching both the existing renderers and iOS (`prepareFeedPosts` appends
 * `.ad` unconditionally). A null row is a Paging placeholder — a shimmer, not content.
 */
internal fun AmityListItem?.isRenderableFeedItem(
    pinnedPostIds: Set<String>,
    createdPostIds: Set<String>,
): Boolean {
    return when (this) {
        is AmityListItem.PostItem -> {
            val postId = post.getPostId()
            post.isRenderableInFeed() && postId !in pinnedPostIds && postId !in createdPostIds
        }

        is AmityListItem.AdItem -> true
        else -> false
    }
}

/**
 * How many of the ALREADY-LOADED paginated rows the renderer would show.
 *
 * Callers pass `posts.itemSnapshotList.items`, which reads loaded items only and never triggers
 * a page load — unlike indexing `LazyPagingItems`, and unlike `itemCount`, which also counts
 * placeholders the renderer draws as shimmers rather than content.
 */
internal fun List<AmityListItem?>.renderableFeedItemCount(
    pinnedPostIds: Set<String>,
    createdPostIds: Set<String>,
): Int {
    return count { item -> item.isRenderableFeedItem(pinnedPostIds, createdPostIds) }
}

/** Post ids of the given pinned posts, for de-duplicating the paginated section against them. */
internal fun List<AmityPinnedPost>.pinnedPostIds(): Set<String> {
    return mapTo(mutableSetOf()) { it.postId }
}

/** Post ids of the given posts, for de-duplicating the paginated section against them. */
internal fun List<AmityPost>.postIds(): Set<String> {
    return mapTo(mutableSetOf()) { it.getPostId() }
}
