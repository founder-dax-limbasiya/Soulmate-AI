# Soulmate AI cloud inference server

This server keeps the language model off Android phones. The Android app sends the current character instructions and a short conversation window to this HTTPS API. Chat history remains encrypted in Android app storage. The gateway does not write chat messages to a database; request-body logging is disabled in the model server. Network operators and the server host can still observe traffic metadata, so do not promise end-to-end privacy.

## Stack

- Qwen3-4B GGUF, Q4_K_M quantization (Apache-2.0 upstream model)
- llama.cpp server (OpenAI-compatible streaming chat completions)
- FastAPI gateway with bounded request sizes, in-memory IP rate limits, and one-at-a-time inference
- Caddy automatic HTTPS

The official llama.cpp Docker image supports linux/arm64 as well as linux/amd64. The model is downloaded to a persistent Docker volume on first start; it is never shipped in the Android APK.

## Suggested low-cost host

An OCI Ampere A1 Always Free allocation currently covers up to 2 OCPUs and 12 GB RAM, subject to the tenancy's home-region quota and capacity. A 4B Q4 model should fit in memory with a 4K context, but CPU generation can be slow and concurrent requests are intentionally limited to one. This is a small beta server, not a production-scale service. See Oracle's current Always Free resource limits before creating a VM.

## Deploy

1. Create an Ubuntu ARM64 VM with at least 2 OCPUs, 12 GB RAM and enough boot/block storage for Docker images plus the model cache (allow at least 15 GB free).
2. Install Docker Engine and the Compose plugin.
3. Point a domain or subdomain you control to the VM's public IP. Allow inbound TCP 80 and 443 in the cloud security list and VM firewall.
4. Copy this folder to the VM, then run:
   ```sh
   cp .env.example .env
   # Edit .env and set DOMAIN to your real domain
   docker compose up -d
   docker compose logs -f llama
   ```
5. Wait for the first model download and load to finish. Test `https://YOUR_DOMAIN/health`; it should return `{"status":"ok","inference":"ready"}`.
6. Enter that HTTPS address in Soulmate AI Settings and tap Connect.

The model download is several gigabytes and happens once on the server. Keep the `model-cache` Docker volume; deleting it forces a fresh download.

## API behavior and limits

- `GET /health` reports ready only when llama.cpp has loaded the model.
- `POST /v1/chat/completions` streams OpenAI-compatible SSE chunks.
- Up to 14 messages and 12,000 total characters per request.
- 420 generated tokens maximum.
- 20 requests per IP per 10 minutes; one active generation at a time.
- Rate-limit counters are in memory and reset when the gateway restarts. This is abuse friction for a private beta, not strong user authentication.
- Chat content is not stored in a database. The server must still be operated as a trusted processor; add real user authentication, billing/quotas, monitoring and a privacy policy before public launch.

## Model and runtime licenses

- Qwen3-4B: Apache-2.0, https://huggingface.co/Qwen/Qwen3-4B
- Qwen3 GGUF: https://huggingface.co/Qwen/Qwen3-4B-GGUF
- llama.cpp: MIT, https://github.com/ggml-org/llama.cpp
