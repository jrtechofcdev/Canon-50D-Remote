package com.example.core.tether

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class SaveReasonTest {
    @Test
    fun fullPhoneIsFoundInsideTheCauseChain() {
        val error = IOException("write failed", IOException("write failed: ENOSPC (No space left on device)"))
        assertEquals(SaveReason.NoSpace, SaveReason.of(error))
    }

    @Test
    fun lostPermissionAndMissingFolderAreToldApart() {
        assertEquals(SaveReason.NoPermission, SaveReason.of(SecurityException("Permission Denial: writing com.android.externalstorage")))
        assertEquals(SaveReason.FolderGone, SaveReason.of(FileNotFoundException("/tree/primary:JPG")))
    }

    @Test
    fun anythingElseKeepsTheGenericAdvice() {
        assertEquals(SaveReason.Other, SaveReason.of(IllegalStateException("PTP timeout")))
    }
}
