package com.amity.socialcloud.uikit.community.compose.socialhome.components

/**
 * Which auxiliary sources the FOLLOWING feed shows alongside its own paginated posts.
 *
 * ANDROID-ONLY BEHAVIOUR — kept deliberately, pending confirmation (2026-08-26).
 *
 * Android renders global pinned posts and locally created posts on the Following feed. iOS
 * renders NEITHER there: in `PostFeedViewModel.swift`, `prepareGlobalPinnedPosts()` opens with
 * `guard feedType == .globalFeed else { return [] }`, and `renderFeed()` appends
 * `recentlyCreatedPosts` only `if !recentlyCreatedPosts.isEmpty, feedType == .globalFeed`.
 *
 * TO ALIGN WITH iOS: set both constants to `false`. That is the entire change — no other file
 * needs to be touched. Both the render gate and the renderable count in
 * [AmityNewsFeedComponent] read these same constants, so the source cannot be counted while
 * being hidden (or vice versa), which is the exact inconsistency this feed's empty-state bug
 * came from. Kotlin will also flag the resulting branches as unreachable, making the dead code
 * easy to delete afterwards.
 *
 * These gate FOLLOWING only. The For You feed and the standalone global feed component are
 * unaffected and always render whichever auxiliary sources they own.
 */
internal object AmityFeedAuxiliarySources {

    const val FOLLOWING_SHOWS_PINNED_POSTS = true

    const val FOLLOWING_SHOWS_CREATED_POSTS = true
}
