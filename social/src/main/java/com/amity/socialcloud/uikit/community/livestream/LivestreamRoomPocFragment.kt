package com.amity.socialcloud.uikit.community.livestream

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.model.core.file.AmityImage
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.sdk.model.video.room.AmityRoomStatus
import com.amity.socialcloud.uikit.community.R
import com.amity.socialcloud.uikit.community.compose.livestream.room.create.AmityCreateRoomPageActivity
import com.amity.socialcloud.uikit.community.compose.livestream.room.view.AmityRoomPlayerPageActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LivestreamRoomPocFragment : Fragment() {

    private val communityId = "699ef435f96492bdba2c8345"

    private val pocViewModel: LivestreamRoomPocViewModel by viewModels()

    private var roomStateDisposable: Disposable? = null

    private val createRoomLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            view?.let { currentView ->
                observeLivestreamState(currentView)
            }
        }

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

    override fun onResume() {
        super.onResume()
        view?.let { observeLivestreamState(it) }
    }

    override fun onDestroyView() {
        roomStateDisposable?.dispose()
        roomStateDisposable = null
        super.onDestroyView()
    }

    private fun loadCommunityData(view: View) {
        val headerBackground = view.findViewById<ImageView>(R.id.ivHeaderBackground)
        val titleView = view.findViewById<TextView>(R.id.tvTitle)
        val descriptionView = view.findViewById<TextView>(R.id.tvDescription)

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

    private fun observeLivestreamState(view: View) {
        val statusView = view.findViewById<TextView>(R.id.tvStatus)
        val dateView = view.findViewById<TextView>(R.id.tvDate)
        val joinButton = view.findViewById<MaterialButton>(R.id.btnJoinLivestream)
        val createButton = view.findViewById<MaterialButton>(R.id.btnCreateLivestream)
        val thumb1 = view.findViewById<ImageView>(R.id.ivThumb1)
        val thumb2 = view.findViewById<ImageView>(R.id.ivThumb2)

        roomStateDisposable?.dispose()
        roomStateDisposable = pocViewModel.observeLivestreamPost(communityId) { state ->
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

            if (state.hasLivestream) {
                if (state.creatorAvatarUrl.isNotBlank()) {
                    Glide.with(this)
                        .load(state.creatorAvatarUrl)
                        .circleCrop()
                        .into(thumb1)
                } else {
                    thumb1.setImageDrawable(null)
                }

                if (state.cohostAvatarUrl.isNotBlank()) {
                    Glide.with(this)
                        .load(state.cohostAvatarUrl)
                        .circleCrop()
                        .into(thumb2)
                } else {
                    thumb2.setImageDrawable(null)
                }

                when {
                    state.shouldShowReplay -> {
                        joinButton.text = "▶  REPLAY"
                        joinButton.setBackgroundColor(Color.parseColor("#E45B62"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    state.room?.getStatus() == AmityRoomStatus.LIVE -> {
                        joinButton.text = "Join Livestream"
                        joinButton.setBackgroundColor(Color.parseColor("#1E5BE0"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                    else -> {
                        joinButton.text = "Join Livestream"
                        joinButton.setBackgroundColor(Color.parseColor("#B8C7F0"))
                        joinButton.setTextColor(Color.WHITE)
                    }
                }

                joinButton.setOnClickListener {
                    val post = state.livestreamPost ?: return@setOnClickListener
                    val room = state.room ?: return@setOnClickListener

                    if (room.getStatus() == AmityRoomStatus.LIVE ||
                        room.getStatus() == AmityRoomStatus.RECORDED
                    ) {
                        ensureJoinedThen {
                            val intent = AmityRoomPlayerPageActivity.newIntent(
                                context = requireContext(),
                                post = post
                            )
                            startActivity(intent)
                        }
                    }
                }
            } else {
                joinButton.text = "Join Livestream"
                joinButton.setBackgroundColor(Color.parseColor("#B8C7F0"))
                joinButton.setTextColor(Color.WHITE)
                joinButton.setOnClickListener(null)

                thumb1.setImageDrawable(null)
                thumb2.setImageDrawable(null)
            }

            createButton.alpha = if (state.canCreate) 1f else 0.55f

            createButton.setOnClickListener {
                ensureJoinedThen {
                    val intent = Intent(
                        requireContext(),
                        AmityCreateRoomPageActivity::class.java
                    ).apply {
                        putExtra(
                            AmityCreateRoomPageActivity.EXTRA_PARAM_TARGET_ID,
                            communityId
                        )
                        putExtra(
                            AmityCreateRoomPageActivity.EXTRA_PARAM_TARGET_TYPE,
                            AmityPost.TargetType.COMMUNITY
                        )
                    }
                    createRoomLauncher.launch(intent)
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }
}