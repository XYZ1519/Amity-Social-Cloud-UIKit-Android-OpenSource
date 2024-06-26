package com.amity.socialcloud.uikit.community.compose.ui.components.mentions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.amity.socialcloud.sdk.model.core.user.AmityUser
import com.amity.socialcloud.sdk.model.social.community.AmityCommunity
import com.amity.socialcloud.uikit.community.compose.comment.elements.AmityCommentAvatarView
import com.amity.socialcloud.uikit.common.ui.theme.AmityTheme
import com.amity.socialcloud.uikit.common.utils.clickableWithoutRipple

@Composable
fun AmityMentionSuggestionView(
    modifier: Modifier = Modifier,
    community: AmityCommunity?,
    keyword: String,
    onClick: (AmityUser) -> Unit,
) {
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val viewModel =
        viewModel<AmityMentionViewModel>(viewModelStoreOwner = viewModelStoreOwner)

    val suggestions = remember(community?.getCommunityId(), keyword) {
        if (community == null || community.isPublic()) {
            viewModel.searchUsersMention(keyword)
        } else {
            viewModel.searchCommunityUsersMention(community.getCommunityId(), keyword)
        }
    }.collectAsLazyPagingItems()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(AmityTheme.colors.background)
            .requiredHeightIn(0.dp, 250.dp)
            .padding(horizontal = 12.dp)
    ) {
        items(
            count = suggestions.itemCount,
            key = { index -> suggestions.itemSnapshotList[index]?.getUserId() ?: index }
        ) {
            val user = suggestions[it] ?: return@items
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .fillMaxWidth()
                    .clickableWithoutRipple {
                        onClick(user)
                    },
            ) {
                AmityCommentAvatarView(
                    avatarUrl = user.getAvatar()?.getUrl(),
                    size = 40.dp,
                    modifier = modifier.padding(4.dp),
                )
                Text(
                    text = user.getDisplayName() ?: "",
                    overflow = TextOverflow.Ellipsis,
                    style = AmityTheme.typography.body
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AmityMentionSuggestionViewPreview() {
    AmityMentionSuggestionView(
        community = null,
        keyword = "keyword",
    ) {}
}