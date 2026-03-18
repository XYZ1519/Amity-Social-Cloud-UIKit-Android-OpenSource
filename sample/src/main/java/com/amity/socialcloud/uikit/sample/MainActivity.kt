package com.amity.socialcloud.uikit.sample

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.WindowCompat
import com.amity.socialcloud.sdk.api.core.AmityCoreClient
import com.amity.socialcloud.sdk.core.session.AccessTokenRenewal
import com.amity.socialcloud.sdk.model.core.session.SessionHandler
import com.amity.socialcloud.uikit.common.common.showSnackBar
import com.amity.socialcloud.uikit.community.livestream.LivestreamRoomPocActivity
import com.amity.socialcloud.uikit.sample.databinding.AmityActivityMainBinding
import com.amity.socialcloud.uikit.sample.env.SamplePreferences
import com.ekoapp.rxlifecycle.extension.untilLifecycleEnd
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import com.trello.rxlifecycle4.components.support.RxAppCompatActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MainActivity : RxAppCompatActivity() {

    enum class RegionConfig(val httpUrl: String, val mqttBroker: String) {
        EU("https://apix.eu.amity.co/", "ssq.eu.amity.co"),
        US("https://apix.us.amity.co/", "ssq.us.amity.co"),
        SG("https://apix.sg.amity.co/", "ssq.sg.amity.co")
    }

    private val binding: AmityActivityMainBinding by lazy {
        AmityActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        setContentView(binding.root)

        setupEnvironmentDropdown()
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        binding.etApiKey.setText(SamplePreferences.getApiKey().get())
        binding.etSecureVisitorUrl.setText(
            "https://472sfz2bt3cddangdvv5nlyjjq0pnshz.lambda-url.ap-southeast-1.on.aws"
        )

        binding.etHttpUrl.visibility = View.GONE
        binding.etMqttBroker.visibility = View.GONE
        binding.btnEnv.visibility = View.GONE
        binding.btnVisitorLogin.visibility = View.GONE

        // Hide the parent TextInputLayout from the new XML
        binding.secureVisitorUrlLayout.visibility = View.GONE

        val savedUserId = AmityCoreClient.getUserId()
        if (savedUserId.isNotEmpty()) {
            binding.etUserId.setText(savedUserId)

            if (binding.etUserName.text.isNullOrBlank()) {
                binding.etUserName.setText(savedUserId)
            }
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            if (!saveEnvironmentFromSelection(showSuccessMessage = false)) {
                return@setOnClickListener
            }

            val userId = binding.etUserId.text?.toString()?.trim().orEmpty()
            val displayNameInput = binding.etUserName.text?.toString()?.trim().orEmpty()

            if (userId.isEmpty()) {
                findViewById<View>(android.R.id.content).showSnackBar(
                    "Enter userId",
                    Snackbar.LENGTH_SHORT
                )
                return@setOnClickListener
            }

            val finalDisplayName = if (displayNameInput.isNotEmpty()) {
                displayNameInput
            } else {
                userId
            }

            registerDevice(
                userId = userId,
                displayName = finalDisplayName
            )
        }

        binding.btnVisitorLogin.setOnClickListener {
            if (!saveEnvironmentFromSelection(showSuccessMessage = false)) {
                return@setOnClickListener
            }

            val displayNameInput = binding.etUserName.text?.toString()?.trim().orEmpty()
            val secureVisitorUrl = binding.etSecureVisitorUrl.text?.toString()?.trim().orEmpty()

            if (secureVisitorUrl.isNotEmpty()) {
                val retrofitInstance =
                    SampleRetrofitProvider.getInstance(SamplePreferences.getHttpUrl().get())
                val api = retrofitInstance.create(SecureService::class.java)
                val visitorDeviceId = AmityCoreClient.getVisitorDeviceId()
                val expiresAt = DateTime.now().plusDays(30).toDateTime(DateTimeZone.UTC)

                api.getAuthSignature(
                    url = secureVisitorUrl,
                    deviceId = visitorDeviceId,
                    authSignatureExpiresAt = expiresAt
                ).enqueue(object : Callback<JsonObject> {
                    override fun onResponse(
                        call: Call<JsonObject>,
                        response: Response<JsonObject>
                    ) {
                        val json: JsonObject? = response.body()
                        val authSignature = try {
                            json?.get("signature")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        }

                        registerDevice(
                            userId = null,
                            displayName = displayNameInput,
                            authSignature = authSignature,
                            authSignatureExpiresAt = expiresAt
                        )
                    }

                    override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                        Timber.e(t, "API call failed")
                        findViewById<View>(android.R.id.content).showSnackBar(
                            "Failed to get visitor auth signature",
                            Snackbar.LENGTH_SHORT
                        )
                    }
                })
            } else {
                registerDevice(
                    userId = null,
                    displayName = displayNameInput
                )
            }
        }
    }

    private fun setupEnvironmentDropdown() {
        val regions = listOf("EU", "US", "SG")
        val adapter = ArrayAdapter(
            this,
            R.layout.item_spinner_selected,
            regions
        )
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spEnvironment.adapter = adapter

        val savedHttpUrl = SamplePreferences.getHttpUrl().get()
        val selectedIndex = when (savedHttpUrl) {
            RegionConfig.US.httpUrl -> 1
            RegionConfig.SG.httpUrl -> 2
            else -> 0
        }
        binding.spEnvironment.setSelection(selectedIndex)
    }

    private fun saveEnvironmentFromSelection(showSuccessMessage: Boolean = true): Boolean {
        val apiKey = binding.etApiKey.text?.toString()?.trim().orEmpty()
        val selectedRegion = binding.spEnvironment.selectedItem?.toString() ?: "EU"

        if (apiKey.isEmpty()) {
            findViewById<View>(android.R.id.content).showSnackBar(
                "API Key is required",
                Snackbar.LENGTH_SHORT
            )
            return false
        }

        val config = when (selectedRegion) {
            "US" -> RegionConfig.US
            "SG" -> RegionConfig.SG
            else -> RegionConfig.EU
        }

        SamplePreferences.getApiKey().set(apiKey)
        SamplePreferences.getHttpUrl().set(config.httpUrl)
        SamplePreferences.getMqttBroker().set(config.mqttBroker)

        SampleRetrofitProvider.reset()

        if (showSuccessMessage) {
            findViewById<View>(android.R.id.content).showSnackBar(
                "Environment updated: $selectedRegion",
                Snackbar.LENGTH_SHORT
            )
        }

        return true
    }

    private fun registerDevice(
        userId: String?,
        displayName: String? = "",
        authSignature: String? = null,
        authSignatureExpiresAt: DateTime? = null,
    ) {
        Single.just(true)
            .delay(200, TimeUnit.MILLISECONDS)
            .flatMapCompletable {
                if (userId.isNullOrEmpty()) {
                    AmityCoreClient.loginAsVisitor(object : SessionHandler {
                        override fun sessionWillRenewAccessToken(renewal: AccessTokenRenewal) {
                            renewal.renew()
                        }
                    }).apply {
                        if (!displayName.isNullOrEmpty()) {
                            displayName(displayName)
                        }
                        if (!authSignature.isNullOrEmpty() && authSignatureExpiresAt != null) {
                            authSignature(authSignature)
                            authSignatureExpiresAt(authSignatureExpiresAt)
                        }
                    }
                        .build()
                        .submit()
                } else {
                    AmityCoreClient.login(userId, object : SessionHandler {
                        override fun sessionWillRenewAccessToken(renewal: AccessTokenRenewal) {
                            renewal.renew()
                        }
                    }).apply {
                        if (!displayName.isNullOrEmpty()) {
                            displayName(displayName)
                        }
                    }
                        .build()
                        .submit()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                registerForPushNotifications()
                val intent = Intent(this, LivestreamRoomPocActivity::class.java)
                startActivity(intent)
            }
            .doOnError {
                findViewById<View>(android.R.id.content).showSnackBar(
                    "Could not register user ${it.message}",
                    Snackbar.LENGTH_SHORT
                )
            }
            .untilLifecycleEnd(this)
            .subscribe()
    }

    private fun registerForPushNotifications() {
        AmityCoreClient.registerPushNotification()
            .subscribeOn(Schedulers.io())
            .doOnComplete {
                Timber.d("registerForPushNotifications: success for userId ${AmityCoreClient.getUserId()}")
            }
            .doOnError {
                Timber.e(it, "registerForPushNotifications failed")
            }
            .subscribe()
    }

    interface SecureService {
        @GET
        fun getAuthSignature(
            @Url url: String,
            @Query("deviceId") deviceId: String,
            @Query("authSignatureExpiresAt") authSignatureExpiresAt: DateTime
        ): Call<JsonObject>
    }
}