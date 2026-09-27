package com.example.core.camera

object PtpCodes {
    // Operation Codes
    const val OC_GetDeviceInfo: Short = 0x1001
    const val OC_OpenSession: Short = 0x1002
    const val OC_CloseSession: Short = 0x1003
    const val OC_GetStorageIDs: Short = 0x1004
    const val OC_GetStorageInfo: Short = 0x1005
    const val OC_GetObjectHandles: Short = 0x1007
    const val OC_GetObjectInfo: Short = 0x1008
    const val OC_GetObject: Short = 0x1009
    const val OC_GetThumb: Short = 0x100A
    const val OC_DeleteObject: Short = 0x100B
    
    // Canon Extension Operation Codes
    val OC_EOS_GetStorageIDs: Short = 0x9101.toShort()
    val OC_EOS_GetObjectInfo: Short = 0x9103.toShort()
    val OC_EOS_GetObject: Short = 0x9104.toShort()
    val OC_EOS_DeleteObject: Short = 0x9105.toShort()
    val OC_EOS_GetPartialObject: Short = 0x9107.toShort()
    val OC_EOS_GetDeviceInfoEx: Short = 0x9108.toShort()
    val OC_EOS_GetObjectInfoEx: Short = 0x9109.toShort()
    val OC_EOS_GetThumbEx: Short = 0x910A.toShort()
    val OC_EOS_RemoteRelease: Short = 0x910F.toShort()
    val OC_EOS_SetDevicePropValueEx: Short = 0x9110.toShort()
    val OC_EOS_SetRemoteMode: Short = 0x9114.toShort()
    val OC_EOS_SetEventMode: Short = 0x9115.toShort()
    val OC_EOS_GetEvent: Short = 0x9116.toShort()
    val OC_EOS_TransferComplete: Short = 0x9117.toShort()
    val OC_EOS_KeepDeviceOn: Short = 0x911D.toShort()
    val OC_EOS_RemoteReleaseOn: Short = 0x9128.toShort()
    val OC_EOS_RemoteReleaseOff: Short = 0x9129.toShort()
    val OC_EOS_GetLiveViewImage: Short = 0x9153.toShort()

    // Property Codes
    const val PROP_APERTURE = 0xD101
    const val PROP_SHUTTER_SPEED = 0xD102
    const val PROP_ISO = 0xD103
    const val PROP_EXPOSURE_COMPENSATION = 0xD104
    const val PROP_SHOOTING_MODE = 0xD105
    const val PROP_DRIVE_MODE = 0xD106
    const val PROP_WHITE_BALANCE = 0xD10A
    const val PROP_IMAGE_QUALITY = 0xD10E
    const val PROP_EVF_OUTPUT_DEVICE = 0xD1B0  // Actives / deactivates Live View

    // Event Codes
    const val EVENT_OBJECT_ADDED = 0xC181              // New photo captured on camera's card
    const val EVENT_OBJECT_REMOVED = 0xC182            // Photo deleted
    const val EVENT_STORAGE_STATUS_CHANGED = 0xC184    // Storage space change
    const val EVENT_PROPERTY_CHANGED = 0xC18A          // Property changed (e.g. knob turned)
}
