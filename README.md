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
