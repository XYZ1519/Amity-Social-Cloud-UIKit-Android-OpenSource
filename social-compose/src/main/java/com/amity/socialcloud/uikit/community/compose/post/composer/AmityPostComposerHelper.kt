package com.amity.socialcloud.uikit.community.compose.post.composer

import androidx.compose.runtime.mutableStateListOf
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.uikit.community.compose.paging.feed.global.isRenderableInFeed
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

object AmityPostComposerHelper {

    /**
     * The composer and the feed can live in different activities, so this stays a process-level
     * bridge. It is a snapshot-backed list so that a feed reading it during composition is
     * subscribed to it: an add or a delete then shows up on the next frame instead of waiting
     * for some unrelated paging emission to happen to recompose the list.
     */
    private val createdPosts = mutableStateListOf<AmityPost>()

    private var nextFeedOwnerId = 0L
    private var activeFeedOwnerId: Long? = null

    @Synchronized
    fun addNewPost(post: AmityPost) {
        if (createdPosts.none { it.getPostId() == post.getPostId() }) {
            createdPosts.add(0, post)
        }
    }

    fun updatePost(postId: String) {
        AmitySocialClient.newPostRepository()
            .getPost(postId)
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { post ->
                replaceIfPresent(post)
            }
            .doOnError {
                // ignore
            }
            .subscribe()

    }

    /**
     * Replaces an entry already in the hand-off. Deliberately does nothing when the post is absent:
     * that is what makes an edit landing after a refresh harmless without any token to check — the
     * list was cleared, so there is nothing to update.
     */
    @Synchronized
    private fun replaceIfPresent(post: AmityPost) {
        val updateIndex = createdPosts.indexOfFirst { it.getPostId() == post.getPostId() }
        if (updateIndex != -1) {
            createdPosts[updateIndex] = post
        }
    }

    @Synchronized
    fun deletePost(postId: String) {
        val deleteIndex = createdPosts.indexOfFirst { it.getPostId() == postId }
        if (deleteIndex != -1) {
            createdPosts.removeAt(deleteIndex)
        }
    }

    /**
     * The locally created posts a feed can actually show.
     *
     * Filtered at the source rather than at each call site: both feed renderers read this, so
     * filtering here means the rows that render and the count that decides the empty state are
     * derived from the same list — they cannot disagree. A post that has since been deleted, or
     * whose type this UIKit cannot draw, is neither rendered nor counted.
     */
    fun getCreatedPosts(): List<AmityPost> {
        // Deliberately NOT synchronized. The monitor exists to make check-then-mutate atomic;
        // this is a pure read, and it runs inside composition on every recomposition of a
        // scrolling feed, so holding a lock here would put the main thread behind background
        // composer callbacks for no gain. `toList()` takes a stable copy of the snapshot list so
        // a concurrent mutation cannot be observed part-way through the filtering below.
        return createdPosts.toList()
            .distinctBy { it.getPostId() }
            .filter { it.isRenderableInFeed() }
    }

    /**
     * Gives a newly created feed page ownership of the process-level hand-off, and starts it empty.
     *
     * The hand-off lives exactly as long as the feed page that owns it. A recreated page does not
     * inherit what an earlier one was showing, which is what makes the session dimension
     * unnecessary here: posts cannot outlive the page, so they cannot cross a sign-in either. This
     * matches iOS, where `recentlyCreatedPosts` is per-ViewModel state cleared by `loadFeed()`.
     *
     * The cost, accepted deliberately: a post handed over while the feed page is being recreated
     * (process death, or a configuration change that loses the ViewModel) is dropped, and only
     * reappears once the server snapshot includes it. iOS behaves the same way.
     */
    @Synchronized
    internal fun attachFeed(): Long {
        nextFeedOwnerId += 1
        activeFeedOwnerId = nextFeedOwnerId
        invalidateLocked()
        return nextFeedOwnerId
    }

    /** An older ViewModel cannot clear content owned by a newer feed page. */
    @Synchronized
    internal fun detachFeed(ownerId: Long) {
        if (activeFeedOwnerId != ownerId) return

        invalidateLocked()
        activeFeedOwnerId = null
    }

    @Synchronized
    internal fun clearForRefresh(ownerId: Long) {
        if (activeFeedOwnerId == ownerId) {
            invalidateLocked()
        }
    }

    @Synchronized
    fun clear() {
        invalidateLocked()
        activeFeedOwnerId = null
    }

    private fun invalidateLocked() {
        createdPosts.clear()
    }
}
