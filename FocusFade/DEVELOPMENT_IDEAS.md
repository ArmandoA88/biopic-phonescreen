# FocusFade - Future Feature Ideas

This document contains a backlog of potential features to enhance FocusFade's ability to reduce phone usage.

---

## 1. Visual Distraction Techniques

- **Color Shift / Dim Overlay** *(Implementing First)*  
  Gradually shift the entire screen toward grayscale or dimmer tones as screen time increases.

- **Overlay Patterns**  
  Display light static or animated patterns on the screen to make prolonged focus uncomfortable without making it unusable.

- **Timers & Warnings**  
  Use an animated countdown or shrinking visible area to signal upcoming lockouts.

---

## 2. Interactive "Break" Prompts

- **Mandatory Micro-Tasks**  
  Before continuing after a set limit, require quick tasks such as solving a simple puzzle, taking a breath exercise, or performing finger stretches.

- **Mindfulness Prompts**  
  Pop up random reminders about posture, eye strain, or deep-breathing exercises.

---

## 3. Positive Reinforcement Features

- **Daily Challenge Tracker**  
  Encourage daily limits with streaks and rewards for staying under the set time.

- **Motivational Quotes**  
  Replace some overlays with uplifting messages tied to focus goals.

- **Progress Visualization**  
  Show charts over the day of their screentime trend, with clear rewards for improvement.

---

## 4. Restrictive Modes

- **Greyscale OS Integration**  
  Temporarily force the screen into grayscale mode (requires API 29+ and special permissions).

- **Touch Delay**  
  Add a slight artificial delay to touch input after long continuous use, making interacting less tempting.

- **Partial Screen Lock**  
  Allow only certain whitelisted sections of the screen to be tappable.

---

## 5. Gamification for Reducing Use

- **Focus Currency**  
  Earn virtual coins for staying off the phone, redeemable for in-app themes or extras.

- **Level System**  
  Increase a personal “focus level” when avoiding overuse, unlocking badges.

- **Community Integration**  
  Optionally compare progress with friends in a leaderboard for motivation.

---

## 6. Time-Based or Context-Based Interventions

- **App Lock Scheduling**  
  Block certain apps during work hours or bedtime automatically.

- **Geo-based Focus Mode**  
  Activate more aggressive blockers in specific places (e.g., school, office).

- **AI Curfew Detection**  
  After late-night usage, progressively make the phone harder to use by increasing blur, dimming, or locking certain apps.

---

## Implementation Notes for First Feature: Color Shift / Dim Overlay

- Extend `BlurOverlayService` to include a **color filter overlay** mode.  
- Use a full-screen `View` with a semi-transparent black layer for dimming.  
- For grayscale, apply a `ColorMatrixColorFilter` to the overlay's paint.  
- Integrate with `FocusStateManager` to gradually intensify the effect over time (similar to blur level).  
- Add settings to toggle between blur and color shift dim modes.  
- Ensure compatibility with **TYPE_APPLICATION_OVERLAY** for overlays.  
- Update service actions to handle mode switching.
