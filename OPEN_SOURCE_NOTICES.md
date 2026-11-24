# Open-source notices

Soulmate AI includes the following open-source components and model artifacts.

- **LiteRT-LM Android runtime 0.19.1** — Apache License 2.0. Source: https://github.com/google-ai-edge/LiteRT-LM
- **Qwen3-0.6B LiteRT model artifact** — Apache-2.0. Base model: https://huggingface.co/Qwen/Qwen3-0.6B. Quantized LiteRT artifact: https://huggingface.co/litert-community/Qwen3-0.6B
- **Android platform APIs** — Android platform APIs are provided by the Android Open Source Project.

The APK build packages the Qwen model file and its upstream license file into the app assets. A SHA-256 checksum is generated during the build and packaged with the model. At first launch, the app extracts the model to private app storage and verifies the extracted bytes before LiteRT-LM opens it.

The selected model is the 328 MiB dynamic INT4 artifact `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`. The app currently initializes it with the CPU backend. The upstream model card documents the supported runtime versions and device-dependent memory requirements.

Full upstream license texts are included in the built APK at `assets/licenses/Qwen3-0.6B-LICENSE.txt` and `assets/licenses/LiteRT-LM-LICENSE.txt`. The model copyright notice identifies Alibaba Cloud (2024). The weights are redistributed under the Apache-2.0 license stated by the upstream model repositories.

The standalone APK is for direct installation/testing. A future Google Play release must use an Android App Bundle and an appropriate Play Asset Delivery model pack because the model makes a single APK too large for Play's signed-APK download limit.
