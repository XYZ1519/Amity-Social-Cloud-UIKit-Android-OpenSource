package com.amity.socialcloud.uikit.community.compose.target.eventpost

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.amity.socialcloud.sdk.model.social.event.AmityEvent

class AmityEventPostTargetSelectionPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val event = intent.getEventExtra() ?: run {
            // Nothing to post without an event — dismiss rather than render an empty picker.
            finish()
            return
        }

        setContent {
            AmityEventPostTargetSelectionPage(
                modifier = Modifier
                    .statusBarsPadding()
                    .systemBarsPadding(),
                event = event,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.getEventExtra(): AmityEvent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_PARAM_EVENT, AmityEvent::class.java)
        } else {
            getParcelableExtra(EXTRA_PARAM_EVENT)
        }
    }

    companion object {
        private const val EXTRA_PARAM_EVENT = "event"

        fun newIntent(
            context: Context,
            event: AmityEvent,
        ): Intent {
            return Intent(
                context,
                AmityEventPostTargetSelectionPageActivity::class.java
            ).apply {
                putExtra(EXTRA_PARAM_EVENT, event)
            }
        }
    }
}
