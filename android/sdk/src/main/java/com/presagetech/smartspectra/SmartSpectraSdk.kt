package com.presagetech.smartspectra

import android.content.Context
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.presage.physiology.proto.MetricsProto.MetricsBuffer
import com.presage.physiology.proto.MetricsProto.Metrics
import org.opencv.android.OpenCVLoader
import timber.log.Timber

/**
 * Entry point for interacting with the SmartSpectra Android SDK.
 *
 * Obtain an instance by calling [initialize] once and then [getInstance].
 * All configuration for measurements and callbacks is performed through this
 * class. Only a single instance should exist within an application.
 */
class SmartSpectraSdk private constructor(private val appContext: Context) {
    private lateinit var apiKey: String


    private val _metricsBuffer = MutableLiveData<MetricsBuffer>()
    val metricsBuffer: LiveData<MetricsBuffer> = _metricsBuffer

    private val _edgeMetrics = MutableLiveData<Metrics>()
    val edgeMetrics: LiveData<Metrics> = _edgeMetrics

    private val _errorMessage = MutableLiveData<String>()
    internal val errorMessage: LiveData<String> = _errorMessage

    init {
        // Uncomment to use test server
        // use with extreme caution
        // useTestServer()
    }

    /**
     * Returns the configured API key. If OAuth is enabled and the key was not
     * explicitly provided, an [IllegalStateException] is thrown to surface the
     * missing configuration to developers.
     */
    internal fun getApiKey(): String {
        if (!::apiKey.isInitialized) {
            val authHandler = AuthHandler.getInstance()
            if (authHandler.isOAuthEnabled()) {
                apiKey = ""
            } else {
                throw IllegalStateException("API key is not initialized. Use .setApiKey() method on SmartSpectraButton to set the key")
            }
        }
        return apiKey
    }

    /**
     * Sets the API key used for API key based authentication.
     *
     * If OAuth configuration is present this value will be ignored.
     */
    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }


    /**
     * Updates the current metrics buffer that observers listen to.
     */
    fun setMetricsBuffer(metricsBuffer: MetricsBuffer) {
        _metricsBuffer.postValue(metricsBuffer)
    }

    /**
     * Registers an observer that is invoked whenever new metrics are produced.
     */
    fun setMetricsBufferObserver(observer: (MetricsBuffer) -> Unit) {
        metricsBuffer.observeForever(observer)
    }

    /**
     * Updates the current edge metrics that observers listen to.
     */
    fun setEdgeMetrics(edgeMetrics: Metrics) {
        _edgeMetrics.postValue(edgeMetrics)
    }

    /**
     * Registers an observer that is invoked whenever new edge metrics are produced.
     */
    fun setEdgeMetricsObserver(observer: (Metrics) -> Unit) {
        edgeMetrics.observeForever(observer)
    }

    /**
     * Posts an error message that will be observed by UI components such as
     * [SmartSpectraResultView].
     */
    internal fun setErrorMessage(message: String) {
        _errorMessage.postValue(message)
    }

    // Setter methods to allow users to configure SmartSpectraButton and SmartSpectraView
    /**
     * Sets the measurement duration in seconds for spot mode.
     *
     * Valid values are in the range 20–120 seconds.
     */
    fun setMeasurementDuration(measurementDuration: Double) {
        SmartSpectraSdkConfig.spotDuration = measurementDuration
    }

    /** Enables or disables display of frames-per-second information. */
    fun setShowFps(showFps: Boolean) {
        SmartSpectraSdkConfig.SHOW_FPS = showFps
    }

    /**
     * Sets the countdown delay in seconds before recording starts.
     */
    fun setRecordingDelay(recordingDelay: Int) {
        SmartSpectraSdkConfig.recordingDelay = recordingDelay
    }

    /**
     * Defines whether measurements run in [SmartSpectraMode.SPOT] or
     * [SmartSpectraMode.CONTINUOUS].
     */
    fun setSmartSpectraMode(smartSpectraMode: SmartSpectraMode) {
        SmartSpectraSdkConfig.smartSpectraMode = smartSpectraMode
    }

    /**
     * Enables or disables collection of edge metrics on device.
     */
    fun setEnableEdgeMetrics(enableEdgeMetrics: Boolean){
        SmartSpectraSdkConfig.enableEdgeMetrics = enableEdgeMetrics;
    }

    /**
     * Sets which device camera to use when capturing.
     */
    fun setCameraPosition(@CameraSelector.LensFacing cameraPosition: Int) {
        SmartSpectraSdkConfig.cameraPosition = cameraPosition
    }

    /**
     * Controls whether camera and mode selection UI is shown in the screening view.
     */
    fun showControlsInScreeningView(customizationEnabled: Boolean) {
        SmartSpectraSdkConfig.showControlsInScreeningView = customizationEnabled
        Timber.e("show controls: ${customizationEnabled}")
    }

    /**
     * This method switches the SDK to use the test server instead of the production server.
     *
     * This is an experimental feature and should only be used for testing purposes.
     *          Ensure you understand the implications before using this method.
     */
    @ExperimentalFeature
    internal fun useTestServer() {
        Timber.w("Using test server. Do not use this for production")
        nativeUseTestServer()
    }

    /** Clears the current metrics buffer and notifies observers. */
    internal fun clearMetricsBuffer() {
        setMetricsBuffer(MetricsBuffer.getDefaultInstance())
    }

    /**
     * Clears metrics and error state from the previous session.
     *
     * Resets `metricsBuffer`, `edgeMetrics`, and `errorMessage` so a new
     * recording session starts without stale values appearing in the UI.
     */
    fun resetMetrics() {
        _metricsBuffer.postValue(MetricsBuffer.getDefaultInstance())
        _edgeMetrics.postValue(Metrics.getDefaultInstance())
        _errorMessage.postValue("")
    }

    /**
     * JNI bridge that instructs the native layer to connect to the test server.
     */
    private external fun nativeUseTestServer()

    companion object {
        @Volatile
        private var INSTANCE: SmartSpectraSdk? = null

        /**
         * Initializes the SDK singleton and loads all required native libraries.
         *
         * This method should be invoked once, typically from an `Application`
         * class before any other SDK API is used.
         */
        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        // load support libraries
                        if (isSupportedAbi()) {
                            // Load necessary libraries
                            System.loadLibrary("mediapipe_jni")
                            if (OpenCVLoader.initLocal()) {
                                Timber.i("OpenCV loaded successfully");
                            } else {
                                Timber.e("OpenCV initialization failed!");
                            }
                        }
                        // create a new instance
                        INSTANCE = SmartSpectraSdk(context.applicationContext)
                    }
                }
            }
        }

        /**
         * Returns the previously initialized [SmartSpectraSdk] instance.
         *
         * @throws IllegalStateException if [initialize] has not been called yet.
         */
        fun getInstance(): SmartSpectraSdk {
            return INSTANCE
                ?: throw IllegalStateException("SmartSpectraSdk must be initialized first.")
        }

        /**
         * Checks whether the device architecture is supported by the native
         * MediaPipe libraries bundled with the SDK.
         */
        internal fun isSupportedAbi(): Boolean {
            Build.SUPPORTED_ABIS.forEach {
                if (it == "arm64-v8a" || it == "armeabi-v7a") {
                    return true
                }
            }
            return false
        }
    }
}
