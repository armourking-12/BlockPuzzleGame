# BlockPuzzleGame

BlockPuzzleGame is a Kotlin-based Android puzzle game inspired by classic block-fitting mechanics. The player places colorful shapes on a grid, clears complete rows and columns, and aims to maximize their score before the board becomes unsolvable.

Built with Jetpack Compose and a lightweight custom game engine, the project focuses on smooth drag-and-drop gameplay, responsive scoring, game-over detection, and persistent settings/high-score tracking.

## Features

- Drag-and-drop shape placement on a square grid
- Hold-box system for temporarily storing a piece
- Double-tap rotate controls for tray and hold shapes
- Row and column line clearing with score multipliers
- High-score persistence using DataStore
- Game-over warning countdown before the round ends
- Adjustable gameplay settings:
  - haptic feedback toggle
  - easy-shape generation toggle
  - visual color palette selection
- Compose-based modern Android UI
- Save-and-resume game progress with local persistence

## Gameplay Overview

Each round gives the player a set of three shapes. The goal is to place them efficiently onto a grid so that full rows or columns are completed. Clearing lines awards points and can trigger bonus scoring when multiple lines are removed at once.

If the board fills up and no remaining shapes can be placed, the game triggers a grace-period countdown before ending the round.

## Tech Stack

- Kotlin
- Android Jetpack
- Jetpack Compose
- Android ViewModel
- DataStore Preferences
- Gradle Kotlin DSL

## Project Structure

```text
BlockPuzzleGame/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/duddletech/blockpuzzlegame/
│   │   │   │   ├── data/
│   │   │   │   ├── logic/
│   │   │   │   ├── model/
│   │   │   │   ├── ui/
│   │   │   │   ├── viewModel/
│   │   │   │   └── MainActivity.kt
│   │   │   └── res/
│   │   ├── androidTest/
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── .gitignore

```
## Screenshots

<p align="center">
  <img width="1080" height="2400" alt="gameplay1" src="https://github.com/user-attachments/assets/e77852c1-1c2b-42f8-aa2f-6a34458c6db3" />
  <img width="1080" height="2400" alt="gameplay2" src="https://github.com/user-attachments/assets/c8933a4e-6e38-4472-94c4-1be176353486" />
  <img width="1080" height="2400" alt="gameplay3" src="https://github.com/user-attachments/assets/881ae31c-3e37-41ed-8364-8d67e383d28c" />
  <img width="1080" height="2400" alt="gameplay4" src="https://github.com/user-attachments/assets/b3705a26-3b86-4257-9bdb-f70b2154bb7e" />
  <img width="1080" height="2400" alt="gameplay5" src="https://github.com/user-attachments/assets/08da6e7c-991d-4902-8605-d9d6d3cb9baa" />

</p>

<p align="center">
  <em>Drag-and-drop shapes, clear lines, and chase your high score!</em>
</p>
