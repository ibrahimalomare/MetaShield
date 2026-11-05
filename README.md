# MetaShield — Android Image Metadata & C2PA Content Credentials Inspector & Sanitizer

**MetaShield** is a complete, production-structured Android application written in **Kotlin** with **Jetpack Compose (Material 3)** and **Clean Architecture + MVVM**. It performs **100% on-device** inspection and format-aware lossless sanitization of embedded image metadata and **C2PA Content Credentials** (`ISO/IEC 19566-5` JUMBF superboxes, `COSE_Sign1` cryptographic signatures, `EXIF`, `XMP`, `IPTC`, and container comments/chunks) while preserving the user's original source image untouched.

## 1. Project Identity & Technical Specifications

| Attribute | Specification |
| :--- | :--- |
| **Application Name** | MetaShield (`com.metashield.app`) |
| **Platform & SDK Levels** | Android — `minSdk = 28` (Android 9.0 Pie), `targetSdk = 35`, `compileSdk = 35` |
| **Language & UI Framework** | Kotlin `2.0.21` + Jetpack Compose BOM `2024.10.01` (Material 3 Dark Theme) |
| **Architecture** | Clean Architecture (`domain`, `data`, `ui`) + MVVM (`StateFlow`, Coroutines, Unidirectional Data Flow) |
| **C2PA Integration** | Official CAI [C2PA Android SDK](https://opensource.contentauthenticity.org/docs/c2pa-android/) (`com.github.contentauth:c2pa-android:0.0.15` / `org.contentauth.c2pa`) via `C2paSdkBridge` + deterministic Kotlin `JumbfBinaryParser` & `CborCoseInspector` |
| **Privacy & Network Guarantee** | `android.permission.INTERNET` is **omitted** from `AndroidManifest.xml`; OS kernel blocks all network sockets |

## 2. Complete Project Directory Tree

```text
MetaShield/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/metashield/app/
        │   │   ├── MetaShieldApp.kt
        │   │   ├── MainActivity.kt
        │   │   ├── di/
        │   │   │   └── AppContainer.kt
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── ImageFormat.kt
        │   │   │   │   ├── ImportedImageInfo.kt
        │   │   │   │   ├── MetadataModels.kt
        │   │   │   │   ├── C2paModels.kt
        │   │   │   │   ├── CleaningModels.kt
        │   │   │   │   └── RecentOperation.kt
        │   │   │   ├── repository/
        │   │   │   │   └── MetaShieldRepository.kt
        │   │   │   └── usecase/
        │   │   │       ├── InspectImageUseCase.kt
        │   │   │       └── CleanMetadataUseCase.kt
        │   │   ├── data/
        │   │   │   ├── c2pa/
        │   │   │   │   ├── C2paSdkBridge.kt
        │   │   │   │   ├── JumbfBinaryParser.kt
        │   │   │   │   └── CborCoseInspector.kt
        │   │   │   ├── parser/
        │   │   │   │   ├── FormatDetector.kt
        │   │   │   │   ├── ExifBinaryReader.kt
        │   │   │   │   ├── XmpXmlParser.kt
        │   │   │   │   ├── IptcIimParser.kt
        │   │   │   │   └── MetadataScannerEngine.kt
        │   │   │   ├── cleaner/
        │   │   │   │   ├── JpegLosslessCleaner.kt
        │   │   │   │   ├── PngLosslessCleaner.kt
        │   │   │   │   ├── WebpLosslessCleaner.kt
        │   │   │   │   ├── TiffFormatCleaner.kt
        │   │   │   │   ├── PostCleaningVerifier.kt
        │   │   │   │   └── MetadataCleaningEngine.kt
        │   │   │   ├── storage/
        │   │   │   │   ├── SafFileHandler.kt
        │   │   │   │   └── PreferencesAndHistoryStore.kt
        │   │   │   └── repository/
        │   │   │       └── MetaShieldRepositoryImpl.kt
        │   │   └── ui/
        │   │       ├── theme/
        │   │       │   ├── Color.kt
        │   │       │   ├── Type.kt
        │   │       │   └── Theme.kt
        │   │       ├── components/
        │   │       │   └── MetaShieldComponents.kt
        │   │       ├── navigation/
        │   │       │   ├── Screen.kt
        │   │       │   └── MetaShieldNavHost.kt
        │   │       ├── viewmodel/
        │   │       │   └── MetaShieldViewModel.kt
        │   │       └── screens/
        │   │           ├── SplashScreen.kt
        │   │           ├── HomeDashboardScreen.kt
        │   │           ├── ImageSelectionScreen.kt
        │   │           ├── ImagePreviewScreen.kt
        │   │           ├── MetadataScannerScreen.kt
        │   │           ├── C2paInspectionScreen.kt
        │   │           ├── MetadataCleaningScreen.kt
        │   │           ├── ProcessingScreen.kt
        │   │           ├── BeforeAfterResultsScreen.kt
        │   │           ├── ExportSaveScreen.kt
        │   │           ├── SettingsScreen.kt
        │   │           └── AboutMetaShieldScreen.kt
        │   └── res/
        │       ├── drawable/
        │       ├── mipmap-anydpi-v26/
        │       ├── values/
        │       └── xml/
        └── test/
            └── java/com/metashield/app/
                ├── TestImageFactory.kt
                └── MetaShieldEngineTest.kt
```

## 3. Format-Aware Inspection & Lossless Cleaning Matrix

| Format | C2PA / JUMBF Carrier | EXIF Carrier | XMP Carrier | IPTC / Other Carrier | Lossless Cleaning Strategy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **JPEG** (`.jpg`, `.jpeg`) | `APP11` (`0xFFEB`) JPEG XT `'JP'` segments carrying `jumb` superbox | `APP1` (`0xFFE1`) `'Exif\0\0'` + TIFF IFDs (`IFD0`, `ExifSubIFD`, `GPS IFD`) | `APP1` (`0xFFE1`) `'http://ns.adobe.com/xap/1.0/\0'` | `APP13` (`0xFFED`) `'Photoshop 3.0'` `8BIM` `0x0404` (`0x1C 0x02` IIM records) + `COM` (`0xFFFE`) | `JpegLosslessCleaner` excises targeted `APP11`, `APP1`, `APP13`, and `COM` segments before `SOS` (`0xFFDA`) and copies `DQT`, `DHT`, `SOF*`, and entropy scan bytes verbatim (0% re-compression). |
| **PNG** (`.png`) | Ancillary `'caBX'` chunk containing `jumb` superbox | Ancillary `'eXIf'` chunk | `'iTXt'` chunk with `'XML:com.adobe.xmp'` keyword | `'tEXt'`, `'zTXt'`, `'iTXt'`, `'tIME'` chunks | `PngLosslessCleaner` verifies CRC32 on every chunk, drops `'caBX'`, `'eXIf'`, `'iTXt'` (XMP), and text/time chunks, and preserves `'IHDR'`, `'PLTE'`, `'tRNS'`, `'IDAT'`, `'IEND'`, and `'iCCP'` verbatim. |
| **WebP** (`.webp`) | RIFF `'C2PA'` / `'jumb'` chunk | RIFF `'EXIF'` chunk (`VP8X` bit `0x08`) | RIFF `'XMP '` chunk (`VP8X` bit `0x04`) | RIFF `'ICCP'` color profile (`VP8X` bit `0x20`) | `WebpLosslessCleaner` drops targeted `'C2PA'`, `'EXIF'`, and `'XMP '` chunks, clears the corresponding feature flags in `'VP8X'`, recalculates the 32-bit little-endian RIFF size, and preserves `'VP8 '`/`'VP8L'` bitstreams verbatim. |
| **TIFF** (`.tif`, `.tiff`) | IFD0 Tag `0xCD41` (`52545` `C2PA_MANIFEST`) | IFD0 descriptive tags + `0x8769` (`ExifIFDPointer`) + `0x8825` (`GPSInfoIFDPointer`) | IFD0 Tag `0x02BC` (`700` `XMLPacket`) | IFD0 Tag `0x83BB` (`33723` `IPTC-NAA`) + `0x8649` (`PhotoshopImageResources`) | `TiffFormatCleaner` rewrites IFD0 in-place (preserving exact file offsets for `StripOffsets` and `TileOffsets`) and zero-fills excised tag value ranges in slack space. |

## 4. C2PA SDK Verification & Validation States

MetaShield integrates the official Content Authenticity Initiative Android library ([contentauth/c2pa-android](https://github.com/contentauth/c2pa-android), package `org.contentauth.c2pa`) and pairs it with a native Kotlin ISO/IEC 19566-5 JUMBF & COSE_Sign1 inspector:

1. **Why a separate cleaning engine is required**: The official `c2pa-android` SDK provides `C2PA.readFile(...)`, `Reader.fromStream(...)`, and `Builder.sign(...)` for reading, validating, and signing manifests, but **does not provide an API to strip or remove C2PA manifests**. MetaShield implements dedicated format-aware binary surgeons (`JpegLosslessCleaner`, `PngLosslessCleaner`, `WebpLosslessCleaner`, `TiffFormatCleaner`) to remove C2PA JUMBF structures safely.
2. **Five Distinct Validation States (`C2paValidationStatus`)**:
   - `VALID`: Cryptographic COSE signature and payload hash bindings (`c2pa.hash.data`) verified with zero errors.
   - `INVALID`: Signature or asset hash binding mismatch (`claimSignature.mismatch` / `assertion.dataHash.mismatch`).
   - `UNTRUSTED_OR_UNVERIFIABLE`: Structurally valid JUMBF manifest and `COSE_Sign1` block present, but signing certificate is self-signed or cannot be chained to a trusted root authority offline.
   - `NO_CREDENTIALS_FOUND`: No C2PA JUMBF boxes, `caBX` chunks, or XMP provenance declarations exist in the file.
   - `UNSUPPORTED_OR_UNREADABLE`: C2PA carrier segment detected, but the JUMBF superbox is truncated or malformed.
3. **Epistemic & Technical Scope Notices**:
   - **Missing Credentials ≠ Proof of Human Creation**: MetaShield explicitly warns users that `NO_CREDENTIALS_FOUND` never proves an image is authentic, unedited, or human-created.
   - **External Manifests & Pixel Watermarks (SynthID)**: MetaShield clearly explains that remote provenance registries (matched by perceptual hashing) and imperceptible pixel-level watermarks (such as Google SynthID) reside outside container metadata headers and are not removed by file metadata sanitization.

## 5. How to Open, Sync, Test, Build APK, and Install in Android Studio

### Prerequisites
- **Android Studio Ladybug (2024.2.1)+** or **Hedgehog (2023.1.1)+**
- **JDK 17** (bundled with Android Studio as `jbr-17`)
- **Android SDK Platform 35** (`platforms;android-35`) and **Build-Tools 35.0.0**

### Step 1: Extract and Open in Android Studio
1. Extract `MetaShield-Android-Project.zip` to a local folder (`MetaShield/`).
2. Open **Android Studio** → **Open** → select the `MetaShield` directory.
3. Allow Android Studio to sync Gradle dependencies from Google Maven, Maven Central, and JitPack (`https://jitpack.io`).
   - *Note*: If building in an environment where JitPack JNI artifacts are blocked by a corporate firewall, set `metashield.enableC2paNativeAar=false` in `gradle.properties`; MetaShield's reflection-safe `C2paSdkBridge` and built-in Kotlin JUMBF/CBOR/COSE inspector compile and operate with zero external native binary dependencies.

### Step 2: Run the Automated JUnit Test Suite (10 Scenarios)
```bash
./gradlew testDebugUnitTest
```

### Step 3: Build the Debug & Release APKs
```bash
# Generate Debug APK
./gradlew assembleDebug

# Generate Minified & Signed Release APK (signed with debug key for immediate device testing)
./gradlew assembleRelease
```

The generated APK files will be located at:
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Step 4: Install on a Connected Android Phone (API 28 / Android 9.0+)
```bash
# Install Debug APK directly via ADB
./gradlew installDebug

# Or install explicitly with adb:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
