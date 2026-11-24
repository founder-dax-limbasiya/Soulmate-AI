# Open-source notices

The Android application uses:

- **LiteRT-LM Android runtime 0.19.1** — Apache License 2.0. Source: https://github.com/google-ai-edge/LiteRT-LM
- **Qwen3-0.6B LiteRT model artifact** — Apache License 2.0. Model repository: https://huggingface.co/litert-community/Qwen3-0.6B
- **Android platform APIs** — Android Open Source Project.

The app downloads `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` from the pinned upstream revision
`e6bb3e4a1eb3f663153f0c1c12b735d64be657a7` on first-run setup. The model is not bundled in the APK.
Weights are stored in the app's private files directory and used for on-device inference. The download
is checked with SHA-256 before it is marked ready. Chat messages and prompts are not sent to a
remote AI inference server.

Review the upstream license texts when updating either dependency or model revision.
