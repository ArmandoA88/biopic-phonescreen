# Manual Blur Functionality - FocusFade

## Overview
The manual blur feature allows users to precisely control the blur level by dragging the grey progress bar in the "Current Status" section of the app.

## How It Works

### 1. Activation
- **Method 1**: Tap the "Manual Blur" button to activate manual mode
- **Method 2**: Start dragging the grey progress bar - this automatically activates manual mode

### 2. Precise Control
- **Drag Sensitivity**: 30 pixels of horizontal movement = 1% blur change (improved sensitivity)
- **Increments**: Blur level changes in precise 1% increments
- **Range**: 0% to 100% blur level
- **Direction**: 
  - Drag RIGHT = Increase blur
  - Drag LEFT = Decrease blur
- **Touch Handling**: Enhanced touch event processing with proper parent view coordination
- **User Guidance**: Toast notification shows "Drag left/right to adjust blur level" when starting

### 3. Visual Feedback
- **Progress Bar Color**: Changes to orange when manual mode is active
- **Progress Bar Size**: Becomes taller (24dp) for easier dragging when in manual mode
- **Custom Drawable**: Uses rounded corners and enhanced visual design
- **Progress Bar**: Updates in real-time as you drag
- **Text Display**: Shows "Current Blur: X% (Manual - Drag left/right ↔)" with clear instructions
- **Button State**: "Manual Blur" button changes to "Exit Manual" with orange background
- **Screen Overlay**: Blur effect applies to the screen overlay instantly
- **Toast Guidance**: Shows "Drag left/right to adjust blur level" when first starting to drag

### 4. Technical Implementation

#### MainActivity Touch Handler
```kotlin
progressBlurLevel.setOnTouchListener { ... }
```
- Detects horizontal drag movements on the orange progress bar
- Calculates blur percentage based on horizontal drag distance
- Rounds to nearest 1% for precise control
- Updates UI immediately for responsive feedback
- Sends commands to BlurOverlayService

#### BlurOverlayService Integration
- Receives `ACTION_SET_MANUAL_BLUR` commands
- Applies blur level to screen overlay
- Pauses automatic blur accumulation during manual mode
- Updates notification with current manual blur level

### 5. User Experience
1. **Start**: Drag the orange progress bar horizontally (or tap "Manual Blur" button)
2. **Guidance**: Toast notification shows "Drag left/right to adjust blur level"
3. **Control**: Move finger left/right for precise 1% adjustments
4. **Feedback**: See immediate visual changes in progress bar and text
5. **Apply**: Blur overlay updates in real-time on screen
6. **Persist**: Manual mode stays active until reset or "Exit Manual" button is pressed

### 6. Exit Manual Mode
- **Exit Manual Button**: Click "Exit Manual" button to disable manual mode and return to automatic
- **Reset Button**: Returns blur to 0% and exits manual mode
- **Service Restart**: Automatically exits manual mode
- **Automatic Mode**: Resumes normal blur accumulation when manual mode is disabled

### 7. Progress Bar Behavior
- **Automatic Mode**: Progress bar fills automatically as blur increases over time
- **Manual Mode**: Progress bar responds immediately to dragging gestures
- **Visual Sync**: Bar position always matches the current blur percentage
- **Smooth Updates**: Real-time visual feedback during both automatic and manual control

## Benefits
- **Precise Control**: Exact 1% increments for fine-tuning
- **Immediate Feedback**: Real-time visual updates
- **Intuitive Interface**: Natural drag gesture on the progress bar
- **No Overlay Clutter**: Control stays within the app interface
- **Responsive**: Smooth, lag-free interaction

## Technical Notes
- Manual mode automatically pauses automatic blur accumulation
- Blur level is rounded to nearest integer for clean display
- Touch sensitivity optimized for comfortable control
- Service communication ensures overlay updates immediately
- Progress bar serves dual purpose: display and control interface
