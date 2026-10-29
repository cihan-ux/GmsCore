package dev.gspace.noads.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.rawResourcePatch
import java.io.DataInputStream
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.InflaterInputStream

// Based on a working Gspace 2.3.3 original/modified APK pair.
// No original or modified APK, full DEX, licensing code, or private account data is included.
private const val ORIGINAL_SHA256 = "3596977526bfd45da5c8cdca2f72894fd8dca10bb59e73dcee9da236381fb067"
private const val PATCHED_SHA256 = "943b8697a03162e6fabf5099d204b812c5ae56527990ee4def8faaa15aee3bba"
private const val DATA_FILE = "/gspace-233.delta.zlib"
private object PatchAssetAnchor

@Suppress("unused")
val gspaceRemoveAds = rawResourcePatch(
    name = "Gspace – Remove Ads",
    description = "Replay verified NoAds DEX changes for Gspace 2.3.3 (exact build only).",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "Gspace",
            packageName = "com.gspace.android",
            apkFileType = ApkFileType.APK,
            appIconColor = 0x3869AB,
            targets = listOf(AppTarget(version = "2.3.3"))
        )
    )

    execute {
        val dexFile = get("classes.dex")
        val sourceDex = dexFile.readBytes()
        // A version number alone is not safe: vendors can publish multiple builds.
        if (sourceDex.sha256() != ORIGINAL_SHA256) {
            error("Unsupported Gspace APK build: the original classes.dex SHA-256 differs. " +
                  "Use the matching original Gspace 2.3.3 APK; never apply to another release.")
        }
        val patchResource = checkNotNull(PatchAssetAnchor::class.java.getResourceAsStream(DATA_FILE)) {
            "Missing patch data: $DATA_FILE"
        }
        val changed = sourceDex.copyOf()
        patchResource.use { input ->
            DataInputStream(InflaterInputStream(input)).use { patch ->
                val total = patch.readInt()
                require(total in 1..100000) { "Invalid delta record count" }
                var lastOffset = 31
                repeat(total) {
                    val offset = patch.readInt()
                    val newByte = patch.readUnsignedByte()
                    require(offset > lastOffset && offset < changed.size) {
                        "Invalid DEX patch offset"
                    }
                    changed[offset] = newByte.toByte()
                    lastOffset = offset
                }
                require(patch.read() == -1) { "Trailing patch data" }
            }
        }
        // Standard DEX signature and checksum, independent of the ZIP signing step.
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(changed, 32, changed.size - 32)
        val signature = sha1.digest()
        System.arraycopy(signature, 0, changed, 12, signature.size)
        val adler = Adler32()
        adler.update(changed, 12, changed.size - 12)
        val checksum = adler.value.toInt()
        for (i in 0 until 4) changed[8 + i] = (checksum ushr (8 * i)).toByte()
        check(changed.sha256() == PATCHED_SHA256) {
            "Patched DEX checksum mismatch; refusing to write it"
        }
        dexFile.writeBytes(changed)
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
