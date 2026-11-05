package com.metashield.app.data.c2pa

import java.io.File

/**
 * Bridge to the official Content Authenticity Initiative (CAI) C2PA Android SDK
 * (`org.contentauth.c2pa.C2PA` and `org.contentauth.c2pa.Reader`).
 *
 * Verified SDK API surface (v0.0.15 / https://opensource.contentauthenticity.org/docs/c2pa-android/):
 * - `C2PA.readFile(path: String): String` (reads and validates manifest store JSON from file)
 * - `Reader.fromStream(format: String, stream: Stream): Reader` -> `reader.json(): String`
 * - Note: The C2PA Android SDK does NOT provide a manifest removal API; removal is handled
 *   separately by MetaShield's format-aware `MetadataCleaningEngine`.
 */
object C2paSdkBridge {

    data class SdkInspectionOutput(
        val sdkAvailableOnClasspath: Boolean,
        val invokedSuccessfully: Boolean,
        val manifestStoreJson: String?,
        val sdkStatusDescription: String
    )

    fun readManifestWithOfficialSdk(file: File, mimeType: String): SdkInspectionOutput {
        return try {
            val c2paClass = Class.forName("org.contentauth.c2pa.C2PA")
            val readFileMethod = c2paClass.methods.firstOrNull {
                it.name == "readFile" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            if (readFileMethod != null) {
                val instance = try {
                    c2paClass.getDeclaredField("INSTANCE").get(null)
                } catch (_: Exception) {
                    null
                }
                val json = readFileMethod.invoke(instance, file.absolutePath) as? String
                SdkInspectionOutput(
                    sdkAvailableOnClasspath = true,
                    invokedSuccessfully = !json.isNullOrBlank(),
                    manifestStoreJson = json,
                    sdkStatusDescription = "org.contentauth.c2pa (Official C2PA Android JNI SDK) + Native JUMBF Inspector"
                )
            } else {
                SdkInspectionOutput(
                    sdkAvailableOnClasspath = true,
                    invokedSuccessfully = false,
                    manifestStoreJson = null,
                    sdkStatusDescription = "org.contentauth.c2pa classpath active; using Native JUMBF/COSE Inspector"
                )
            }
        } catch (t: Throwable) {
            // Handles ClassNotFoundException (when running pure JVM unit tests) or UnsatisfiedLinkError
            // or C2PAError (when a file has no manifest)
            val isC2paError = t.javaClass.name.contains("C2PAError") ||
                t.cause?.javaClass?.name?.contains("C2PAError") == true
            SdkInspectionOutput(
                sdkAvailableOnClasspath = isC2paError,
                invokedSuccessfully = false,
                manifestStoreJson = null,
                sdkStatusDescription = if (isC2paError) {
                    "org.contentauth.c2pa SDK checked (No SDK manifest / Fallback to JUMBF Inspector)"
                } else {
                    "MetaShield On-Device JUMBF / ISO-19566-5 / COSE Inspector Engine"
                }
            )
        }
    }
}
