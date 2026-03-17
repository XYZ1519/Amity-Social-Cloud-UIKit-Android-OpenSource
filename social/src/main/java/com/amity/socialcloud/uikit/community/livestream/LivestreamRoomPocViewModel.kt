package com.amity.socialcloud.uikit.community.livestream

import androidx.lifecycle.viewModelScope
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.amity.socialcloud.sdk.api.core.AmityCoreClient
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.model.core.file.AmityImage
import com.amity.socialcloud.sdk.model.core.user.AmityUser
import com.amity.socialcloud.sdk.model.social.feed.AmityFeedType
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.sdk.model.video.stream.AmityStream
import com.amity.socialcloud.uikit.common.base.AmityBaseViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

data class LivestreamRoomPocUiState(
    val livestreamPost: AmityPost? = null,
    val stream: AmityStream? = null,
    val creatorDisplayName: String = "",
    val creatorAvatarUrl: String = "",
    val isCurrentUserCreator: Boolean = false,
    val hasLivestream: Boolean = false,
    val canJoin: Boolean = false,
    val canCreate: Boolean = true,
    val shouldShowReplay: Boolean = false,
    val statusLabel: String = "UPCOMING",
    val statusDate: String = ""
)

class LivestreamRoomPocViewModel : AmityBaseViewModel() {

    fun observeLivestreamPost(
        communityId: String,
        onResult: (LivestreamRoomPocUiState) -> Unit
    ): Completable {
        return AmitySocialClient.newFeedRepository()
            .getCommunityFeed(communityId)
            .feedType(AmityFeedType.PUBLISHED)
            .build()
            .query()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { pagingData ->

                val differ = AsyncPagingDataDiffer(
                    diffCallback = object : DiffUtil.ItemCallback<AmityPost>() {
                        override fun areItemsTheSame(oldItem: AmityPost, newItem: AmityPost): Boolean {
                            return oldItem.getPostId() == newItem.getPostId()
                        }

                        override fun areContentsTheSame(oldItem: AmityPost, newItem: AmityPost): Boolean {
                            return oldItem.getPostId() == newItem.getPostId()
                        }
                    },
                    updateCallback = object : ListUpdateCallback {
                        override fun onInserted(position: Int, count: Int) = Unit
                        override fun onRemoved(position: Int, count: Int) = Unit
                        override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                        override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
                    },
                    mainDispatcher = Dispatchers.Main,
                    workerDispatcher = Dispatchers.Default
                )

                differ.addOnPagesUpdatedListener {
                    val livestreamPost = differ.snapshot().items.firstOrNull { post ->
                        val children = post.getChildren()
                        val childData = if (children.isNotEmpty()) children[0].getData() else null
                        childData is AmityPost.Data.LIVE_STREAM
                    }

                    if (livestreamPost == null) {
                        onResult(
                            LivestreamRoomPocUiState(
                                hasLivestream = false,
                                canJoin = false,
                                canCreate = true,
                                shouldShowReplay = false,
                                statusLabel = "UPCOMING",
                                statusDate = ""
                            )
                        )
                        return@addOnPagesUpdatedListener
                    }

                    val childData = livestreamPost.getChildren()[0].getData() as AmityPost.Data.LIVE_STREAM

                    childData.getStream()
                        .firstOrError()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ stream ->
                            resolveCreator(stream, livestreamPost) { creator ->
                                val currentUserId = AmityCoreClient.getUserId()
                                val creatorId = stream.getCreatorId() ?: livestreamPost.getCreatorId()
                                val isCurrentUserCreator = creatorId == currentUserId

                                val isLive = stream.getStatus() == AmityStream.Status.LIVE
                                val isReplayable =
                                    stream.getStatus() == AmityStream.Status.RECORDED ||
                                            stream.getStatus() == AmityStream.Status.ENDED

                                val canJoin = isLive || isReplayable

                                val statusLabel = when {
                                    isLive -> "LIVE"
                                    isReplayable -> "PAST EVENT"
                                    else -> "UPCOMING"
                                }

                                val statusDate =
                                    livestreamPost.getCreatedAt()?.toString("MMM d 'at' h:mm a") ?: ""

                                onResult(
                                    LivestreamRoomPocUiState(
                                        livestreamPost = livestreamPost,
                                        stream = stream,
                                        creatorDisplayName = creator?.getDisplayName() ?: "",
                                        creatorAvatarUrl = creator?.getAvatar()?.getUrl(AmityImage.Size.MEDIUM)
                                            ?: "",
                                        isCurrentUserCreator = isCurrentUserCreator,
                                        hasLivestream = true,
                                        canJoin = canJoin,
                                        canCreate = false,
                                        shouldShowReplay = isReplayable,
                                        statusLabel = statusLabel,
                                        statusDate = statusDate
                                    )
                                )
                            }
                        }, { error ->
                            Timber.e(error)
                            onResult(
                                LivestreamRoomPocUiState(
                                    hasLivestream = false,
                                    canJoin = false,
                                    canCreate = true,
                                    shouldShowReplay = false,
                                    statusLabel = "UPCOMING",
                                    statusDate = ""
                                )
                            )
                        })
                }

                viewModelScope.launch {
                    differ.submitData(pagingData)
                }
            }
            .doOnError { error ->
                Timber.e(error)
                onResult(
                    LivestreamRoomPocUiState(
                        hasLivestream = false,
                        canJoin = false,
                        canCreate = true,
                        shouldShowReplay = false,
                        statusLabel = "UPCOMING",
                        statusDate = ""
                    )
                )
            }
            .ignoreElements()
    }

    private fun resolveCreator(
        stream: AmityStream,
        post: AmityPost,
        onResolved: (AmityUser?) -> Unit
    ) {
        val creatorId = stream.getCreatorId() ?: post.getCreatorId()

        if (creatorId.isNullOrBlank()) {
            onResolved(post.getCreator())
            return
        }

        AmityCoreClient.newUserRepository()
            .getUser(creatorId)
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ user ->
                onResolved(user)
            }, {
                onResolved(post.getCreator())
            })
    }
}