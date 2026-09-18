# Project Error Log & Resolution Guide

This document outlines the current errors found in the **BlockPuzzleGame** project, the reasons behind them, and the recommended solutions.

---

## 1. Global Issue: Package Name Mismatch
**Files affected:** `Shape.kt`, `ShapeTemplates.kt`, `ShapeTemplate.kt`

*   **Error:** Many files report "Unresolved reference" for `Shape`, `CellOffset`, or `ShapeTemplates` even though the files exist.
*   **Why:** The package declared at the top of these files is `package com.blockpuzzle.model`, but the project structure is `package com.duddletech.blockpuzzlegame.model`. The compiler cannot find these classes because they are in the "wrong" namespace relative to the folder structure.
*   **Solution:** Change the package declaration in these files to:
    ```kotlin
    package com.duddletech.blockpuzzlegame.model
    ```
    Then, update all import statements in the rest of the project to match.

---

## 2. Dependency Management (`libs.versions.toml`)
**Files affected:** `app/build.gradle.kts`, `GameStateRepository.kt`, `SettingsRepository.kt`, `HighScoreRepository.kt`

*   **Error:** `Unresolved reference: datastore`, `Unresolved reference: viewmodel`, etc.
*   **Why:** Several libraries used in the code are not defined in the Version Catalog (`gradle/libs.versions.toml`).
*   **Solution:** Add the following to `gradle/libs.versions.toml`:
    ```toml
    [libraries]
    androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version = "1.1.2" }
    androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
    androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
    ```
    After adding these, run a **Gradle Sync**.

---

## 3. `HighScoreRepository.kt` Syntax Error
*   **Error:** `Expecting a top level declaration`.
*   **Why:** The file has the `package` declaration repeated twice at the top.
*   **Solution:** Remove the redundant `package com.duddletech.blockpuzzlegame.data` line.

---

## 4. `GameStateRepository.kt` JSON Serialization
*   **Error:** Type mismatches in `JSONObject` constructors and `prefs` indexing.
*   **Why:**
    1.  The DataStore `edit` block requires the correct key type (e.g., `stringPreferencesKey`).
    2.  `JSONObject(jsonStr)` is being called on a `MatchGroup` or incorrect type in one of the lambda expressions.
*   **Solution:**
    *   Ensure `KEY_SAVED_GAME` is correctly defined using `stringPreferencesKey`.
    *   Ensure the JSON string is properly retrieved from DataStore before passing it to the `JSONObject` constructor.

---

## 5. `GameEngine.kt` Logic & Inference
*   **Error:** `Cannot infer type for type parameter T`.
*   **Why:** Because `Shape` and `CellOffset` are unresolved (due to the package issue), Kotlin cannot infer the types of lists or sets containing them (e.g., `buildSet { ... }`).
*   **Solution:** This will be automatically fixed once the **Package Name Mismatch (Issue #1)** is resolved.

---

## 6. Formatting & Best Practices
*   **Error:** `Missing trailing comma`.
*   **Why:** Modern Android/Compose style guides prefer trailing commas for better git diffs and easier reordering of parameters.
*   **Solution:** Add trailing commas to data class properties and function arguments in `Cell.kt`, `GameState.kt`, etc.

---

## Summary of Action Plan
1.  **Correct Packages:** Fix package names in the `model` folder.
2.  **Update Dependencies:** Add missing items to `libs.versions.toml`.
3.  **Gradle Sync:** Sync the project to apply dependency changes.
4.  **Clean Code:** Remove the duplicate package line in `HighScoreRepository.kt`.
5.  **Refactor Imports:** Use "Optimize Imports" (Ctrl+Alt+O) across the project to fix broken references.
