# FocusFade Project Structure

## Overview
This document outlines the structure and organization of the FocusFade Android application.

## Directory Structure

```
FocusFade/
├── app/
│   ├── build.gradle                    # App-level build configuration
│   └── src/main/
│       ├── AndroidManifest.xml         # App manifest with permissions and components
│       ├── java/com/focusfade/app/
│       │   ├── MainActivity.kt          # Main app interface
│       │   ├── SettingsActivity.kt     # Settings configuration
│       │   ├── WhitelistActivity.kt    # App whitelisting interface
│       │   ├── adapter/
│       │   │   └── AppListAdapter.kt    # RecyclerView adapter for app lists
│       │   ├── manager/
│       │   │   ├── FocusStateManager.kt # Core blur logic and state management
│       │   │   ├── SettingsManager.kt   # User preferences with DataStore
│       │   │   └── WhitelistManager.kt  # App whitelisting and detection
│       │   ├── receiver/
│       │   │   ├── BootResetReceiver.kt      # Boot event handling
│       │   │   ├── DailyResetReceiver.kt     # Daily reset alarm handling
│       │   │   └── DailyResetScheduler.kt    # Alarm scheduling utility
│       │   ├── service/
│       │   │   ├── BlurOverlayService.kt     # Main foreground service
│       │   │   └── ScreenTimeTrackingService.kt # Screen state tracking
│       │   └── view/
│       │       └── BlurOverlayView.kt   # Custom blur rendering view
│       └── res/
│           ├── drawable/                # Vector icons and graphics
│           │   ├── ic_expand_more.xml
│           │   ├── ic_notification.xml
│           │   ├── ic_refresh.xml
│           │   └── ic_stop.xml
│           ├── layout/                  # UI layouts
│           │   ├── activity_main.xml
│           │   ├── activity_settings.xml
│           │   ├── activity_whitelist.xml
│           │   └── item_app.xml
│           └── values/                  # Resources and themes
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
├── build.gradle                        # Project-level build configuration
├── gradle.properties                   # Gradle properties
├── settings.gradle                     # Gradle settings
├── README.md                           # Project documentation
└── PROJECT_STRUCTURE.md               # This file
```

## Key Components

### Core Architecture

#### Managers
- **FocusStateManager**: Singleton that manages blur calculations, screen state, and focus logic
- **SettingsManager**: Handles all user preferences using DataStore for persistence
- **WhitelistManager**: Manages app whitelisting and foreground app detection using UsageStatsManager

#### Services
- **BlurOverlayService**: Main foreground service that renders the blur overlay using WindowManager
- **ScreenTimeTrackingService**: Tracks screen on/off events using BroadcastReceiver

#### UI Components
- **MainActivity**: Primary user interface with settings controls and status display
- **SettingsActivity**: Advanced settings configuration
- **WhitelistActivity**: App whitelisting management with suggested and all apps
- **BlurOverlayView**: Custom view that renders the progressive blur effect

#### Background Components
- **BootResetReceiver**: Handles device boot events and service restart
- **DailyResetReceiver**: Processes daily reset alarms
- **DailyResetScheduler**: Utility for scheduling and managing daily reset alarms

### Data Flow

1. **Screen Events**: ScreenTimeTrackingService detects screen on/off
2. **State Updates**: FocusStateManager calculates blur levels based on usage
3. **Blur Rendering**: BlurOverlayService updates BlurOverlayView with new blur level
4. **App Detection**: WhitelistManager checks foreground apps and pauses blur if whitelisted
5. **Settings**: SettingsManager persists user preferences and notifies components of changes

### Permissions

- `SYSTEM_ALERT_WINDOW`: Required for overlay display
- `PACKAGE_USAGE_STATS`: Optional, for app whitelisting functionality
- `RECEIVE_BOOT_COMPLETED`: For automatic service restart after reboot
- `FOREGROUND_SERVICE`: For persistent background operation

### Key Features Implementation

#### Progressive Blur
- BlurOverlayView uses multiple layered rectangles to simulate blur effect
- Smooth animations with proportional duration based on blur level changes
- Efficient rendering with pre-calculated blur rectangles

#### Customizable Behavior
- Blur gain/recovery rates configurable via sliders
- Min/max blur levels with validation
- Daily reset time with alarm scheduling
- App whitelisting with suggested productivity apps

#### Battery Optimization
- Lightweight overlay rendering without intensive graphics
- Efficient coroutine usage for background operations
- Minimal resource usage with optimized update intervals

## Build Configuration

- **Target SDK**: 34 (Android 14)
- **Min SDK**: 24 (Android 7.0)
- **Kotlin**: Modern Android development with coroutines
- **Material Design 3**: Contemporary UI components and theming
- **DataStore**: Modern preference storage replacing SharedPreferences

## Development Notes

- Clean architecture with separation of concerns
- Reactive programming with Kotlin Flows
- Proper lifecycle management and memory leak prevention
- Comprehensive error handling and permission management
- Material Design guidelines compliance
