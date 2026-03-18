package com.amity.socialcloud.uikit.community.livestream

import android.util.Log
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
import com.amity.socialcloud.sdk.model.video.room.AmityRoom
import com.amity.socialcloud.sdk.model.video.room.AmityRoomStatus
import com.amity.socialcloud.sdk.model.video.stream.AmityStream
import com.amity.socialcloud.uikit.common.base.AmityBaseViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

private const val POC_TAG = "POC_ROOM"

data class LivestreamRoomPocUiState(
    val livestreamPost: AmityPost? = null, // keep name to avoid fragment changes
    val stream: AmityStream? = null,
    val room: AmityRoom? = null,
    val creatorDisplayName: String = "",
    val creatorAvatarUrl: String = "",
    val isCurrentUserCreator: Boolean = false,
    val hasLivestream: Boolean = false, // keep name to avoid fragment changes (means "has room or livestream content")
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

        // Guaranteed visible log (even if Timber debug is off)
        Log.e(POC_TAG, "observeLivestreamPost() called communityId=$communityId")

        return AmitySocialClient.newFeedRepository()
            .getCommunityFeed(communityId)
            .feedType(AmityFeedType.PUBLISHED)
            .build()
            .query()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { pagingData ->

                Log.e(POC_TAG, "Feed doOnNext pagingData received")

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
                    val items = differ.snapshot().items
                    Log.e(POC_TAG, "Pages updated. snapshotSize=${items.size}")

                    // Print a few item child types to understand what's in the feed
                    items.take(10).forEach { post ->
                        val dataType = post.getChildren()
                            .firstOrNull()
                            ?.getData()
                            ?.javaClass
                            ?.name
                        Log.e(POC_TAG, "postId=${post.getPostId()} childType=$dataType")
                    }

                    val eventPost = items.firstOrNull { post ->
                        val data = post.getChildren().firstOrNull()?.getData()
                        data is AmityPost.Data.LIVE_STREAM || data is AmityPost.Data.ROOM
                    }

                    Log.e(
                        POC_TAG,
                        "eventPost=${eventPost?.getPostId()} selectedChildType=${
                            eventPost?.getChildren()?.firstOrNull()?.getData()?.javaClass?.name
                        }"
                    )

                    if (eventPost == null) {
                        Log.e(POC_TAG, "No LIVE_STREAM/ROOM post found -> emit default UPCOMING state")
                        onResult(LivestreamRoomPocUiState())
                        return@addOnPagesUpdatedListener
                    }

                    val childData = eventPost.getChildren().firstOrNull()?.getData()

                    when (childData) {
                        is AmityPost.Data.LIVE_STREAM -> {
                            Log.e(POC_TAG, "Handling LIVE_STREAM postId=${eventPost.getPostId()}")

                            childData.getStream()
                                .firstOrError()
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ stream ->
                                    Log.e(
                                        POC_TAG,
                                        "LIVE_STREAM streamId=${stream.getStreamId()} status=${stream.getStatus()} creatorId=${stream.getCreatorId()}"
                                    )

                                    resolveCreator(
                                        creatorId = stream.getCreatorId() ?: eventPost.getCreatorId(),
                                        fallbackCreator = eventPost.getCreator()
                                    ) { creator ->
                                        val currentUserId = AmityCoreClient.getUserId()
                                        val creatorId = stream.getCreatorId() ?: eventPost.getCreatorId()
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
                                            eventPost.getCreatedAt()?.toString("MMM d 'at' h:mm a") ?: ""

                                        Log.e(
                                            POC_TAG,
                                            "LIVE_STREAM computed canJoin=$canJoin label=$statusLabel date=$statusDate"
                                        )

                                        onResult(
                                            LivestreamRoomPocUiState(
                                                livestreamPost = eventPost,
                                                stream = stream,
                                                room = null,
                                                creatorDisplayName = creator?.getDisplayName() ?: "",
                                                creatorAvatarUrl = creator?.getAvatar()
                                                    ?.getUrl(AmityImage.Size.MEDIUM) ?: "",
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
                                    Log.e(POC_TAG, "LIVE_STREAM getStream failed: ${error.message}", error)
                                    onResult(LivestreamRoomPocUiState())
                                })
                        }

                        is AmityPost.Data.ROOM -> {
                            val room = childData.getRoom()

                            Log.e(
                                POC_TAG,
                                "Handling ROOM postId=${eventPost.getPostId()} roomId=${room?.getRoomId()} status=${room?.getStatus()} creatorId=${room?.getCreatorId()}"
                            )

                            resolveCreator(
                                creatorId = room?.getCreatorId() ?: eventPost.getCreatorId(),
                                fallbackCreator = eventPost.getCreator()
                            ) { creator ->
                                val currentUserId = AmityCoreClient.getUserId()
                                val creatorId = room?.getCreatorId() ?: eventPost.getCreatorId()
                                val isCurrentUserCreator = creatorId == currentUserId

                                val isLive = room?.getStatus() == AmityRoomStatus.LIVE
                                val isReplayable =
                                    room?.getStatus() == AmityRoomStatus.RECORDED ||
                                            room?.getStatus() == AmityRoomStatus.ENDED

                                val canJoin = isLive || isReplayable

                                val statusLabel = when {
                                    isLive -> "LIVE"
                                    isReplayable -> "PAST EVENT"
                                    else -> "UPCOMING"
                                }

                                val statusDate =
                                    eventPost.getCreatedAt()?.toString("MMM d 'at' h:mm a") ?: ""

                                Log.e(
                                    POC_TAG,
                                    "ROOM computed canJoin=$canJoin label=$statusLabel date=$statusDate"
                                )

                                onResult(
                                    LivestreamRoomPocUiState(
                                        livestreamPost = eventPost,
                                        stream = null,
                                        room = room,
                                        creatorDisplayName = creator?.getDisplayName() ?: "",
                                        creatorAvatarUrl = creator?.getAvatar()
                                            ?.getUrl(AmityImage.Size.MEDIUM) ?: "",
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
                        }

                        else -> {
                            Log.e(
                                POC_TAG,
                                "Unsupported child data type=${childData?.javaClass?.name} for postId=${eventPost.getPostId()}"
                            )
                            onResult(LivestreamRoomPocUiState())
                        }
                    }
                }

                viewModelScope.launch {
                    differ.submitData(pagingData)
                }
            }
            .doOnError { error ->
                Log.e(POC_TAG, "Feed query failed: ${error.message}", error)
                onResult(LivestreamRoomPocUiState())
            }
            .ignoreElements()
    }

    private fun resolveCreator(
        creatorId: String?,
        fallbackCreator: AmityUser?,
        onResolved: (AmityUser?) -> Unit
    ) {
        if (creatorId.isNullOrBlank()) {
            onResolved(fallbackCreator)
            return
        }

        AmityCoreClient.newUserRepository()
            .getUser(creatorId)
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ user ->
                onResolved(user)
            }, { error ->
                Timber.w(error, "resolveCreator failed, falling back to post creator")
                onResolved(fallbackCreator)
            })
    }
}