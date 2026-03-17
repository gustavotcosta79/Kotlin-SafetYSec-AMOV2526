# SafetYSec - Mobile Safety & Monitoring System

## 📝 Overview
**SafetYSec** is a native Android application developed in **Kotlin** designed to provide protection and safety for vulnerable users (the "Protected") through remote monitoring by caregivers (the "Monitors"). The system enables automatic accident detection, real-time GPS tracking, and forensic evidence collection to ensure user well-being.

## 🏗️ Technical Architecture
The application follows the **MVVM (Model-View-ViewModel)** architectural pattern to ensure a clean separation between business logic and the user interface:

* **View (UI)**: Built entirely with **Jetpack Compose**, featuring a dynamic and responsive design that supports both Portrait and Landscape orientations.
* **ViewModel**: Manages the UI state and business logic, leveraging **Kotlin Coroutines** and **Flows** for efficient asynchronous task management and real-time data streaming.
* **Model/Data**: Utilizes **Firebase** (Firestore, Authentication, and Cloud Storage) for centralized data persistence and real-time synchronization between users.

## 🚀 Key Technical Features

### 1. Advanced Event Detection (Sensors)
Implemented a custom `SensorManager` that processes accelerometer data in real-time to detect critical events:
* **Fall Detection**: A sequential algorithm that identifies a near-zero G free-fall followed by a high-G impact.
* **Road Accident Detection**: Identifies violent G-force impacts (exceeding 15G) typical of vehicular collisions.

### 2. Location & Geofencing
* **FusedLocationProvider**: High-precision GPS tracking with optimized energy efficiency.
* **Virtual Fences (Geofencing)**: Automatically triggers alerts if a "Protected" user leaves predefined safety zones calculated via geodesic distance.

### 3. Forensic Evidence Collection
* **CameraX Integration**: Upon an alert, the app automatically triggers a **30-second background video recording**.
* **Evidence Storage**: Videos are compressed and uploaded to Firebase Storage, with direct links attached to the incident report in Firestore for the Monitor to review.

### 4. Privacy & Security
* **Monitoring Windows**: Users can define specific time intervals and days for authorized monitoring to ensure privacy outside of care hours.
* **Multi-Factor Authentication (MFA)**: Secure access using Firebase Auth and a simulated MFA code system.
* **One-Time Password (OTP)**: Secure association mechanism between Monitors and Protected users.

## 🛠️ Tech Stack
* **Language**: Kotlin
* **UI**: Jetpack Compose
* **Backend**: Firebase (Firestore, Auth, Storage)
* **Asynchrony**: Kotlin Coroutines & Flows
* **Hardware APIs**: CameraX, Sensors API, Google Play Services (Location)
* **Localization**: Full support for English and Portuguese.

---
*Developed by Gustavo Costa and Duarte Santos as part of the Mobile Architectures course (2025/2026).*
