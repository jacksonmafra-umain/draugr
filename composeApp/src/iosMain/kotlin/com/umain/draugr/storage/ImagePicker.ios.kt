package com.umain.draugr.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeDiskImage
import platform.darwin.NSObject

/**
 * The Files app picker. Large images are easier to place through iTunes File Sharing, which
 * `SideloadStore` discovers on its own; this covers the case where the image is already on the
 * device or in iCloud Drive.
 */
private class PickerDelegate(
    private val machineId: String,
    private val onPicked: (PickedImage?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onPicked(null)
            return
        }
        val store = SideloadStore()
        val name = url.lastPathComponent ?: "sideload.img"
        val target = store.targetPath(machineId, name)
        platformFileSystem.createDirectories(store.directoryFor(machineId))

        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(target.toString(), null)
        // Copied by the file manager rather than read into memory: these images are large, and
        // the source may be a security-scoped iCloud URL.
        val copied = manager.copyItemAtPath(url.path ?: "", toPath = target.toString(), error = null)
        if (!copied) {
            onPicked(null)
            return
        }
        val size = platformFileSystem.metadataOrNull(target)?.size ?: 0L
        store.register(
            SideloadedImage(
                machineId = machineId,
                fileName = name,
                sizeBytes = size,
                sha256 = store.sha256Of(target),
            ),
        )
        onPicked(PickedImage(fileName = name, sizeBytes = size))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

@Composable
actual fun rememberImagePicker(
    machineId: String,
    onPicked: (PickedImage?) -> Unit,
): () -> Unit {
    val delegate = remember(machineId, onPicked) { PickerDelegate(machineId, onPicked) }
    return {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeDiskImage, UTTypeData),
        )
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
