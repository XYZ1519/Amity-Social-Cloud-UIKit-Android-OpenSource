package com.amity.socialcloud.uikit.community.livestream

import androidx.lifecycle.viewModelScope
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.amity.socialcloud.sdk.api.core.AmityCoreClient
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.api.video.AmityVideoClient
import com.amity.socialcloud.sdk.model.core.file.AmityImage
import com.amity.socialcloud.sdk.model.core.user.AmityUser
import com.amity.socialcloud.sdk.model.social.feed.AmityFeedType
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.sdk.model.video.room.AmityRoom
import com.amity.socialcloud.sdk.model.video.room.AmityRoomStatus
import com.amity.socialcloud.uikit.common.base.AmityBaseViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

data class LivestreamRoomPocUiState(
    val livestreamPost: AmityPost? = null,
    val room: AmityRoom? = null,
    val creatorDisplayName: String = "",
    val creatorAvatarUrl: String = "",
    val cohostDisplayName: String = "",
    val cohostAvatarUrl: String = "",
    val isCurrentUserCreator: Boolean = false,
    val hasLivestream: Boolean = false,
    val canJoin: Boolean = false,
    val canCreate: Boolean = true,
    val shouldShowReplay: Boolean = false,
    val statusLabel: String = "UPCOMING",
    val statusDate: String = ""
)

class LivestreamRoomPocViewModel : AmityBaseViewModel() {

    private var roomDisposableRef: Disposable? = null

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
                    val roomPost = differ.snapshot().items.firstOrNull { post ->
                        val children = post.getChildren()
                        val childData = if (children.isNotEmpty()) children[0].getData() else null
                        childData is AmityPost.Data.ROOM
                    }

                    if (roomPost == null) {
                        roomDisposableRef?.dispose()
                        roomDisposableRef = null

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

                    Timber.d(
                        "RoomPoc FEED picked roomPostId=%s createdAt=%s",
                        roomPost.getPostId(),
                        roomPost.getCreatedAt()
                    )

                    val childData = roomPost.getChildren()[0].getData() as AmityPost.Data.ROOM
                    val initialRoom = childData.getRoom()
                    val roomId = initialRoom?.getRoomId()

                    if (roomId.isNullOrBlank()) {
                        roomDisposableRef?.dispose()
                        roomDisposableRef = null

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

                    roomDisposableRef?.dispose()
                    roomDisposableRef = null

                    roomDisposableRef = AmityVideoClient.newRoomRepository()
                        .getRoom(roomId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnNext { room ->
                            val status = room.getStatus()
                            val isLive = status == AmityRoomStatus.LIVE
                            val isRecorded = status == AmityRoomStatus.RECORDED
                            val canJoin = isLive || isRecorded

                            val statusLabel = when {
                                isLive -> "LIVE"
                                isRecorded -> "PAST EVENT"
                                else -> "UPCOMING"
                            }

                            Timber.d(
                                "RoomPoc RAW room status=%s roomId=%s postId=%s creatorId=%s participants=%s",
                                status,
                                room.getRoomId(),
                                roomPost.getPostId(),
                                room.getCreatorId(),
                                room.getParticipants().joinToString { "${it.type}:${it.userId}" }
                            )

                            Timber.d(
                                "RoomPoc DERIVED isLive=%s isRecorded=%s canJoin=%s statusLabel=%s",
                                isLive,
                                isRecorded,
                                canJoin,
                                statusLabel
                            )

                            resolveCreator(room, roomPost) { creator ->
                                val cohost = resolveCoHost(room)

                                val currentUserId = AmityCoreClient.getUserId()
                                val creatorId = creator?.getUserId() ?: roomPost.getCreatorId()
                                val isCurrentUserCreator = creatorId == currentUserId

                                val statusDate =
                                    roomPost.getCreatedAt()?.toString("MMM d 'at' h:mm a") ?: ""

                                Timber.d(
                                    "RoomPoc FINAL creator=%s cohost=%s isCurrentUserCreator=%s shouldShowReplay=%s statusLabel=%s statusDate=%s",
                                    creator?.getDisplayName(),
                                    cohost?.getDisplayName(),
                                    isCurrentUserCreator,
                                    isRecorded,
                                    statusLabel,
                                    statusDate
                                )

                                onResult(
                                    LivestreamRoomPocUiState(
                                        livestreamPost = roomPost,
                                        room = room,
                                        creatorDisplayName = creator?.getDisplayName() ?: "",
                                        creatorAvatarUrl = creator?.getAvatar()?.getUrl(AmityImage.Size.MEDIUM) ?: "",
                                        cohostDisplayName = cohost?.getDisplayName() ?: "",
                                        cohostAvatarUrl = cohost?.getAvatar()?.getUrl(AmityImage.Size.MEDIUM) ?: "",
                                        isCurrentUserCreator = isCurrentUserCreator,
                                        hasLivestream = true,
                                        canJoin = canJoin,
                                        canCreate = false,
                                        shouldShowReplay = isRecorded,
                                        statusLabel = statusLabel,
                                        statusDate = statusDate
                                    )
                                )
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
                        .subscribe()
                        .also(::addDisposable)
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
        room: AmityRoom,
        post: AmityPost,
        onResolved: (AmityUser?) -> Unit
    ) {
        val roomCreator = room.getCreator()
        if (roomCreator != null) {
            onResolved(roomCreator)
            return
        }

        val creatorId = post.getCreatorId()
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
            .let(::addDisposable)
    }

    private fun resolveCoHost(room: AmityRoom): AmityUser? {
        return room.getParticipants()
            .firstOrNull { participant ->
                participant.type == AmityRoom.ParticipantType.CoHost
            }
            ?.user
    }

    override fun onCleared() {
        roomDisposableRef?.dispose()
        roomDisposableRef = null
        super.onCleared()
    }
}