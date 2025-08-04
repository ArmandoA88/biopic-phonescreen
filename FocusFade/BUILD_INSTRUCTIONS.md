# Building FocusFade APK

## Prerequisites

1. **Android Studio** (recommended) or **Android SDK Command Line Tools**
2. **Java Development Kit (JDK) 8 or higher**
3. **Android SDK** with API level 24+ and build tools

## Method 1: Using Android Studio (Recommended)

1. **Open the Project**
   - Launch Android Studio
   - Click "Open an existing Android Studio project"
   - Navigate to the `FocusFade` folder and select it

2. **Sync the Project**
   - Android Studio will automatically sync Gradle files
   - Wait for the sync to complete (may take a few minutes on first run)

3. **Build the APK**
   - Go to `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - Or use the keyboard shortcut: `Ctrl+Shift+A` (Windows/Linux) or `Cmd+Shift+A` (Mac)
   - Type "Build APK" and select it

4. **Locate the APK**
   - Once built, click "locate" in the notification
   - Or find it at: `FocusFade/app/build/outputs/apk/debug/app-debug.apk`

## Method 2: Using Command Line

1. **Navigate to Project Directory**
   ```bash
   cd FocusFade
   ```

2. **Make Gradlew Executable** (Linux/Mac only)
   ```bash
   chmod +x gradlew
   ```

3. **Build Debug APK**
   ```bash
   # Windows
   gradlew.bat assembleDebug
   
   # Linux/Mac
   ./gradlew assembleDebug
   ```

4. **Locate the APK**
   - Find the APK at: `app/build/outputs/apk/debug/app-debug.apk`

## Method 3: Build Release APK (Signed)

For a release APK that can be distributed:

1. **Generate Signing Key** (first time only)
   ```bash
   keytool -genkey -v -keystore focusfade-release-key.keystore -alias focusfade -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create keystore.properties** (in project root)
   ```properties
   storePassword=YOUR_STORE_PASSWORD
   keyPassword=YOUR_KEY_PASSWORD
   keyAlias=focusfade
   storeFile=../focusfade-release-key.keystore
   ```

3. **Build Release APK**
   ```bash
   # Windows
   gradlew.bat assembleRelease
   
   # Linux/Mac
   ./gradlew assembleRelease
   ```

## Installation on Android Device

1. **Enable Unknown Sources**
   - Go to Settings → Security → Unknown Sources (enable)
   - Or Settings → Apps → Special Access → Install Unknown Apps

2. **Install APK**
   - Transfer the APK to your Android device
   - Open the APK file and follow installation prompts

3. **Grant Permissions**
   - Open FocusFade app
   - Grant "Display over other apps" permission when prompted
   - Optionally grant "Usage access" permission for app whitelisting

## Troubleshooting

### Common Issues:

1. **Gradle Sync Failed**
   - Check internet connection
   - Update Android Studio
   - Invalidate caches: File → Invalidate Caches and Restart

2. **SDK Not Found**
   - Install Android SDK through Android Studio
   - Set ANDROID_HOME environment variable

3. **Build Failed**
   - Check Java version (JDK 8+)
   - Update Gradle wrapper if needed
   - Clean and rebuild: `gradlew clean assembleDebug`

4. **Permission Denied (Linux/Mac)**
   - Make gradlew executable: `chmod +x gradlew`

### Build Variants:

- **Debug APK**: For testing, includes debugging info
- **Release APK**: Optimized for distribution, requires signing

### APK Locations:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Next Steps After Installation

1. Launch FocusFade
2. Grant overlay permission
3. Configure blur settings
4. Enable the service
5. Optionally set up app whitelisting
6. Enjoy mindful screen time management!

## Development Notes

- The app targets Android 7.0+ (API 24)
- Uses Material Design 3 components
- Requires overlay and usage stats permissions
- Built with Kotlin and modern Android architecture
