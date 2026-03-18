package com.amity.socialcloud.uikit.community.livestream

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.amity.socialcloud.sdk.model.video.stream.AmityStream
import com.amity.socialcloud.uikit.community.R
import com.amity.socialcloud.uikit.community.compose.livestream.create.AmityCreateLivestreamPageActivity
import com.amity.socialcloud.uikit.community.newsfeed.activity.AmityLivestreamVideoPlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LivestreamRoomPocFragment : Fragment() {

    private val communityId = "699ef435f96492bdba2c8345"

    private val pocViewModel: LivestreamRoomPocViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_livestream_room_poc,
            container,
            false
        )
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

                // Second speaker should always show the fixed placeholder avatar
                Glide.with(this)
                    .load(R.drawable.avatar)
                    .circleCrop()
                    .into(thumb2)

                titleView.text = community.getDisplayName().trim()

                val description = community.getDescription().trim()
                if (description.isNotEmpty()) {
                    descriptionView.text = description
                }
            }
            .doOnError {
                Timber.e(it)
            }
            .subscribe()
    }

    private fun ensureJoinedThen(
        onJoined: () -> Unit
    ) {
        AmitySocialClient.newCommunityRepository()
            .joinCommunity(communityId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onJoined()
            }, { error ->
                Timber.w(error, "Join community failed, attempting to continue anyway")
                onJoined()
            })
    }

    private fun loadPlaceholderAvatar(target: ImageView) {
        Glide.with(this)
            .load(R.drawable.avatar)
            .circleCrop()
            .into(target)
    }

    private fun observeLivestreamState(view: View) {
        val statusView = view.findViewById<TextView>(R.id.tvStatus)
        val dateView = view.findViewById<TextView>(R.id.tvDate)
        val joinButton = view.findViewById<MaterialButton>(R.id.btnJoinLivestream)
        val createButton = view.findViewById<MaterialButton>(R.id.btnCreateLivestream)
        val thumb1 = view.findViewById<ImageView>(R.id.ivThumb1)
        val thumb2 = view.findViewById<ImageView>(R.id.ivThumb2)

        // Ensure both avatars have a default placeholder immediately
        loadPlaceholderAvatar(thumb1)
        loadPlaceholderAvatar(thumb2)

        pocViewModel.observeLivestreamPost(communityId) { state ->
            joinButton.isEnabled = state.canJoin
            createButton.isEnabled = state.canCreate

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

            // Second speaker always remains the fixed placeholder avatar
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

                when {
                    state.shouldShowReplay -> {
                        joinButton.text = "REPLAY"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    state.stream?.getStatus() == AmityStream.Status.LIVE -> {
                        joinButton.text = "Join Livestream"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    else -> {
                        joinButton.text = "Join Livestream"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                }

                joinButton.setOnClickListener {
                    state.stream?.let { stream ->
                        if (stream.getStatus() == AmityStream.Status.LIVE ||
                            stream.getStatus() == AmityStream.Status.RECORDED ||
                            stream.getStatus() == AmityStream.Status.ENDED
                        ) {
                            ensureJoinedThen {
                                val intent = AmityLivestreamVideoPlayerActivity.newIntent(
                                    context = requireContext(),
                                    streamId = stream.getStreamId()
                                )
                                startActivity(intent)
                            }
                        }
                    }
                }
            } else {
                // No livestream -> first avatar should also be the placeholder
                loadPlaceholderAvatar(thumb1)

                joinButton.text = "Join Livestream"
                joinButton.setBackgroundColor(Color.parseColor("#B8C7F0"))
                joinButton.setTextColor(Color.WHITE)
                joinButton.setOnClickListener(null)
            }

            createButton.alpha = if (state.canCreate) 1f else 0.55f

            createButton.setOnClickListener {
                ensureJoinedThen {
                    val intent = Intent(
                        requireContext(),
                        AmityCreateLivestreamPageActivity::class.java
                    ).apply {
                        putExtra(
                            AmityCreateLivestreamPageActivity.EXTRA_PARAM_TARGET_ID,
                            communityId
                        )
                        putExtra(
                            AmityCreateLivestreamPageActivity.EXTRA_PARAM_TARGET_TYPE,
                            AmityPost.TargetType.COMMUNITY
                        )
                    }
                    startActivity(intent)
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }
}