package com.amity.socialcloud.uikit.community.compose.common

import androidx.compose.runtime.Composable
import com.amity.socialcloud.uikit.common.ui.elements.AmityAlertDialog
import com.amity.socialcloud.uikit.common.ui.theme.AmityTheme
import com.amity.socialcloud.uikit.community.compose.localization.amitySocialString

@Composable
fun DeclineInvitationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (showDialog) {
        AmityAlertDialog(
            dialogTitle = amitySocialString("amity_social_modal_community_invitation_reject_dialog_title"),
            dialogText = amitySocialString("amity_social_modal_community_invitation_reject_dialog_subtitle"),
            confirmText = amitySocialString("amity_social_button_decline"),
            dismissText = amitySocialString("amity_social_button_cancel"),
            confirmTextColor = AmityTheme.colors.alert,
            dismissTextColor = AmityTheme.colors.highlight,
            onConfirmation = onConfirm,
            onDismissRequest = onDismiss,
        )
    }
}

