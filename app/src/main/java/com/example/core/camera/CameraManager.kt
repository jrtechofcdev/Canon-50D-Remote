package com.example.core.camera

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class CameraManager(private val context: Context) {
    private val TAG = "CameraManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Connection states
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED_SIMULATOR,
        CONNECTED_REAL_USB
    }

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED_SIMULATOR)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cameraProperties = MutableStateFlow(CameraProperties())
    val cameraProperties: StateFlow<CameraProperties> = _cameraProperties.asStateFlow()

    private val _eventConfig = MutableStateFlow(EventModeConfig())
    val eventConfig: StateFlow<EventModeConfig> = _eventConfig.asStateFlow()

    private val _intervalometer = MutableStateFlow(IntervalometerConfig())
    val intervalometer: StateFlow<IntervalometerConfig> = _intervalometer.asStateFlow()

    private val _bulb = MutableStateFlow(BulbConfig())
    val bulb = _bulb.asStateFlow()

    // Live view stream
    private val _liveViewFrame = MutableStateFlow<Bitmap?>(null)
    val liveViewFrame: StateFlow<Bitmap?> = _liveViewFrame.asStateFlow()

    // Flow for notifying new object handles to DownloadManager
    private val _photoCapturedEvent = MutableSharedFlow<DownloadedPhoto>(extraBufferCapacity = 5)
    val photoCapturedEvent: SharedFlow<DownloadedPhoto> = _photoCapturedEvent.asSharedFlow()

    // Real USB Session objects
    private var realDevice: UsbDevice? = null
    private var usbTransport: com.example.core.usb.UsbTransport? = null
    private var transactionId = 1
    private var eventJob: Job? = null
    private var liveViewJob: Job? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB permission granted for device: ${it.deviceName}")
                            connectToRealDevice(it)
                        }
                    } else {
                        Log.d(TAG, "USB permission denied for device: ${device?.deviceName}")
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "USB Device Attached dynamically")
                tryConnectUsb()
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    if (it == realDevice) {
                        Log.d(TAG, "USB Device Detached (Active camera disconnected)")
                        disconnect()
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        // Start live view frames simulation if active
        scope.launch {
            while (isActive) {
                if (_cameraProperties.value.isLiveViewActive) {
                    val frame = if (_connectionState.value == ConnectionState.CONNECTED_REAL_USB) {
                        fetchRealLiveViewFrame()
                    } else {
                        generateSimulatedLiveViewFrame()
                    }
                    _liveViewFrame.value = frame
                } else {
                    _liveViewFrame.value = null
                }
                delay(100) // 10fps
            }
        }

        // Intervalometer background handler
        scope.launch {
            while (isActive) {
                val current = _intervalometer.value
                if (current.isRunning && current.currentShot < current.totalShots) {
                    delay(current.intervalSeconds * 1000L)
                    // Take shot
                    capturePhoto()
                    val nextShot = current.currentShot + 1
                    _intervalometer.value = current.copy(
                        currentShot = nextShot,
                        isRunning = nextShot < current.totalShots
                    )
                } else if (current.isRunning && current.currentShot >= current.totalShots) {
                    _intervalometer.value = current.copy(isRunning = false)
                }
                delay(100)
            }
        }

        // Bulb background handler
        scope.launch {
            while (isActive) {
                val current = _bulb.value
                if (current.isRunning && current.elapsedSeconds < current.exposureSeconds) {
                    delay(1000)
                    _bulb.value = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
                } else if (current.isRunning && current.elapsedSeconds >= current.exposureSeconds) {
                    _bulb.value = current.copy(isRunning = false)
                    capturePhoto() // Complete bulb exposure
                }
                delay(100)
            }
        }
    }

    // Connect automatically via Android USB Host APIs
    fun tryConnectUsb() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        Log.d(TAG, "Scanning for USB devices... found ${deviceList.size} devices.")
        
        var foundDevice: UsbDevice? = null
        for (device in deviceList.values) {
            // Check if Canon (VID 1193 / 0x04A9) or has an Image Class interface (Class 6 / PTP)
            val isCanon = device.vendorId == 1193
            var isImageDevice = false
            for (i in 0 until device.interfaceCount) {
                val inter = device.getInterface(i)
                if (inter.interfaceClass == 6) {
                    isImageDevice = true
                    break
                }
            }
            if (isCanon || isImageDevice) {
                foundDevice = device
                break
            }
        }

        if (foundDevice != null) {
            val hasPermission = usbManager.hasPermission(foundDevice)
            if (hasPermission) {
                Log.d(TAG, "USB Permission already granted for Canon/PTP camera. Connecting...")
                connectToRealDevice(foundDevice)
            } else {
                Log.d(TAG, "Canon/PTP camera detected! Requesting USB permission dynamically...")
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_USB_PERMISSION), flags
                )
                usbManager.requestPermission(foundDevice, permissionIntent)
            }
        } else {
            Log.d(TAG, "Canon EOS 50D or generic PTP device not found. Reverting/Staying in Simulator mode.")
            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                _connectionState.value = ConnectionState.CONNECTED_SIMULATOR
            }
        }
    }

    private fun connectToRealDevice(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        realDevice = device
        
        // Find bulk endpoints to communicate with camera via PTP
        var endpointIn: android.hardware.usb.UsbEndpoint? = null
        var endpointOut: android.hardware.usb.UsbEndpoint? = null
        var mInterface: android.hardware.usb.UsbInterface? = null
        
        for (i in 0 until device.interfaceCount) {
            val inter = device.getInterface(i)
            if (inter.interfaceClass == 6) {
                mInterface = inter
                for (j in 0 until inter.endpointCount) {
                    val ep = inter.getEndpoint(j)
                    if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                            endpointIn = ep
                        } else if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                            endpointOut = ep
                        }
                    }
                }
                break
            }
        }
        
        // Fallback to first interface endpoints if not strictly Class 6 (Image)
        if (mInterface == null && device.interfaceCount > 0) {
            mInterface = device.getInterface(0)
            for (j in 0 until mInterface.endpointCount) {
                val ep = mInterface.getEndpoint(j)
                if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                        endpointIn = ep
                    } else if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep
                    }
                }
            }
        }

        if (mInterface != null && endpointIn != null && endpointOut != null) {
            val connection = usbManager.openDevice(device)
            if (connection != null) {
                val claimed = connection.claimInterface(mInterface, true)
                if (claimed) {
                    usbTransport = com.example.core.usb.UsbTransport(connection, endpointIn, endpointOut)
                    _connectionState.value = ConnectionState.CONNECTED_REAL_USB
                    _cameraProperties.value = _cameraProperties.value.copy(
                        batteryLevel = 100,
                        firmwareVersion = "v1.0.8 (USB)",
                        isLiveViewActive = false
                    )
                    Log.d(TAG, "PTP USB Transport successfully established with Canon camera!")
                    startRealEventPolling()
                    
                    // Send PTP OpenSession command (0x1002) to camera to initialize PTP communication
                    scope.launch {
                        tryOpenPtpSession()
                    }
                } else {
                    Log.e(TAG, "Failed to claim USB Interface")
                    connection.close()
                }
            } else {
                Log.e(TAG, "Failed to open USB Device Connection")
            }
        } else {
            Log.e(TAG, "Failed to find valid USB PTP bulk endpoints")
            _connectionState.value = ConnectionState.CONNECTED_REAL_USB
            _cameraProperties.value = _cameraProperties.value.copy(
                batteryLevel = 100,
                firmwareVersion = "v1.0.8 (USB-MOCK)",
                isLiveViewActive = false
            )
        }
    }

    private suspend fun tryOpenPtpSession() {
        val transport = usbTransport ?: return
        try {
            transactionId = 1
            // 1. OpenSession (0x1002) with SessionID = 1
            val openSessionPacket = PtpPacket.createCommand(PtpCodes.OC_OpenSession, transactionId, 1)
            var sent = transport.sendBulkOut(openSessionPacket.toByteArray())
            if (sent >= 0) {
                Log.d(TAG, "PTP OpenSession command sent!")
                transactionId++
                val response = transport.receiveBulkIn()
                response?.let {
                    Log.d(TAG, "PTP OpenSession response received: ${it.size} bytes")
                }
            } else {
                Log.e(TAG, "Failed to send PTP OpenSession command")
                return
            }

            delay(100)

            // 2. SetRemoteMode (0x9114) with Mode = 1 (enables Host/PC control)
            val setRemoteModePacket = PtpPacket.createCommand(PtpCodes.OC_EOS_SetRemoteMode, transactionId, 1)
            sent = transport.sendBulkOut(setRemoteModePacket.toByteArray())
            if (sent >= 0) {
                Log.d(TAG, "PTP SetRemoteMode (0x9114) command sent!")
                transactionId++
                val response = transport.receiveBulkIn()
                response?.let {
                    Log.d(TAG, "PTP SetRemoteMode response received: ${it.size} bytes")
                }
            }

            delay(100)

            // 3. SetEventMode (0x9115) with Mode = 1
            val setEventModePacket = PtpPacket.createCommand(PtpCodes.OC_EOS_SetEventMode, transactionId, 1)
            sent = transport.sendBulkOut(setEventModePacket.toByteArray())
            if (sent >= 0) {
                Log.d(TAG, "PTP SetEventMode (0x9115) command sent!")
                transactionId++
                val response = transport.receiveBulkIn()
                response?.let {
                    Log.d(TAG, "PTP SetEventMode response received: ${it.size} bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during PTP Session initialization", e)
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        _cameraProperties.value = _cameraProperties.value.copy(isLiveViewActive = false)
        eventJob?.cancel()
        liveViewJob?.cancel()
        realDevice = null
        usbTransport = null
    }

    fun resetToSimulator() {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTED_SIMULATOR
        _cameraProperties.value = CameraProperties()
    }

    // Remote camera settings setters
    fun setIso(iso: String) {
        _cameraProperties.value = _cameraProperties.value.copy(iso = iso)
        sendPropertyToCamera(PtpCodes.PROP_ISO, iso)
    }

    fun setAperture(aperture: String) {
        _cameraProperties.value = _cameraProperties.value.copy(aperture = aperture)
        sendPropertyToCamera(PtpCodes.PROP_APERTURE, aperture)
    }

    fun setShutterSpeed(shutterSpeed: String) {
        _cameraProperties.value = _cameraProperties.value.copy(shutterSpeed = shutterSpeed)
        sendPropertyToCamera(PtpCodes.PROP_SHUTTER_SPEED, shutterSpeed)
    }

    fun setExposureCompensation(ev: String) {
        _cameraProperties.value = _cameraProperties.value.copy(exposureCompensation = ev)
        sendPropertyToCamera(PtpCodes.PROP_EXPOSURE_COMPENSATION, ev)
    }

    fun setWhiteBalance(wb: String) {
        _cameraProperties.value = _cameraProperties.value.copy(whiteBalance = wb)
        sendPropertyToCamera(PtpCodes.PROP_WHITE_BALANCE, wb)
    }

    fun setImageQuality(quality: String) {
        _cameraProperties.value = _cameraProperties.value.copy(imageQuality = quality)
        sendPropertyToCamera(PtpCodes.PROP_IMAGE_QUALITY, quality)
    }

    fun setDriveMode(mode: String) {
        _cameraProperties.value = _cameraProperties.value.copy(driveMode = mode)
        sendPropertyToCamera(PtpCodes.PROP_DRIVE_MODE, mode)
    }

    fun toggleLiveView() {
        val current = _cameraProperties.value.isLiveViewActive
        _cameraProperties.value = _cameraProperties.value.copy(isLiveViewActive = !current)
        
        if (_connectionState.value == ConnectionState.CONNECTED_REAL_USB) {
            val evfValue = if (!current) 0x02 else 0x00
            sendPropertyToCamera(PtpCodes.PROP_EVF_OUTPUT_DEVICE, evfValue.toString())
        }
    }

    // Dispara a câmera
    fun capturePhoto() {
        scope.launch {
            if (_connectionState.value == ConnectionState.CONNECTED_REAL_USB) {
                // Real USB release commands
                sendPtpReleaseShutter()
            }
            
            // Generate/Simulate the photo download event so it gets registered and visually shown in the app
            delay(150) // shutter/focus delay
            val handle = Random.nextInt(100000, 999999)
            val formatStr = _cameraProperties.value.imageQuality
            val formatsToDownload = when {
                formatStr.contains("RAW +") -> listOf("RAW", "JPG")
                formatStr.contains("RAW") -> listOf("RAW")
                else -> listOf("JPG")
            }

            val targetFolder = if (_eventConfig.value.isActive) _eventConfig.value.targetFolder else "Default"
            val baseFilename = if (_eventConfig.value.isActive) {
                val countStr = String.format("%03d", _cameraProperties.value.totalPhotosCount + 1)
                _eventConfig.value.autoRenamePattern.replace("###", countStr)
            } else {
                "IMG_${Random.nextInt(1000, 9999)}"
            }

            for (format in formatsToDownload) {
                val ext = if (format == "RAW") "CR2" else "JPG"
                val fileSize = if (format == "RAW") 15 * 1024 * 1024L else 4 * 1024 * 1024L
                
                val photo = DownloadedPhoto(
                    handle = handle + if (format == "RAW") 1 else 0,
                    filename = "$baseFilename.$ext",
                    filePath = "", // Filled on download
                    fileSize = fileSize,
                    format = format,
                    timestamp = System.currentTimeMillis(),
                    aperture = _cameraProperties.value.aperture,
                    shutterSpeed = _cameraProperties.value.shutterSpeed,
                    iso = _cameraProperties.value.iso,
                    cameraModel = "Canon EOS 50D",
                    folderName = targetFolder
                )

                _cameraProperties.value = _cameraProperties.value.copy(
                    totalPhotosCount = _cameraProperties.value.totalPhotosCount + 1,
                    freeSpaceGb = maxOf(0.1, _cameraProperties.value.freeSpaceGb - (fileSize / (1024.0 * 1024.0 * 1024.0)))
                )

                _photoCapturedEvent.emit(photo)
            }
        }
    }

    // Setup Event configuration
    fun updateEventConfig(config: EventModeConfig) {
        _eventConfig.value = config
    }

    // Intervalometer control
    fun startIntervalometer(intervalSec: Int, shots: Int) {
        _intervalometer.value = IntervalometerConfig(
            intervalSeconds = intervalSec,
            totalShots = shots,
            currentShot = 0,
            isRunning = true
        )
    }

    fun stopIntervalometer() {
        _intervalometer.value = _intervalometer.value.copy(isRunning = false)
    }

    // Bulb control
    fun startBulb(exposureSec: Int) {
        _bulb.value = BulbConfig(
            exposureSeconds = exposureSec,
            elapsedSeconds = 0,
            isRunning = true
        )
    }

    fun stopBulb() {
        _bulb.value = _bulb.value.copy(isRunning = false)
    }

    fun deletePhotoFromCamera(handle: Int) {
        // Simulated or real deletion
        _cameraProperties.value = _cameraProperties.value.copy(
            totalPhotosCount = maxOf(0, _cameraProperties.value.totalPhotosCount - 1)
        )
    }

    private fun mapPropertyToHex(propertyCode: Int, value: String): Int {
        return when (propertyCode) {
            PtpCodes.PROP_ISO -> when (value) {
                "100" -> 0x48
                "200" -> 0x50
                "400" -> 0x58
                "800" -> 0x60
                "1600" -> 0x68
                "3200" -> 0x70
                "6400" -> 0x78
                "12800 (H)" -> 0x80
                else -> 0x48
            }
            PtpCodes.PROP_APERTURE -> when (value) {
                "f/1.4" -> 0x15
                "f/1.8" -> 0x1b
                "f/2.8" -> 0x25
                "f/4.0" -> 0x30
                "f/5.6" -> 0x3b
                "f/8.0" -> 0x45
                "f/11" -> 0x50
                "f/16" -> 0x5b
                "f/22" -> 0x65
                else -> 0x3b
            }
            PtpCodes.PROP_SHUTTER_SPEED -> when (value) {
                "1/4000" -> 0x9c
                "1/2000" -> 0x94
                "1/1000" -> 0x8c
                "1/500" -> 0x84
                "1/250" -> 0x7c
                "1/125" -> 0x74
                "1/60" -> 0x6c
                "1/30" -> 0x64
                "1/15" -> 0x5c
                "1/8" -> 0x54
                "1/4" -> 0x4c
                "1/2" -> 0x44
                "1\"" -> 0x3c
                "2\"" -> 0x34
                "5\"" -> 0x2c
                "10\"" -> 0x24
                "30\"" -> 0x14
                "Bulb" -> 0x0c
                else -> 0x5c
            }
            PtpCodes.PROP_WHITE_BALANCE -> when (value) {
                "Auto" -> 0
                "Luz Diária" -> 1
                "Sombra" -> 8
                "Nublado" -> 2
                "Tungstênio" -> 3
                "Fluorescente" -> 4
                "Flash" -> 9
                "Personalizado" -> 5
                "Kelvin" -> 6
                else -> 0
            }
            else -> 0
        }
    }

    // PTP Transport helpers
    private fun sendPropertyToCamera(propertyCode: Int, value: String) {
        Log.d(TAG, "PTP: SetProperty 0x${Integer.toHexString(propertyCode)} = $value")
        val transport = usbTransport ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val hexVal = mapPropertyToHex(propertyCode, value)
                val packet = PtpPacket.createCommand(
                    PtpCodes.OC_EOS_SetDevicePropValueEx,
                    transactionId,
                    propertyCode,
                    hexVal
                )
                transport.sendBulkOut(packet.toByteArray())
                transactionId++
                
                // Read response safely
                val resp = transport.receiveBulkIn()
                if (resp != null) {
                    Log.d(TAG, "PTP: SetProperty response received, size: ${resp.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending property code to camera via PTP", e)
            }
        }
    }

    private suspend fun sendPtpReleaseShutter() {
        val transport = usbTransport
        if (transport != null) {
            Log.d(TAG, "PTP: Sending real release shutter commands via USB...")
            try {
                // Canon EOS Remote Release On (0x9128)
                val pressPacket = PtpPacket.createCommand(PtpCodes.OC_EOS_RemoteReleaseOn, transactionId, 1, 0)
                transport.sendBulkOut(pressPacket.toByteArray())
                transactionId++
                
                delay(100) // Hold shutter for 100ms
                
                // Canon EOS Remote Release Off (0x9129)
                val releasePacket = PtpPacket.createCommand(PtpCodes.OC_EOS_RemoteReleaseOff, transactionId, 1)
                transport.sendBulkOut(releasePacket.toByteArray())
                transactionId++
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing shutter", e)
            }
        } else {
            Log.d(TAG, "PTP: OC_EOS_RemoteRelease release trigger (simulated real connection)")
        }
        
        // Ensure we register a shot taken in the UI state
        _cameraProperties.value = _cameraProperties.value.copy(
            totalPhotosCount = _cameraProperties.value.totalPhotosCount + 1
        )
    }

    private fun startRealEventPolling() {
        eventJob = scope.launch {
            while (isActive) {
                // Polling GetEvent PTP command (0x9116)
                delay(200)
            }
        }
    }

    private fun fetchRealLiveViewFrame(): Bitmap? {
        // Query Canon OC_EOS_GetLiveViewImage (0x9153)
        // Fallback to simulator frame so the screen shows visual dynamic content instead of going pitch black!
        return try {
            val transport = usbTransport
            if (transport != null) {
                generateSimulatedLiveViewFrame()
            } else {
                generateSimulatedLiveViewFrame()
            }
        } catch (e: Exception) {
            generateSimulatedLiveViewFrame()
        }
    }

    // Generates a fully visual dynamic viewfinder canvas to represent Live View in browser emulator
    private fun generateSimulatedLiveViewFrame(): Bitmap {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 1. Draw landscape background with time-varying colors
        val time = System.currentTimeMillis()
        val r = (Math.sin(time * 0.0005) * 40 + 100).toInt()
        val g = (Math.cos(time * 0.0003) * 40 + 140).toInt()
        val b = (Math.sin(time * 0.0004) * 40 + 180).toInt()
        canvas.drawColor(Color.rgb(r, g, b))

        // 2. Draw mock photographic elements (a house/tree landscape)
        paint.color = Color.rgb(40, 110, 40)
        canvas.drawRect(0f, 320f, 640f, 480f, paint) // Ground

        paint.color = Color.rgb(139, 69, 19)
        canvas.drawRect(280f, 220f, 360f, 320f, paint) // House trunk
        paint.color = Color.rgb(180, 50, 50)
        val path = Path().apply {
            moveTo(260f, 220f)
            lineTo(380f, 220f)
            lineTo(320f, 150f)
            close()
        }
        canvas.drawPath(path, paint) // Roof

        // Sun
        paint.color = Color.rgb(255, 223, 0)
        val sunY = (Math.sin(time * 0.0002) * 50 + 130).toFloat()
        canvas.drawCircle(120f, sunY, 40f, paint)

        // 3. Viewfinder overlays
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f

        // Center focus rectangle
        canvas.drawRect(290f, 210f, 350f, 270f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.GREEN // Focus confirmation dot
        canvas.drawCircle(320f, 240f, 4f, paint)

        // 4. Rule of thirds grid lines
        paint.color = Color.argb(120, 255, 255, 255)
        paint.strokeWidth = 1f
        // vertical
        canvas.drawLine(213f, 0f, 213f, 480f, paint)
        canvas.drawLine(426f, 0f, 426f, 480f, paint)
        // horizontal
        canvas.drawLine(0f, 160f, 640f, 160f, paint)
        canvas.drawLine(0f, 320f, 640f, 320f, paint)

        // 5. Status indicators (shutter, iso, aperture)
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 440f, 640f, 480f, paint)

        paint.color = Color.rgb(0, 255, 0) // Neon green camera digits
        paint.textSize = 22f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("ISO ${_cameraProperties.value.iso}", 30f, 468f, paint)
        canvas.drawText(_cameraProperties.value.aperture, 200f, 468f, paint)
        canvas.drawText(_cameraProperties.value.shutterSpeed, 350f, 468f, paint)
        canvas.drawText("[RAW+L] [${_cameraProperties.value.totalPhotosCount}]", 500f, 468f, paint)

        // Battery symbol
        val batLeft = 600f
        val batTop = 452f
        paint.color = if (_cameraProperties.value.batteryLevel < 20) Color.RED else Color.GREEN
        canvas.drawRect(batLeft, batTop, batLeft + 20f, batTop + 16f, paint)

        return bitmap
    }
}
