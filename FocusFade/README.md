# FocusFade

FocusFade is a minimalist Android app that helps users regain control of their screen time by applying a progressive blur overlay on their device the longer it's used. The longer the phone screen stays on, the blurrier the display becomes — gently encouraging the user to take breaks.

## 🌟 Features

### Core Functionality
- **Progressive Blur Overlay**: Screen gradually becomes blurrier with extended use
- **Dynamic Recovery**: Blur fades when screen is off, simulating focus recovery
- **Customizable Rates**: Adjust blur gain and recovery speeds
- **Daily Reset**: Automatic blur reset at customizable time (default: midnight)
- **Boot Reset**: Blur resets when device restarts

### Smart Features
- **App Whitelisting**: Exclude productive apps (Kindle, Duolingo, etc.) from blur
- **Smooth Transitions**: Proportional animation speeds based on blur changes
- **Battery Efficient**: Lightweight overlay with minimal resource usage
- **No Gamification**: Pure visual feedback without rewards or achievements

### Customization
- Blur gain rate (how fast blur increases)
- Blur recovery rate (how fast blur decreases)
- Minimum and maximum blur levels
- Daily reset time
- Whitelisted apps management

## 🏗️ Architecture

### Core Components

#### Managers
- **FocusStateManager**: Calculates blur levels and manages focus state
- **SettingsManager**: Handles user preferences with DataStore
- **WhitelistManager**: Manages app whitelisting and foreground detection

#### Services
- **BlurOverlayService**: Renders the blur overlay using WindowManager
- **ScreenTimeTrackingService**: Tracks screen on/off events

#### UI Components
- **BlurOverlayView**: Custom view that renders the blur effect
- **MainActivity**: Main configuration interface

#### Background Components
- **BootResetReceiver**: Handles device boot events
- **DailyResetReceiver**: Manages daily reset alarms
- **DailyResetScheduler**: Schedules reset alarms

## 🔧 Technical Details

### Permissions Required
- `SYSTEM_ALERT_WINDOW`: For overlay display
- `PACKAGE_USAGE_STATS`: To detect current app for whitelist behavior
- `RECEIVE_BOOT_COMPLETED`: Reset on phone restart
- `FOREGROUND_SERVICE`: For persistent operation

### Dependencies
- AndroidX Core, AppCompat, Material Design
- Kotlin Coroutines for async operations
- DataStore for settings persistence
- WorkManager for background tasks

### Minimum Requirements
- Android API 24 (Android 7.0)
- Overlay permission
- Usage stats permission (optional, for whitelisting)

## 🚀 Getting Started

### Building the Project
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on device or emulator

### First Launch
1. Grant overlay permission when prompted
2. Optionally grant usage stats permission for app whitelisting
3. Configure blur rates and daily reset time
4. Enable the service to start monitoring

### Usage
- The app runs in the background once enabled
- Blur increases gradually with screen time
- Blur decreases when screen is off
- Use the main app to adjust settings and view current status
- Reset blur manually anytime with the reset button

## ⚙️ Configuration

### Blur Settings
- **Gain Rate**: 1-60 minutes per 10% blur increase
- **Recovery Rate**: 1-60 minutes per 10% blur decrease
- **Max Blur Level**: 10-100% maximum blur intensity
- **Min Blur Level**: 0-90% minimum blur level

### Daily Reset
- Set custom time for daily blur reset
- Default: midnight (00:00)
- Automatically reschedules after each reset

### App Whitelisting
- Add productive apps to prevent blur accumulation
- Suggested apps include Kindle, Duolingo, productivity tools
- Requires usage stats permission

## 🎯 Design Philosophy

FocusFade follows minimalist design principles:
- **No Gamification**: No points, streaks, or achievements
- **Gentle Feedback**: Visual cues rather than harsh restrictions
- **User Control**: Full customization of behavior
- **Privacy First**: No tracking, ads, or data collection
- **Battery Conscious**: Efficient implementation

## 🔮 Future Enhancements

Potential features for future versions:
- Screen time analytics and graphs
- Scheduled focus sessions (Pomodoro mode)
- Emergency pause for navigation/maps
- Advanced blur patterns and effects
- Export/import settings

## 📱 Compatibility

- Tested on Android 7.0+ (API 24+)
- Works on phones and tablets
- Optimized for various screen sizes
- Material Design 3 theming

## 🤝 Contributing

This is a demonstration project showcasing Android development best practices:
- Clean architecture with separation of concerns
- Modern Android development with Kotlin and Coroutines
- Proper permission handling and background services
- Material Design UI/UX principles

## 📄 License

This project is created for educational and demonstration purposes.

## 🙏 Acknowledgments

Inspired by digital wellbeing principles and behavior design research from:
- BJ Fogg's behavior model
- Nir Eyal's habit formation research
- Thaler & Sunstein's nudge theory
- Apps like Forest, Digital Wellbeing, and Zen Mode
