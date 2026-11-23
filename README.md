# Real World GTA

An early Android-first prototype for a real-world open-world driving game.

## Current prototype

- Android APK wrapper using the native Android WebView.
- Lightweight 3D driving scene built with Three.js.
- Search for a real city and load nearby OpenStreetMap roads and building footprints.
- Touch driving controls and keyboard controls for desktop browser testing.
- Simple drive-to-marker jobs and prototype in-game credits.
- No Vercel deployment.

## Build the APK

Every push to `main` starts the **Build Android APK** GitHub Actions workflow. You can also run it manually:

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Press **Run workflow**.
4. Open the completed run and download the `real-world-gta-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on an Android device.

This is a debug APK for testing, not a signed Play Store release.

## Important prototype limitations

This is a first playable foundation, not a finished GTA-scale game. It currently has basic driving, streamed city geometry and a tiny mission loop. Character movement, traffic AI, pedestrians, police systems, save profiles, multiplayer, optimized streaming and a production-grade physics system are not implemented yet. Map loading needs an internet connection and may be limited by public OpenStreetMap/Nominatim/Overpass service availability. The prototype requests a small map area to keep mobile loading manageable.

## Map data

© OpenStreetMap contributors. Map data is available under the Open Database License (ODbL). This prototype uses public Nominatim and Overpass endpoints; please use them responsibly and respect their usage policies. See https://www.openstreetmap.org/copyright and https://operations.osmfoundation.org/policies/.

## Next milestones

1. Verify APK on a real Android phone and fix device-specific WebGL/input issues.
2. Replace the simple vehicle with improved vehicle physics and add a walkable character.
3. Stream map tiles and city chunks with caching and performance budgets.
4. Add traffic, pedestrians, police response, mission progression and save data.
5. Plan desktop and iOS builds from the shared game layer.
