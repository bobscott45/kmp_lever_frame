---
name: kmp-cross-platform
description: Guide the agy agent in building, testing, and troubleshooting Kotlin Multiplatform (KMP) logic across Android, JVM Desktop, and Native Linux targets. Trigger this skill when performing multi-platform builds, fixing platform-specific expect/actual structures, or debugging POSIX/C-interop elements.
argument_hint: [android | desktop | linux | all]
user_invocable: true
---

# Kotlin Multiplatform Build & Fix Skill (Android, Desktop, Linux)

This skill governs the automated verification, compilation, and cross-platform synthesis of shared codebases across JVM, Android SDK, and Native POSIX platforms.

## 1. Domain Boundaries & Target Architecture
Only execute tasks within the boundaries of these specific compilation paths:
*   **Android (`androidTarget`)**: Min SDK 24, Compile SDK 34. Leverages Android-specific libraries.
*   **Desktop (`jvm("desktop")`)**: JVM 17 Target execution runtime.
*   **Linux (`linuxX64`)**: Native target leveraging Kotlin/Native compilation and POSIX C-interop.

## 2. Mandatory Verification Workflows
When asked to build, test, or check code changes, follow these strict execution pathways:

### Step 1: Sequential Compilation Verification
Always execute tasks using the specific Gradle tasks rather than global builds to prevent cross-compilation target leakage.
1. Run `./gradlew check` to verify global common validation.
2. Run `./gradlew compileDebugKotlinAndroid` to catch Android variant syntax failures.
3. Run `./gradlew compileKotlinDesktop` to confirm JVM compilation.
4. Run `./gradlew compileKotlinLinuxX64` to validate LLVM native bindings and POSIX components.

### Step 2: Validating Expect / Actual Patterns
When modifying multiplatform definitions, verify compliance with structural declarations:
*   Every `expect` class or function declared in `src/commonMain/kotlin/` must have an explicit `actual` implementation in `src/androidMain/kotlin/`, `src/desktopMain/kotlin/`, and `src/linuxX64Main/kotlin/`.
*   Ensure **never** to expose native JVM types (like `java.io.*`) or Android types (like `android.content.Context`) inside `commonMain`.

## 3. Platform Execution Runbooks

### Android Sandbox Constraints
*   Do not attempt shell execution or direct process forks inside `androidMain`.
*   Wrap operations in Android platform channels or secure abstraction stubs.

### JVM Desktop Execution
*   Utilize standard `java.lang.Runtime` or `java.lang.ProcessBuilder` configurations for tool tasks.
*   Validate resource constraints and thread containment via standard Coroutines Dispatchers.

### Native Linux POSIX Interactions
*   Ensure explicit raw pointers or C-interop structures (e.g., `platform.posix.popen`) balance memory allocations.
*   Properly free allocated native buffers and systematically apply `pclose()` or `free()` bindings.

## 4. Expected Output Formats

### For Successful Structural Verifications:
```text
[kmp-cross-platform] Verification successful.
- Common Interface Alignment: Verified
- Android SDK Compilation: Validated (Target 34)
- Desktop JVM Compliance: Validated (JVM 17)
- Linux Native Linkage: Validated (POSIX x64)
```

### For Common Multiplatform Mismatches:
```text
[kmp-cross-platform] Error: Missing platform-specific implementation.
Target: linuxX64
Expected: expect class PlatformDevice in commonMain
Resolution: Ensure actual class PlatformDevice exists in src/linuxX64Main/kotlin/ with matching signatures.
```
