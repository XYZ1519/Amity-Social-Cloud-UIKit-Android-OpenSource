package com.amity.socialcloud.uikit.community.compose.post.detail.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amity.socialcloud.sdk.api.social.AmitySocialClient
import com.amity.socialcloud.sdk.api.social.event.AmityEventRepository
import com.amity.socialcloud.sdk.helper.core.coroutines.asFlow
import com.amity.socialcloud.sdk.helper.core.hashtag.AmityHashtagMetadataGetter
import com.amity.socialcloud.sdk.helper.core.mention.AmityMentionMetadataGetter
import com.amity.socialcloud.sdk.model.social.event.AmityEvent
import com.amity.socialcloud.sdk.model.social.event.AmityEventStatus
import com.amity.socialcloud.sdk.model.social.post.AmityPost
import com.amity.socialcloud.uikit.common.ui.elements.AmityExpandableText
import com.amity.socialcloud.uikit.common.ui.scope.AmityComposeComponentScope
import com.amity.socialcloud.uikit.common.ui.theme.AmityTheme
import com.amity.socialcloud.uikit.common.utils.clickableWithoutRipple
import com.amity.socialcloud.uikit.community.compose.R
import com.amity.socialcloud.uikit.community.compose.community.profile.component.EventCardItem
import com.amity.socialcloud.uikit.community.compose.community.profile.component.EventCardStyle
import com.amity.socialcloud.uikit.community.compose.localization.amitySocialString
import com.amity.socialcloud.uikit.community.compose.post.detail.components.AmityPostContentComponentStyle
import com.amity.socialcloud.uikit.community.compose.ui.shimmer.AmityEventCardShimmer
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.catch

/**
 * Renders an event post (dataType "event") in the feed / post detail.
 *
 * The parent post holds the author's caption (title + text); the child post carries the event
 * reference, resolved to an [AmityEvent] and shown as an [EventCardItem]. Tapping the card raises
 * [onEventClick] so the host component owns the navigation; tapping the caption follows the usual
 * post tap. Handles the loading (shimmer) and deleted/unavailable states — the post survives its
 * event, so a missing event never hides the post.
 */
@Composable
fun AmityPostEventElement(
    modifier: Modifier = Modifier,
    componentScope: AmityComposeComponentScope? = null,
    post: AmityPost,
    style: AmityPostContentComponentStyle,
    boldedText: String? = null,
    onClick: () -> Unit,
    // Raised with the resolved event's id when the event card is tapped. Navigation lives on the
    // host component's behavior class, not in here.
    onEventClick: (String) -> Unit = {},
    onMentionedUserClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
) {

    // The event reference lives on the child post; bail if this isn't actually an event post.
    val eventData = post.getChildren()
        .firstOrNull { it.getData() is AmityPost.Data.EVENT }
        ?.getData() as? AmityPost.Data.EVENT ?: return
    val eventId = eventData.getEventId()

    // The caption is the author's own words. The parent post's data is TEXT (title + text);
    // fall back to the event child's data for the all-in-one "event" parent shape.
    val title = (post.getData() as? AmityPost.Data.TEXT)?.getTitle()
        ?: eventData.getTitle()
    val captionText = (post.getData() as? AmityPost.Data.TEXT)?.getText()
        ?: eventData.getText()

    val mentionGetter = remember(post.getPostId(), post.getUpdatedAt()) {
        AmityMentionMetadataGetter(post.getMetadata() ?: JsonObject())
    }
    val hashtagGetter = remember(post.getPostId(), post.getUpdatedAt()) {
        AmityHashtagMetadataGetter(post.getMetadata() ?: JsonObject())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = AmityTheme.typography.titleBold.copy(
                    fontSize = 17.sp,
                    textAlign = TextAlign.Start
                ),
                color = AmityTheme.colors.base,
            )
            if (captionText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (captionText.isNotEmpty()) {
            AmityExpandableText(
                modifier = Modifier,
                text = captionText,
                mentionGetter = mentionGetter,
                mentionees = post.getMentionees(),
                hashtagGetter = hashtagGetter,
                style = AmityTheme.typography.body,
                boldWhenMatches = boldedText?.let { listOf(it) } ?: emptyList(),
                intialExpand = style == AmityPostContentComponentStyle.DETAIL,
                onClick = onClick,
                onMentionedUserClick = onMentionedUserClick,
                onHashtagClick = onHashtagClick,
            )
        }

        if (title.isNotEmpty() || captionText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Resolve the event reactively (mirrors the poll element): getEvent() emits the enriched
        // cached event and subsequent updates. Start on the shimmer; a deleted / unavailable event
        // errors the stream, which flips to the unavailable card. The post, its comments and
        // reactions survive regardless.
        //
        // A CANCELLED event is semantically the same as a deleted one (spec REQ-126) and still
        // resolves normally, so normalise it here rather than letting it render as a live card.
        var uiState by remember(eventId) { mutableStateOf<EventPostUiState>(EventPostUiState.Loading) }
        LaunchedEffect(eventId) {
            AmitySocialClient.newEventRepository()
                .getEvent(eventId)
                .asFlow()
                .catch { if (uiState !is EventPostUiState.Loaded) uiState = EventPostUiState.Unavailable }
                .collect { event ->
                    uiState = if (event.getStatus() == AmityEventStatus.CANCELLED) {
                        EventPostUiState.Unavailable
                    } else {
                        EventPostUiState.Loaded(event)
                    }
                }
        }
        when (val state = uiState) {
            EventPostUiState.Loading -> AmityEventCardShimmer(style = EventCardStyle.Large)
            EventPostUiState.Unavailable -> AmityEventPostUnavailableCard()
            is EventPostUiState.Loaded -> EventCardItem(
                event = state.event,
                style = EventCardStyle.Large,
                onClick = { onEventClick(eventId) },
            )
        }
    }
}

private sealed interface EventPostUiState {
    data object Loading : EventPostUiState
    data object Unavailable : EventPostUiState
    data class Loaded(val event: AmityEvent) : EventPostUiState
}

/**
 * Card shown when the attached event is no longer available (deleted, absent, or cancelled — all
 * three normalise to this state per spec REQ-126). Mirrors the [EventCardItem] skeleton — a grey
 * thumbnail area with a centered image-off icon, and a details section with the message
 * left-aligned — so the layout matches the design and doesn't jump.
 *
 * Non-interactive by design: only a resolved card navigates to the event.
 */
@Composable
internal fun AmityEventPostUnavailableCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(1.dp, AmityTheme.colors.divider),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(193.dp)
                .background(AmityTheme.colors.baseShade4),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.amity_ic_event_unavailable),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
        // Details
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(AmityTheme.colors.background)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = amitySocialString("amity_social_label_this_event_is_no_longer_available"),
                style = AmityTheme.typography.bodyBold.copy(fontSize = 15.sp),
                color = AmityTheme.colors.baseShade2,
            )
        }
    }
}
