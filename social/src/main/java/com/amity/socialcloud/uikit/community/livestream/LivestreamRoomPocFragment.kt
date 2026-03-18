package com.amity.socialcloud.uikit.community.livestream

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.model.core.file.AmityImage
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.sdk.model.video.room.AmityRoomStatus
import com.amity.socialcloud.sdk.model.video.stream.AmityStream
import com.amity.socialcloud.uikit.community.R
import com.amity.socialcloud.uikit.community.compose.livestream.room.create.AmityCreateRoomPageActivity
import com.amity.socialcloud.uikit.community.compose.livestream.room.view.AmityRoomPlayerPageActivity
import com.amity.socialcloud.uikit.community.newsfeed.activity.AmityLivestreamVideoPlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LivestreamRoomPocFragment : Fragment() {

    private val communityId = "69baf27c2c3a9394ec9b76bc"
    private val pocViewModel: LivestreamRoomPocViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_livestream_room_poc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCommunityData(view)
        observeLivestreamState(view)
    }

    private fun loadCommunityData(view: View) {
        val headerBackground = view.findViewById<ImageView>(R.id.ivHeaderBackground)
        val titleView = view.findViewById<TextView>(R.id.tvTitle)
        val descriptionView = view.findViewById<TextView>(R.id.tvDescription)
        val thumb2 = view.findViewById<ImageView>(R.id.ivThumb2)

        AmitySocialClient.newCommunityRepository()
            .getCommunity(communityId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { community ->
                val avatarUrl = community.getAvatar()?.getUrl(AmityImage.Size.LARGE) ?: ""

                Glide.with(this)
                    .load(avatarUrl)
                    .centerCrop()
                    .into(headerBackground)

                Glide.with(this)
                    .load(R.drawable.avatar)
                    .circleCrop()
                    .into(thumb2)

                titleView.text = community.getDisplayName().trim()

                val description = community.getDescription().trim()
                if (description.isNotEmpty()) descriptionView.text = description
            }
            .doOnError { Timber.e(it) }
            .subscribe()
    }

    private fun ensureJoinedThen(onJoined: () -> Unit) {
        AmitySocialClient.newCommunityRepository()
            .joinCommunity(communityId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ onJoined() }, { error ->
                Timber.w(error, "Join community failed, attempting to continue anyway")
                onJoined()
            })
    }

    private fun loadPlaceholderAvatar(target: ImageView) {
        Glide.with(this).load(R.drawable.avatar).circleCrop().into(target)
    }

    private fun observeLivestreamState(view: View) {
        val statusView = view.findViewById<TextView>(R.id.tvStatus)
        val dateView = view.findViewById<TextView>(R.id.tvDate)
        val joinButton = view.findViewById<MaterialButton>(R.id.btnJoinLivestream)
        val createButton = view.findViewById<MaterialButton>(R.id.btnCreateLivestream)
        val thumb1 = view.findViewById<ImageView>(R.id.ivThumb1)
        val thumb2 = view.findViewById<ImageView>(R.id.ivThumb2)

        loadPlaceholderAvatar(thumb1)
        loadPlaceholderAvatar(thumb2)

        pocViewModel.observeLivestreamPost(communityId) { state ->

            Log.e(
                "POC_UI",
                "state: has=${state.hasLivestream} label=${state.statusLabel} canJoin=${state.canJoin} canCreate=${state.canCreate} roomStatus=${state.room?.getStatus()} streamStatus=${state.stream?.getStatus()}"
            )

            joinButton.isEnabled = state.canJoin
            createButton.isEnabled = state.canCreate
            createButton.alpha = if (state.canCreate) 1f else 0.55f

            statusView.text = state.statusLabel
            dateView.text = state.statusDate
            dateView.visibility = if (state.statusDate.isBlank()) View.GONE else View.VISIBLE

            when (state.statusLabel) {
                "LIVE" -> {
                    statusView.setBackgroundColor(Color.parseColor("#E85C5C"))
                    statusView.setTextColor(Color.WHITE)
                }
                "PAST EVENT" -> {
                    statusView.setBackgroundColor(Color.parseColor("#F9E3E3"))
                    statusView.setTextColor(Color.parseColor("#CF6D6D"))
                }
                else -> {
                    statusView.setBackgroundColor(Color.parseColor("#E9ECEF"))
                    statusView.setTextColor(Color.parseColor("#6C757D"))
                }
            }

            loadPlaceholderAvatar(thumb2)

            if (state.hasLivestream) {
                if (state.creatorAvatarUrl.isNotBlank()) {
                    Glide.with(this)
                        .load(state.creatorAvatarUrl)
                        .placeholder(R.drawable.avatar)
                        .error(R.drawable.avatar)
                        .fallback(R.drawable.avatar)
                        .circleCrop()
                        .into(thumb1)
                } else {
                    loadPlaceholderAvatar(thumb1)
                }

                val isReplayable =
                    state.stream?.getStatus() == AmityStream.Status.RECORDED ||
                            state.stream?.getStatus() == AmityStream.Status.ENDED ||
                            state.room?.getStatus() == AmityRoomStatus.RECORDED ||
                            state.room?.getStatus() == AmityRoomStatus.ENDED

                val isLive =
                    state.stream?.getStatus() == AmityStream.Status.LIVE ||
                            state.room?.getStatus() == AmityRoomStatus.LIVE

                when {
                    isReplayable -> {
                        joinButton.text = "REPLAY"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    isLive -> {
                        joinButton.text = "Join Room"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    else -> {
                        joinButton.text = "Join Room"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                }

                joinButton.setOnClickListener {
                    val post = state.livestreamPost ?: return@setOnClickListener

                    // ROOM: LIVE/RECORDED/ENDED -> open room player
                    state.room?.let { room ->
                        when (room.getStatus()) {
                            AmityRoomStatus.LIVE,
                            AmityRoomStatus.RECORDED,
                            AmityRoomStatus.ENDED -> {
                                ensureJoinedThen {
                                    startActivity(
                                        AmityRoomPlayerPageActivity.newIntent(
                                            context = requireContext(),
                                            post = post,
                                            fromInvitation = false
                                        )
                                    )
                                }
                            }
                            else -> Unit
                        }
                        return@setOnClickListener
                    }

                    // LIVE_STREAM: ENDED/RECORDED -> replay; LIVE -> room player
                    state.stream?.let { stream ->
                        when (stream.getStatus()) {
                            AmityStream.Status.RECORDED,
                            AmityStream.Status.ENDED -> {
                                ensureJoinedThen {
                                    startActivity(
                                        AmityLivestreamVideoPlayerActivity.newIntent(
                                            context = requireContext(),
                                            streamId = stream.getStreamId()
                                        )
                                    )
                                }
                            }
                            AmityStream.Status.LIVE -> {
                                ensureJoinedThen {
                                    startActivity(
                                        AmityRoomPlayerPageActivity.newIntent(
                                            context = requireContext(),
                                            post = post,
                                            fromInvitation = false
                                        )
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            } else {
                loadPlaceholderAvatar(thumb1)
                joinButton.text = "Join Room"
                joinButton.setBackgroundColor(Color.parseColor("#B8C7F0"))
                joinButton.setTextColor(Color.WHITE)
                joinButton.setOnClickListener(null)
            }

            createButton.setOnClickListener {
                ensureJoinedThen {
                    startActivity(
                        AmityCreateRoomPageActivity.newIntent(
                            context = requireContext(),
                            targetId = communityId,
                            targetType = AmityPost.TargetType.COMMUNITY,
                            community = null,
                            postId = null
                        )
                    )
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }
}