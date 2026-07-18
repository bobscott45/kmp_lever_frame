# Xcode Version Sync Configuration

Follow these steps when you are ready to configure Xcode to automatically sync its version on macOS:

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. In the Project Navigator (left sidebar), click on the top-level **iosApp** project.
3. In the main window, select the **iosApp Target** (under "TARGETS", not "PROJECT").
4. Go to the **Build Phases** tab.
5. Click the `+` icon at the top-left of the Build Phases pane and select **"New Run Script Phase"**.
6. Rename the newly created phase to `"Sync KMP Versions"`.
7. **Crucial:** Click and drag this new phase so it runs **before** the "Compile Sources" phase.
8. In the script box, paste the following script:

```bash
cd "$SRCROOT/.."
./gradlew syncIosVersions
```

This ensures that Xcode will invoke the Gradle task you've set up every time you build, injecting the correct version natively.
