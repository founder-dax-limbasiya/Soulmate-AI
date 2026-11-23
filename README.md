# Soulmate AI

An Android-first, privacy-first AI companion foundation. The current build runs the language model locally on the user's Android device using LiteRT-LM.

## Current MVP foundation

- Calm editorial UI with restrained colours, typography-led screens and character monograms (no generated-looking portrait placeholders).

- Self-declared 18+ entry gate. Date of birth is used only for the on-device check and is not saved.
- Character discovery, local conversation UI and user-created fictional character profiles.
- Optional user-written personal context (up to 1,500 characters), injected into character instructions and stored only in the encrypted local state.
- AES-GCM encrypted local app state, with the encryption key held by Android Keystore.
- Optional analytics consent preference is off by default. No analytics endpoint or cloud upload is connected in this build.
- On-device Qwen3-0.6B LiteRT-LM model download and CPU inference.
- Chat text, personal context and prompts are not sent to a Soulmate AI server in this build.

## Build

The GitHub Actions workflow builds a debug APK with Java 17 and Gradle 8.9.

The model is deliberately **not** bundled into the APK. From the app's Model settings, a user can download the approximately 328 MiB file from the public Hugging Face model repository. Once downloaded, inference runs locally and can work offline.

## Model and runtime

- Runtime: LiteRT-LM Android 0.19.1
- Model: Qwen3-0.6B, Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm
- Model card/license: https://huggingface.co/litert-community/Qwen3-0.6B
- Runtime source: https://github.com/google-ai-edge/LiteRT-LM

Performance depends heavily on the handset. The 0.6B model is an initial compatibility target, not a promise of high-quality or fast output on every phone. Model loading can require substantial free RAM and storage.

## Privacy notes

The app encrypts saved profile and chat state using an AES-GCM key protected by Android Keystore. Clearing app data or uninstalling the app removes the local data; there is no cloud backup in this MVP. A self-declared age gate is not identity verification.

Analytics are off by default and are not transmitted in this build. A future analytics service must be opt-in, disclose exactly which aggregate events are sent, and must never upload private chat content by default.

## Important limitations of this build

- This is an early MVP foundation, not a finished commercial adult platform.
- The local model is a general small language model. Character prompts shape tone, but they do not guarantee unrestricted output or high-quality roleplay.
- No account system, cloud sync, voice, payments, creator marketplace, or analytics backend is connected yet.
- Do not treat the self-declared 18+ gate as a substitute for stronger age assurance where required by law or distribution policy.
