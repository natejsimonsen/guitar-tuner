# Guitar Tuner

A minimal Android guitar tuner app. No external dependencies — pure Android SDK + Kotlin.

## Features
- Real-time pitch detection via YIN algorithm
- Displays note name, frequency, and cents deviation
- Color-coded tuning bar (green ±5¢, yellow ±5–15¢, red beyond)
- Works across full guitar range (E2 82 Hz – E4 330 Hz and beyond)
- APK under 100KB

## Standard Tuning Reference
| String | Note | Frequency |
|--------|------|-----------|
| 6 | E2 | 82.41 Hz |
| 5 | A2 | 110.00 Hz |
| 4 | D3 | 146.83 Hz |
| 3 | G3 | 196.00 Hz |
| 2 | B3 | 246.94 Hz |
| 1 | E4 | 329.63 Hz |

## Requirements
- Android 6.0+ (API 23+)
- Microphone permission

## Build
Open in Android Studio and run, or:
```
./gradlew assembleDebug
```
