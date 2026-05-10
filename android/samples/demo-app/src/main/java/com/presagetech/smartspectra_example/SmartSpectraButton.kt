// SmartSpectraButton.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra_example.ui.OnboardingTutorialActivity
import com.presagetech.smartspectra_example.ui.SmartSpectraActivity
import com.presagetech.smartspectra_example.utils.PreferencesUtils
import timber.log.Timber
import kotlin.math.roundToInt


/**
 * A custom view that presents the SmartSpectra checkup button and info actions.
 *
 * When pressed the view launches [SmartSpectraActivity] to perform a
 * measurement. It also exposes a bottom sheet with links to the SDK
 * documentation and tutorials.
 */
class SmartSpectraButton(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val BASE_URL = "https://api.physiology.presagetech.com"
    private val linksMap = mapOf(
        R.id.txt_terms_of_service to "$BASE_URL/termsofservice",
        R.id.txt_privacy_policy to "$BASE_URL/privacypolicy",
        R.id.txt_instruction_of_use to "$BASE_URL/instructions ",
        R.id.txt_contact_us to "$BASE_URL/contact",
    )

    private var checkupButton: View
    private var infoButton: View

    private var onboardingTutorialHasBeenShown: Boolean
    private var agreedToTermsOfService: Boolean
    private var agreedToPrivacyPolicy: Boolean

    private val smartSpectraSdk: SmartSpectraSdk by lazy {
        SmartSpectraSdk.shared
    }

    init {
        onboardingTutorialHasBeenShown =
            PreferencesUtils.getBoolean(context, PreferencesUtils.ONBOARDING_TUTORIAL_KEY, false)

        agreedToTermsOfService =
            PreferencesUtils.getBoolean(context, PreferencesUtils.AGREED_TO_TERMS_OF_SERVICE_KEY, false)
        agreedToPrivacyPolicy =
            PreferencesUtils.getBoolean(context, PreferencesUtils.AGREED_TO_PRIVACY_POLICY_KEY, false)

        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ContextCompat.getDrawable(context, R.drawable.smart_spectra_button_background)
        LayoutInflater.from(context).inflate(R.layout.view_start_button, this, true)

        checkupButton = findViewById(R.id.button_checkup)
        setOnClickListener(this::onStartClicked)

        infoButton = findViewById(R.id.button_info)
        infoButton.setOnClickListener { infoBottomSheetDialog.show() }
    }

    private val infoBottomSheetDialog: BottomSheetDialog by lazy {
        val dialog = BottomSheetDialog(context).also {
            it.setContentView(R.layout.info_bottom_sheet_layout)
        }
        dialog.findViewById<AppCompatTextView>(R.id.txt_terms_of_service)?.setOnClickListener {
            dialog.dismiss()
            showTermsOfService(context)
        }
        dialog.findViewById<AppCompatTextView>(R.id.txt_privacy_policy)?.setOnClickListener {
            dialog.dismiss()
            showPrivacyPolicy(context)
        }
        dialog.findViewById<AppCompatTextView>(R.id.txt_instruction_of_use)?.setOnClickListener {
            dialog.dismiss()
            openInWebView(linksMap[R.id.txt_instruction_of_use].toString())
        }
        dialog.findViewById<AppCompatTextView>(R.id.txt_contact_us)?.setOnClickListener {
            dialog.dismiss()
            openInWebView(linksMap[R.id.txt_contact_us].toString())
        }
        dialog.findViewById<AppCompatTextView>(R.id.show_tutorial)?.setOnClickListener {
            dialog.dismiss()
            openOnboardingTutorial(context)
        }
        dialog
    }

    /**
     * Displays the onboarding tutorial the first time the button is pressed.
     *
     * After the tutorial completes, the provided [onComplete] callback will be
     * invoked. If the tutorial has already been shown the callback is invoked
     * immediately.
     */
    private fun showTutorialIfNecessary(onComplete: (() -> Unit)? = null) {
        if (!onboardingTutorialHasBeenShown) {
            openOnboardingTutorial(context) {
                showAgreementsIfNecessary(onComplete)
            }
        } else {
            onComplete?.invoke()
        }
    }

    /**
     * Shows the Terms of Service and Privacy Policy dialogs if the user has not
     * yet agreed to them. When both agreements are confirmed the
     * [onComplete] callback is executed.
     */
    private fun showAgreementsIfNecessary(onComplete: (() -> Unit)? = null) {
        if(!agreedToTermsOfService) {
            //show terms of service
            showTermsOfService(context) { agreed ->
                if (agreed) {
                    // call show agreement again to check for privacy policy
                    showAgreementsIfNecessary(onComplete)
                }
            }
        } else if(!agreedToPrivacyPolicy) {
            //show privacy policy
            showPrivacyPolicy(context) {
                onComplete?.invoke()
            }
        } else {
            onComplete?.invoke()
        }
    }

    /**
     * Handles clicks on the measurement button and launches
     * [SmartSpectraActivity] once prerequisites such as tutorial and
     * agreements have been completed.
     */
    private fun onStartClicked(view: View) {

        val postAgreementActions: () -> Unit = {
            if(agreedToTermsOfService && agreedToPrivacyPolicy) {
                val intent = Intent(context, SmartSpectraActivity::class.java)
                context.startActivity(intent)
            }
        }

        //show the tutorial when clicking checkup button in first launch
        if(!onboardingTutorialHasBeenShown) {
            showTutorialIfNecessary {
                postAgreementActions()
            }
        } else {
            // ensure continued agreement to the terms of service and privacy policy
            showAgreementsIfNecessary {
                postAgreementActions()
            }
        }
    }

    /**
     * Launches the onboarding tutorial activity and marks it as completed.
     */
    private fun openOnboardingTutorial(context: Context, callback: (() -> Unit)? = null) {
        val intent = Intent(context, OnboardingTutorialActivity::class.java)
        context.startActivity(intent)
        onboardingTutorialHasBeenShown = true
        PreferencesUtils.saveBoolean(context, PreferencesUtils.ONBOARDING_TUTORIAL_KEY, true)
        callback?.invoke()
    }

    /**
     * Opens the supplied URL in an external browser.
     */
    private fun openInWebView(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }

    /**
     * Displays the Terms of Service dialog. Invokes [callback] with the user's
     * choice.
     */
    private fun showTermsOfService(context: Context, callback: ((Boolean) -> Unit)? = null) {
        showEulaDialog(context, linksMap[R.id.txt_terms_of_service].toString()) { agreed ->
            agreedToTermsOfService = agreed
            PreferencesUtils.saveBoolean(context, PreferencesUtils.AGREED_TO_TERMS_OF_SERVICE_KEY, agreed)
            Timber.d("User agreed to terms of service: $agreed")
            if (!agreed) {
                Toast.makeText(context, "You need to agree to Terms of Service before using our service.", Toast.LENGTH_LONG).show()
            }
            callback?.invoke(agreed)
        }
    }

    /**
     * Displays the Privacy Policy dialog. Invokes [callback] with the user's
     * choice.
     */
    private fun showPrivacyPolicy(context: Context, callback: ((Boolean) -> Unit)? = null) {
        showEulaDialog(context, linksMap[R.id.txt_privacy_policy].toString()) { agreed ->
            agreedToPrivacyPolicy = agreed
            PreferencesUtils.saveBoolean(context, PreferencesUtils.AGREED_TO_PRIVACY_POLICY_KEY, agreed)
            Timber.d("User agreed to privacy policy: $agreed")
            if (!agreed) {
                Toast.makeText(context, "You need to agree to privacy policy before using our service.", Toast.LENGTH_LONG).show()
            }
            callback?.invoke(agreed)
        }
    }

    /**
     * Generic helper for showing EULA style dialogs that load the agreement
     * from a URL.
     */
    private fun showEulaDialog(context: Context, url: String, callback: ((Boolean) -> Unit)? = null) {
        val dialog = Dialog(context, R.style.FullScreenDialogTheme)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_eula, null)
        val webView = view.findViewById<WebView>(R.id.webview_eula)
        val agreeButton = view.findViewById<Button>(R.id.button_agree)
        val declineButton = view.findViewById<Button>(R.id.button_decline)

        webView.loadUrl(url)

        agreeButton.setOnClickListener {
            Timber.d("Agreed to Terms")
            dialog.dismiss()
            callback?.invoke(true)
        }

        declineButton.setOnClickListener {
            Timber.d("Not accepted")
            dialog.dismiss()
            callback?.invoke(false)
        }
        dialog.setContentView(view)
        dialog.show()
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dpToPx(56)
        val heightSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, heightSpec)
    }

    /**
     * Converts the provided density independent pixel value to a physical
     * pixel value based on the current screen density.
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).roundToInt()
    }
}
