# Open-source notices

Soulmate AI uses the following open-source components and model artifacts.

- **LiteRT-LM Android runtime** — Apache License 2.0. Source: https://github.com/google-ai-edge/LiteRT-LM
- **Qwen3-0.6B LiteRT model artifact** — Apache-2.0 model card. The model is downloaded by the end user and is not bundled in the APK: https://huggingface.co/litert-community/Qwen3-0.6B
- **AndroidX / Android platform APIs** — platform and transitive dependency notices are provided by their respective projects.

The Qwen model file selected by this app is the 328 MiB dynamic INT4 LiteRT-LM artifact. Its GPU path requires LiteRT-LM 0.19.0 or later; this app currently initializes it with the CPU backend for broader compatibility.

Review each upstream license and model card before redistributing model weights or publishing derivative builds.
