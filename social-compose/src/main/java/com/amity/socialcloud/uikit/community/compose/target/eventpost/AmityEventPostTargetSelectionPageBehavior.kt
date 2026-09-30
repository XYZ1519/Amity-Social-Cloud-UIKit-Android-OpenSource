package com.amity.socialcloud.uikit.community.compose.target.eventpost

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.amity.socialcloud.sdk.model.social.community.AmityCommunity
import com.amity.socialcloud.sdk.model.social.event.AmityEvent
import com.amity.socialcloud.uikit.community.compose.post.composer.AmityPostComposerOptions
import com.amity.socialcloud.uikit.community.compose.post.composer.AmityPostComposerPageActivity
import com.amity.socialcloud.uikit.community.compose.post.composer.AmityPostTargetType

open class AmityEventPostTargetSelectionPageBehavior {

    /**
     * Opens the composer in event-post mode for the target the user picked. The event supplies the
     * attached card and the one-time title/body prefill; the event reference itself is immutable
     * from here on.
     */
    open fun goToPostComposerPage(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        event: AmityEvent,
        targetId: String?,
        targetType: AmityPostTargetType,
        community: AmityCommunity? = null,
    ) {
        val intent = AmityPostComposerPageActivity.newIntent(
            context = context,
            options = AmityPostComposerOptions.AmityPostComposerCreateOptions(
                targetId = targetId,
                targetType = targetType,
                community = community,
                attachedEventId = event.getEventId(),
                prefilledTitle = event.getTitle(),
                prefilledBody = event.getDescription(),
            )
        )
        launcher.launch(intent)
    }
}
