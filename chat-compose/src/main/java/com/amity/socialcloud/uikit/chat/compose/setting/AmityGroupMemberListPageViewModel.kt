package com.amity.socialcloud.uikit.chat.compose.setting

import androidx.paging.PagingData
import com.amity.socialcloud.sdk.api.chat.AmityChatClient
import com.amity.socialcloud.sdk.api.chat.member.query.AmityChannelMembership
import com.amity.socialcloud.sdk.api.core.AmityCoreClient
import com.amity.socialcloud.sdk.helper.core.coroutines.asFlow
import com.amity.socialcloud.sdk.model.chat.member.AmityChannelMember
import com.amity.socialcloud.sdk.model.core.permission.AmityPermission
import com.amity.socialcloud.uikit.common.base.AmityBaseViewModel
import com.amity.socialcloud.uikit.common.utils.AmityConstants
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.joda.time.Duration

class AmityGroupMemberListPageViewModel(
    private val channelId: String,
) : AmityBaseViewModel() {

    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword

    /**
     * Each member action is governed by its own channel permission, matching iOS. A single
     * moderator-ish flag was standing in for all of them, which meant anyone allowed to ADD members
     * also got promote, mute, ban and remove -- and anyone allowed to ban but not add got none.
     */
    private fun hasChannelPermission(permission: AmityPermission): Flow<Boolean> {
        return AmityCoreClient.hasPermission(permission)
            .atChannel(channelId)
            .check()
            .distinctUntilChanged()
            .subscribeOn(Schedulers.io())
            .asFlow()
            .catch { }
    }

    /** Gates the add-member button in the header. */
    fun canAddMember(): Flow<Boolean> = hasChannelPermission(AmityPermission.ADD_CHANNEL_USER)

    /** Gates promote and demote: both edit a member's channel role. */
    fun canPromote(): Flow<Boolean> = hasChannelPermission(AmityPermission.EDIT_CHANNEL_USER)

    /**
     * Gates mute and unmute, and whether the muted indicator is worth showing.
     *
     * MUTE_USER_INSIDE_CHANNEL, not MUTE_CHANNEL_USER: the latter is the older name and is not
     * what a channel role actually carries, so the check never matched and mute never appeared.
     */
    fun canMute(): Flow<Boolean> = hasChannelPermission(AmityPermission.MUTE_USER_INSIDE_CHANNEL)

    /** BAN_USER_FROM_CHANNEL, not BAN_CHANNEL_USER -- same reason as [canMute]. */
    fun canBan(): Flow<Boolean> = hasChannelPermission(AmityPermission.BAN_USER_FROM_CHANNEL)

    fun canRemove(): Flow<Boolean> = hasChannelPermission(AmityPermission.REMOVE_CHANNEL_USER)

    fun searchMembers(keyword: String = ""): Flow<PagingData<AmityChannelMember>> {
        return AmityChatClient.newChannelRepository()
            .membership(channelId)
            .searchMembers(keyword)
            .membershipFilter(listOf(AmityChannelMembership.MEMBER, AmityChannelMembership.MUTED))
            .build()
            .query()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .asFlow()
            .catch { }
    }

    fun getModerators(): Flow<List<AmityChannelMember>> {
        return AmityChatClient.newChannelRepository()
            .membership(channelId)
            .getMembersFromCache()
            .map { members ->
                members.filter { member ->
                    member.getRoles().contains(AmityConstants.CHANNEL_MODERATOR_ROLE)
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .asFlow()
            .catch { emit(emptyList()) }
    }

    fun searchModerators(keyword: String = ""): Flow<PagingData<AmityChannelMember>> {
        return AmityChatClient.newChannelRepository()
            .membership(channelId)
            .searchMembers(keyword)
            .membershipFilter(listOf(AmityChannelMembership.MEMBER, AmityChannelMembership.MUTED))
            .roles(listOf(AmityConstants.CHANNEL_MODERATOR_ROLE))
            .build()
            .query()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .asFlow()
            .catch { }
    }

    fun onSearchKeywordChanged(keyword: String) {
        _searchKeyword.value = keyword
    }

    fun getMyMembership(): Flow<AmityChannelMember> {
        return AmityChatClient.newChannelRepository()
            .membership(channelId)
            .getMyMembership()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .asFlow()
            .catch { }
    }

    fun promoteModerator(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .addRole(AmityConstants.CHANNEL_MODERATOR_ROLE, listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun demoteModerator(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .removeRole(AmityConstants.CHANNEL_MODERATOR_ROLE, listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun removeMember(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .membership(channelId)
            .removeMembers(listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun banMember(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .banMembers(listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun unbanMember(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .unbanMembers(listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun muteMember(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .muteMembers(Duration.millis(-1), listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun unmuteMember(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .moderation(channelId)
            .unmuteMembers(listOf(userId))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun reportUser(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityCoreClient.newUserRepository()
            .flagUser(userId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun unreportUser(
        userId: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityCoreClient.newUserRepository()
            .unflagUser(userId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { onSuccess() }
            .doOnError { onError(it) }
            .subscribe()
    }

    fun searchBannedMembers(keyword: String = ""): Flow<PagingData<AmityChannelMember>> {
        return AmityChatClient.newChannelRepository()
            .membership(channelId)
            .searchMembers(keyword)
            .membershipFilter(listOf(AmityChannelMembership.BANNED))
            .build()
            .query()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .asFlow()
            .catch { }
    }

    fun addMembers(
        userIds: List<String>,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        AmityChatClient.newChannelRepository()
            .membership(channelId)
            .addMembers(userIds)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onSuccess()
            },{
                onError(it)
            })
    }
}
