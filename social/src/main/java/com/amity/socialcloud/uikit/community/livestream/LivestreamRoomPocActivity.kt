package com.amity.socialcloud.uikit.community.livestream

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.amity.socialcloud.uikit.community.R

class LivestreamRoomPocActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_livestream_room_poc)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.livestreamRoomContainer, LivestreamRoomPocFragment())
                .commit()
        }
    }
}