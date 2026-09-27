package com.example

import android.app.Application
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
import com.example.core.data.PhotoDatabase
import com.example.core.data.SettingsRepository
import com.example.core.service.CameraForegroundService
import com.example.core.storage.PhotoStore
import com.example.core.tether.TetherController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Creates the app's single instances; screens reach them through `application as TetherApp`. */
class TetherApp : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var database: PhotoDatabase
        private set
    lateinit var store: PhotoStore
        private set
    lateinit var camera: CameraManager
        private set
    lateinit var tether: TetherController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        database = PhotoDatabase.create(this)
        store = PhotoStore(this)
        camera = CameraManager(this) { settings.current().keepCameraAwake }
        tether = TetherController(this, camera, settings, store, database.photoDao())

        appScope.launch {
            camera.state.distinctUntilChangedBy { it::class }.collect { state ->
                if (state is ConnectionState.Connected) {
                    tether.resetSessionCounter()
                    CameraForegroundService.start(this@TetherApp)
                }
            }
        }
    }
}
