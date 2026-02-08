# Shipments

[![Android CI](https://github.com/dustinmcafee/AndroidAssignmentProblem/actions/workflows/android.yml/badge.svg?branch=master)](https://github.com/dustinmcafee/AndroidAssignmentProblem/actions/workflows/android.yml)

Android app that optimally assigns drivers to shipments using the Assignment Problem Solution algorithms.

Given a list of drivers and shipment addresses, the app computes a suitability score for every possible driver-shipment pair, then finds the 1-to-1 assignment that maximizes the total score.

## Suitability Score

For a given driver and shipment address:

1. Extract the street name (drop the leading house number, strip trailing Suite/Apt)
2. If the street name length is **even**: base score = (vowels in driver name) * 1.5
3. If the street name length is **odd**: base score = (consonants in driver name) * 1.0
4. If the street name length and driver name length share a common factor > 1: multiply by 1.5

## Assignment Algorithms

The app includes five implementations of the assignment problem solver, compared against each other in unit tests:

| Algorithm                                | Complexity | Source                                   |
| ---------------------------------------- | ---------- | ---------------------------------------- |
| Jonker-Volgenant (Dijkstra + potentials) | O(n^3)     | Custom implementation                    |
| JGraphT Kuhn-Munkres (Edmonds-Karp 1972) | O(n^3)     | [jgrapht-core](https://jgrapht.org/)     |
| Bellman-Ford successive shortest paths   | O(n^4)     | Custom + JGraphT shortest path           |
| Classic Hungarian (Kuhn 1955)            | O(n^4)     | Custom implementation                    |
| Brute force (all permutations)           | O(n!)      | [Guava](https://github.com/google/guava) |

The app uses Jonker-Volgenant for production. The others exist for educational comparison and test validation.

## Build

Requires JDK 17 and Android SDK.

```
./gradlew assembleDebug
```

## Test

```
./gradlew test
```

Tests compare all five solvers against each other on 3x3, 10x10, 50x100, and 100x100 matrices. Test output includes ASCII art visualizations of augmenting paths and final assignments.

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- AndroidViewModel + StateFlow
- Gson for JSON parsing
- JGraphT for graph algorithms (Bellman-Ford, Kuhn-Munkres)
- Guava for brute force permutations
- JUnit 4 for unit tests
- GitHub Actions CI/CD

## Project Structure

```
app/src/main/java/com/shipments/app/
  MainActivity.kt
  model/
    AssignmentProblemSolver.kt    # Five solver implementations
    SuitabilityScoreCalculator.kt # Scoring logic
    data/
      ShipmentData.kt             # JSON model
      DriverAssignment.kt         # Assignment result model
  viewmodel/
    ShipmentsViewModel.kt         # Loads data, runs solver
  ui/
    ShipmentsApp.kt               # Navigation (state-based)
    DriverListScreen.kt           # Main screen
    DriverDetailScreen.kt         # Driver detail screen

app/src/test/java/com/shipments/app/model/
  AssignmentProblemSolverTest.kt  # Solver comparison tests
```
