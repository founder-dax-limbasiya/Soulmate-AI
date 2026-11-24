# Soulmate AI

Soulmate AI is an Android-first fictional adult companion app. The Android client stays lightweight: chat UI, character profiles and encrypted chat history live on the phone, while open-source model inference runs on a separately hosted cloud server.

## Android app

- Editorial-style Android WebView UI with Discover, Chats, Create Character and Settings.
- Self-declared 18+ entry gate; this is not identity or age verification.
- Fictional adult character profiles and user-created characters.
- AES-GCM encrypted local state, with the key protected by Android Keystore.
- Conversation history stays on the phone. When a user sends a message, the character system prompt, optional personal notes and up to 12 recent messages are sent to the configured HTTPS AI server for generation.
- Streaming replies over HTTPS using OkHttp.
- The app does not bundle or download model weights. Expected APK size is in the normal Android-app range (CI enforces a 60 MB upper bound).

## Cloud inference server

The `server/` directory contains an open-source deployment stack:

- Qwen3-4B GGUF, Q4_K_M quantization (Apache-2.0 upstream model).
- llama.cpp server for OpenAI-compatible streaming inference.
- FastAPI gateway with request-size limits, bounded message context, IP-based beta rate limiting and one concurrent generation at a time.
- Caddy for automatic HTTPS.

See [server/README.md](server/README.md) for deployment instructions. The model is downloaded once onto the server's persistent volume, not to each user's phone.

A small Oracle Cloud Ampere A1 Always Free allocation (where available) can be used for a private beta, but CPU generation may be slow. Current free resource quotas and regional capacity must be checked in the Oracle Console before deployment. Public launch needs stronger user authentication, abuse controls, monitoring, privacy disclosures and capacity planning.

## Build Android APK

GitHub Actions builds the lightweight debug APK with Java 17 and Gradle 8.9. The build verifies that no `.litertlm`, `.gguf` or `.safetensors` model weights are included in the APK.

## Privacy and limitations

- Chat history and character profiles are stored encrypted on the phone.
- Messages, character instructions and recent context are transmitted to the AI server selected in Settings. Do not connect to a server you do not trust.
- The included gateway does not store chat content in a database. The server operator still processes requests and may see network metadata; this is not end-to-end encryption.
- No account system, cloud chat sync, voice, payments or analytics backend is connected yet.
- The default cloud server URL is intentionally unset. The app needs a deployed HTTPS gateway before AI chat can work.
- The initial cloud stack is a private-beta architecture, not a production service guarantee.
