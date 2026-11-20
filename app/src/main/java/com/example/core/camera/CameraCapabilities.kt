package com.example.core.camera

import com.example.core.ptp.DeviceInfo
import com.example.core.ptp.PtpOp

/**
 * What the connected camera says it can do over USB, read once from its operation list at connection.
 * A command the camera doesn't list is never sent and its button is hidden: asking anyway cost a USB
 * round trip with the session locked, and an error on screen. Some cameras list operations they then
 * refuse, so the answers are still checked (the depth of field preview on the 50D, for example).
 *
 * Without a camera every field is true, so the screens look as they always did until one connects.
 */
data class CameraCapabilities(
    val liveView: Boolean = true,
    val af: Boolean = true,
    val afCancel: Boolean = true,
    val driveLens: Boolean = true,
    val zoom: Boolean = true,
    val zoomPosition: Boolean = true,
    val dof: Boolean = true,
    val pressRelease: Boolean = true,
    val remoteRelease: Boolean = true,
    val requestProp: Boolean = true,
    val partialObject: Boolean = true,
) {
    /** One line for the log: what is missing, or that everything the app uses is there. */
    fun describe(): String {
        val missing = buildList {
            if (!liveView) add("live view (0x9153)")
            if (!af) add("AF (0x9154)")
            if (!afCancel) add("fim do AF (0x9160)")
            if (!driveLens) add("mover a lente (0x9155)")
            if (!zoom) add("zoom (0x9158)")
            if (!zoomPosition) add("posição do zoom (0x9159)")
            if (!dof) add("profundidade (0x9156)")
            if (!pressRelease) add("meio curso (0x9128/0x9129)")
            if (!remoteRelease) add("disparo direto (0x910F)")
            if (!requestProp) add("pedir valores (0x9127)")
            if (!partialObject) add("download em blocos (0x101B)")
        }
        return if (missing.isEmpty()) "todas as operações que o app usa" else "sem ${missing.joinToString(", ")}"
    }

    companion object {
        fun of(info: DeviceInfo) = CameraCapabilities(
            liveView = info.supports(PtpOp.EOS_GET_VIEWFINDER_DATA),
            af = info.supports(PtpOp.EOS_DO_AF),
            afCancel = info.supports(PtpOp.EOS_AF_CANCEL),
            driveLens = info.supports(PtpOp.EOS_DRIVE_LENS),
            zoom = info.supports(PtpOp.EOS_ZOOM),
            zoomPosition = info.supports(PtpOp.EOS_ZOOM_POSITION),
            dof = info.supports(PtpOp.EOS_DOF_PREVIEW),
            pressRelease = info.supports(PtpOp.EOS_REMOTE_RELEASE_ON) && info.supports(PtpOp.EOS_REMOTE_RELEASE_OFF),
            remoteRelease = info.supports(PtpOp.EOS_REMOTE_RELEASE),
            requestProp = info.supports(PtpOp.EOS_REQUEST_DEVICE_PROP_VALUE),
            partialObject = info.supports(PtpOp.GET_PARTIAL_OBJECT),
        )
    }
}
