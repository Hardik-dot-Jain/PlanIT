# PlanIT
*A minimalist, offline-first visual daily planner with fluid physics and a soft neo-brutalist aesthetic.*

I needed a zero-clutter, highly tactile way to time-block my day without getting distracted by a bloated UI. I couldn't find a planner that fit the aesthetic and fluid drag-and-drop feel I wanted, so I architected and shipped this offline-first Android application in a rapid 12-hour sprint.

## 🚀 Key Features

* **Soft Neo-Brutalism:** A strict, custom design system utilizing bold borders, hard drop shadows, pastel color palettes, and custom typography (Clash Display & Satoshi).
* **Tactile Drag-and-Drop:** Activities are dragged from a persistent dock onto a 24-hour timeline. Features a real-time "ghost block" drop preview and dynamic coordinate math that snaps to 15-minute increments.
* **Fluid Physics & Haptics:** Built with Compose `spring()` animations for physical card "squish" states, lift shadows, and perfectly tracked Y-offset dragging, paired with localized haptic feedback on grid snaps.
* **Rigid vs. Flexible Blocks:** Blocks are categorized as flexible (stretchable durations like "Gym") or rigid (fixed durations like "Lectures"). Rigid blocks programmatically disable resize gesture handles.
* **Exact Alarm Engine:** Integrates Android's `AlarmManager` (`setExactAndAllowWhileIdle`) and `BroadcastReceivers` to trigger precise OS-level notifications exactly when a scheduled block begins. Resilient to device reboots.

## 🛠 Tech Stack & Architecture

* **UI:** Jetpack Compose, Custom Canvas Layouts, Compose Spring Animations
* **Architecture:** MVVM (Model-View-ViewModel), Unidirectional Data Flow
* **Local Storage:** Room Database (SQLite), Kotlin Coroutines, StateFlow
* **Background Processing:** `AlarmManager`, `NotificationService`, `BroadcastReceiver` (Boot & Alarm)
* **Date & Time:** `java.time.LocalDate` and `LocalTime` for robust, timezone-aware temporal math.

## 🧠 Technical Challenges Overcome

**1. Timeline Coordinate Snapping**
Mapping a scrollable 24-hour timeline canvas to exact pixel offsets required custom Y-coordinate math. I implemented a `1.2.dp = 1 minute` scale, ensuring that `onDrag` delta pixels accurately translated into `LocalTime` mutations, complete with minimum-height clamps (`18.dp`) so short 15-minute tasks remained visually interactive.

**2. Gesture Resolution**
To prevent the scrollable timeline from consuming the drag events of the blocks, I implemented `detectDragGesturesAfterLongPress` for movement and isolated `detectDragGestures` for the bottom resize handles, achieving parity with Google Calendar's standard interaction mechanics.
