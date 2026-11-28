# Luna for Android

Luna is a native Android companion for adults. This directory is a separate Android Studio project. The original Soulmate AI project lives one directory above it.

## First installation for a beginner

1. On a computer install Android Studio with the Android SDK and its bundled JDK. A 64-bit ARM Android phone running Android 9 or newer is required for this build.
2. In GitHub select the **luna-compose-offline** branch, click **Code → Download ZIP**, then extract it. In Android Studio select **Open**, choose the extracted **luna** directory containing settings.gradle.kts, and trust the project.
3. In **Tools → SDK Manager**, install Android SDK Platform 36. Under SDK Tools enable **Show Package Details** and install NDK 28.2.13676358 and CMake 3.22.1. Set Gradle JDK to 17 or the compatible bundled JDK. Allow Gradle sync to complete. Build tools and dependencies come from their normal upstream registries. CMake fetches llama.cpp tag b5046 from GitHub.
4. On your phone open **Settings → About phone** and tap **Build number** seven times. Under **Developer options** turn on USB debugging. Connect your phone by USB, unlock it, and allow this computer. In Android Studio choose the phone and click the green Run triangle. This installs a debug-signed build.
5. Confirm your age, choose your companion name, avatar, language, personality, and conversation style. In **Settings**, choose a model. Compact (Q4_0) is approximately 429 MB and Balanced (Q4_K_M) approximately 491 MB. Allow an extra 40 MB for installation, plus space for any previous model during replacement. GGUF is already quantized. The app streams directly to temporary storage and checks the publisher SHA-256.
6. Use **Download from GitHub** after the maintainer has populated the model release. Alternatively use **Import verified GGUF** and pick the matching official file already on your phone. Import temporarily needs enough storage for the original and app-private copy. Switch to airplane mode, open Chat and send a short message. Stop cancels local generation.
7. On a low-memory phone, close other apps and use Compact. During generation the model needs working RAM beyond its file size. The app checks available RAM and uses 2 threads/768 context tokens or 4 threads/1536 tokens. It releases the model after each reply to conserve idle RAM, at the cost of slower first tokens. No permanent service runs. On devices without sufficient RAM the local journal and memory screens still work.

## Model distribution

Use a public release tagged **luna-models-v1** in founder-dax-limbasiya/Soulmate-AI. The phone contacts only GitHub for downloads. The upstream models are Qwen's official Qwen2.5-0.5B-Instruct GGUF conversions. Do not substitute similarly named third-party quantizations. Publish these exact weights, the included Apache license, and the publisher attribution in app/src/main/assets/NOTICE.txt. Verify files with sha256sum before upload.

| Filename | SHA-256 |
|---|---|
| qwen2.5-0.5b-instruct-q4_0.gguf | 7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed |
| qwen2.5-0.5b-instruct-q4_k_m.gguf | 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db |

## Project layout

- app/src/main/java/studio/luna/MainActivity.kt — Compose screens, companion settings, streaming display and cancellation.
- Local.kt — gzip-compressed SQLite journal, extractive summaries, checksum-verified install and local model selection.
- Device.kt — on-device dictation, installed offline voices, optional one-time local reminder.
- app/src/main/cpp — JNI bridge and CMake build. The engine samples actual model tokens. Model weights are separate from the APK.
- web — Vercel website, privacy page and optional reporting endpoint. No chat traffic reaches Vercel. Browser preview settings are independent of phone settings.
- Repository root .github/workflows/luna-android.yml — builds a debug APK and unsigned release artifacts, checks the release APK is below 30,000,000 bytes and excludes weights.

## Local memory and safety

Users explicitly save personal facts in Memories. Older messages are periodically replaced with short excerpts of user turns. Device-local SQLite records use gzip and Android app sandbox protection. Compression is not encryption. Reset memories removes saved notes and summaries. Delete conversation removes messages and summaries. Disable cloud backup, as configured. The 18+ gate is self-attestation. The system prompt directs the model to keep interactions non-explicit, avoid coercion and encourage healthy boundaries. A small model can ignore directions. Test restricted-content handling across all four languages before public launch. In-app reporting transmits a selected category only, and the developer must monitor Vercel runtime logs within the plan's retention period. There is no mandatory backend for chat.

## Release signing and Google Play

1. Set a unique permanent applicationId before your first Play upload. Increment versionCode on every subsequent release.
2. From Android Studio use **Build → Generate Signed App Bundle or APK**. Choose Android App Bundle for Play or APK for direct installation, select app, then **Create new** under key store. Keep the upload keystore and its passwords backed up privately outside the repository. Select release and finish. The wizard signs the artifact. Terminal builds with ./gradlew :app:assembleRelease :app:bundleRelease create unsigned distribution artifacts until you sign them.
3. Inspect the release APK in Android Studio APK Analyzer. Check its actual size, native 16 KB page compatibility and ABI, then test the signed build on physical phones. Check offline generation, multilingual quality, cancellation, download interruption, storage exhaustion, voice behavior, reporting and deletion. Test with TalkBack and both color schemes.
4. In Play Console create the app, enable Play App Signing, upload the signed AAB to an internal test track, and complete account verification and applicable testing requirements. Fill in Data safety, content rating, target audience, AI disclosures, privacy policy and support contact. Declare the adult audience and any metadata handled by reporting. Verify current Play requirements and test report handling before production submission. A new Play developer account costs US$25 once. Store approval is a separate review.

## Hosting and cost

The website is at https://luna-companion-dax.vercel.app. To redeploy, use the web directory as the Vercel project root with the Other framework preset. No inference service, counters, database, paid API, or API keys are required. GitHub provides source and model distribution; Vercel hosts the site and category-only reporting. The Android build pulls ordinary SDK/Gradle dependencies at development time. Vercel Hobby is for personal/non-commercial use and subject to limits. Download data, device electricity, developer hardware and optional Play registration are separate costs. No perpetual free-hosting guarantee applies.
