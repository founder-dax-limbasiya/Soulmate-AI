import asyncio
import os
import time
from collections import defaultdict, deque
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse

LLAMA_CHAT_URL = os.getenv("LLAMA_CHAT_URL", "http://llama:8080/v1/chat/completions")
LLAMA_HEALTH_URL = os.getenv("LLAMA_HEALTH_URL", "http://llama:8080/health")
WINDOW_SECONDS = int(os.getenv("RATE_WINDOW_SECONDS", "600"))
MAX_REQUESTS_PER_WINDOW = int(os.getenv("RATE_MAX_REQUESTS", "20"))
MAX_MESSAGES = 14
MAX_CONTENT_CHARS = 6000
MAX_OUTPUT_TOKENS = 420

app = FastAPI(title="Soulmate AI Gateway", docs_url=None, redoc_url=None)
http = httpx.AsyncClient(timeout=httpx.Timeout(connect=15, read=300, write=30, pool=15))
request_times: dict[str, deque[float]] = defaultdict(deque)
rate_lock = asyncio.Lock()
generation_slot = asyncio.Semaphore(1)


def client_ip(request: Request) -> str:
    # The gateway is reachable only through Caddy. Trust its forwarded client IP.
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        return forwarded.split(",", 1)[0].strip()[:64]
    return request.client.host if request.client else "unknown"


async def enforce_rate_limit(ip: str) -> None:
    now = time.monotonic()
    async with rate_lock:
        hits = request_times[ip]
        while hits and now - hits[0] > WINDOW_SECONDS:
            hits.popleft()
        if len(hits) >= MAX_REQUESTS_PER_WINDOW:
            raise HTTPException(status_code=429, detail="Chat limit reached for now. Please try again later.")
        hits.append(now)
        # Keep the in-memory table bounded on a small free server.
        if len(request_times) > 4096:
            for key in list(request_times)[:1024]:
                if not request_times[key] or now - request_times[key][-1] > WINDOW_SECONDS:
                    request_times.pop(key, None)


def validate_messages(value: Any) -> list[dict[str, str]]:
    if not isinstance(value, list) or not 1 <= len(value) <= MAX_MESSAGES:
        raise HTTPException(status_code=400, detail="A conversation must contain 1 to 14 messages.")
    clean: list[dict[str, str]] = []
    total = 0
    for item in value:
        if not isinstance(item, dict):
            raise HTTPException(status_code=400, detail="Invalid message format.")
        role = item.get("role")
        content = item.get("content")
        if role not in {"system", "user", "assistant"} or not isinstance(content, str):
            raise HTTPException(status_code=400, detail="Invalid message role or content.")
        if len(content) > MAX_CONTENT_CHARS:
            raise HTTPException(status_code=413, detail="A message is too long.")
        total += len(content)
        clean.append({"role": role, "content": content})
    if total > 18000:
        raise HTTPException(status_code=413, detail="Conversation context is too large.")
    if not any(item["role"] == "user" for item in clean):
        raise HTTPException(status_code=400, detail="At least one user message is required.")
    return clean


@app.get("/health")
async def health():
    try:
        response = await http.get(LLAMA_HEALTH_URL, timeout=5)
        if response.status_code == 200:
            return {"status": "ok", "inference": "ready"}
    except httpx.HTTPError:
        pass
    raise HTTPException(status_code=503, detail="The AI model is starting or unavailable.")


@app.post("/v1/chat/completions")
async def chat_completions(request: Request):
    await enforce_rate_limit(client_ip(request))
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON body.")

    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="Invalid request.")
    messages = validate_messages(body.get("messages"))

    payload = {
        "model": "Qwen3-4B",
        "messages": messages,
        "temperature": min(1.2, max(0.1, float(body.get("temperature", 0.85)))),
        "top_p": min(1.0, max(0.1, float(body.get("top_p", 0.92)))),
        "max_tokens": min(MAX_OUTPUT_TOKENS, max(64, int(body.get("max_tokens", 420)))),
        "stream": True,
        "chat_template_kwargs": {"enable_thinking": False},
    }

    async def relay():
        # One active generation at a time on the small Always Free CPU VM.
        async with generation_slot:
            try:
                async with http.stream("POST", LLAMA_CHAT_URL, json=payload) as upstream:
                    if upstream.status_code != 200:
                        await upstream.aread()
                        yield b'data: {"error":{"message":"The inference server rejected this request."}}\n\n'
                        yield b"data: [DONE]\n\n"
                        return
                    async for chunk in upstream.aiter_bytes():
                        yield chunk
            except httpx.HTTPError:
                yield b'data: {"error":{"message":"The inference server is temporarily unavailable."}}\n\n'
                yield b"data: [DONE]\n\n"

    return StreamingResponse(
        relay(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@app.on_event("shutdown")
async def shutdown():
    await http.aclose()
