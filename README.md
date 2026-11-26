# Soulmate AI

Soulmate AI is an Android-first, on-device AI companion app for fictional adult characters.

## How AI works

- **Inference runs on each user's own Android phone.** There is no Soulmate AI cloud inference server, Oracle VM, API key or account needed.
- The APK includes the app, UI and LiteRT-LM runtime, but **does not bundle model weights**.
- On first setup, the user explicitly downloads the open-source Qwen3-0.6B model (about 328 MiB) from a pinned Hugging Face revision into app-private phone storage.
- The app calculates a SHA-256 checksum while downloading and saves the model only after the full download passes the minimum-size check.
- After the one-time model download, chats run offline. Chat text, character prompts and personal notes stay on the phone.
- Internet permission is used only for the user-initiated model download. No remote chat inference endpoint is configured.

## APK size target

- Target distributable APK size: **50,000,000 bytes maximum**.
- The compact build targets modern **ARM64 Android phones** and excludes emulator-only native ABIs.
- R8 code shrinking and Android resource shrinking are enabled for debug and release builds.
- The 328 MiB model is intentionally downloaded separately into app-private storage. It is not part of the APK; the app cannot generate AI replies until the model has been downloaded and loaded.
- CI checks APK size, confirms model weights are absent, and verifies that only ARM64 native libraries are packaged.

## Android app

- Editorial-style WebView UI with Discover, Chats, Create Character and Settings.
- Self-declared 18+ entry gate; this is not identity or age verification.
- Fictional adult character profiles and user-created characters.
- AES-GCM encrypted local state, with the key protected by Android Keystore.
- LiteRT-LM 0.19.1 on-device inference using the CPU backend.
- The model is saved under app-private storage and remains installed when local chat data is cleared.

## First-run requirements

- Internet access is needed once to download approximately 328 MiB. Wi-Fi is recommended.
- Keep at least about 410 MiB of free storage for the download and installation.
- Keep the app open during download. A failed or incomplete download is discarded and can be retried.
- Model loading needs substantial free RAM; on-device performance varies by chipset and available memory. Some lower-memory phones may not be able to load the model reliably.
- Once downloaded and loaded, chatting does not need Internet.

## Build Android APK

GitHub Actions builds a minified ARM64 debug APK with Java 17 and Gradle 8.9. The build fails if the APK exceeds 50,000,000 bytes or if any model weights are packaged in the APK.

## Open-source components

- LiteRT-LM Android runtime: https://github.com/google-ai-edge/LiteRT-LM
- Qwen3-0.6B LiteRT model: https://huggingface.co/litert-community/Qwen3-0.6B
- Apache-2.0 upstream model license.

## Privacy and limitations

- Conversation history, user-created characters and personal notes are stored locally; chat state is AES-GCM encrypted.
- No account system, cloud chat sync, voice, payments or analytics backend is connected.
- This is an early beta. The small 0.6B model is a lightweight local companion model, not a frontier-scale model; reply quality and speed depend on the handset.
- The model is not advertised as an unrestricted or 'uncensored' model. Its responses can still reflect its training and post-training behaviour.
- The 18+ gate is self-declared and is not verified age assurance.
