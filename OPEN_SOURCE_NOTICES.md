# Open-source notices

The lightweight Android APK includes:

- **OkHttp 4.12.0** — Apache License 2.0. Source: https://github.com/square/okhttp
- **Android platform APIs** — Android Open Source Project.

The cloud inference deployment in `server/` uses:

- **Qwen3-4B** — Apache-2.0. Model card: https://huggingface.co/Qwen/Qwen3-4B
- **Qwen3-4B GGUF quantizations** — https://huggingface.co/Qwen/Qwen3-4B-GGUF
- **llama.cpp** — MIT License. Source: https://github.com/ggml-org/llama.cpp
- **FastAPI** — MIT License. Source: https://github.com/fastapi/fastapi
- **HTTPX** — BSD-3-Clause. Source: https://github.com/encode/httpx
- **Caddy** — Apache License 2.0. Source: https://github.com/caddyserver/caddy

Model weights are downloaded to the server's persistent model cache during deployment and are not included in the Android APK. Review the upstream model and runtime license files when pinning/updating versions.
