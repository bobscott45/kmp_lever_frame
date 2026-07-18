# Day-to-Day Versioning Workflow

With the single source of truth (`gradle/libs.versions.toml`) and automated syncing in place, here is how you should handle versions going forward.

## 1. Developing New Features
During active development, keep the version in `gradle/libs.versions.toml` marked with a `-dev` suffix. 

Example:
```toml
[versions]
app-version = "1.0.3-dev"
app-versionCode = "4"
```
*Because of our automated syncs, all platforms (Android, iOS, Desktop) will now automatically build and report the version as `1.0.3-dev`.*

## 2. Finalizing a Release
When you are ready to ship a release (e.g., v1.0.3):

1. **Remove the `-dev` suffix** in `gradle/libs.versions.toml`:
   ```toml
   [versions]
   app-version = "1.0.3"
   app-versionCode = "4"
   ```
2. **Sync the iOS Config**:
   Run the sync task to ensure the local iOS `Config.xcconfig` file has the updated release version strings for the git commit:
   ```bash
   ./gradlew syncIosVersions
   ```

## 3. Commit and Tag
Once the version file is updated and synced, commit the change and create an annotated Git tag.

```bash
git add gradle/libs.versions.toml iosApp/Configuration/Config.xcconfig
git commit -m "chore: Release v1.0.3"
git tag -a v1.0.3 -m "Release v1.0.3"
git push origin main --tags
```

## 4. Prepare for the Next Cycle (The "Bump")
Immediately after pushing the release, prepare the codebase for the next development cycle by incrementing the version and appending `-dev` again.

1. **Update `gradle/libs.versions.toml`**:
   ```toml
   [versions]
   app-version = "1.0.4-dev"
   app-versionCode = "5"
   ```
2. **Commit and Push**:
   ```bash
   git add gradle/libs.versions.toml
   git commit -m "chore: start v1.0.4-dev cycle"
   git push origin main
   ```

By following this workflow, your Git tags will perfectly match your app builds across all platforms, and you eliminate the risk of version mismatching!
