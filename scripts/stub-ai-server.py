#!/usr/bin/env python3
"""OpenAI 兼容的 stub 模型服务（仅用于本地联调，不入库/不联网调用真实模型）。

- POST /v1/embeddings      : 用 hashing trick 生成确定性 1536 维向量并做 L2 归一化，
                             使词面相近的文本余弦相似度较高，便于验证 dense 召回。
- POST /v1/chat/completions: 支持普通与 stream(SSE) 两种模式，答案由请求中的上下文拼出。

用法: python3 liganex_stub_ai.py [port]
"""
import hashlib
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIM = int(sys.argv[2]) if len(sys.argv) > 2 else 1536


def embed(text: str) -> list:
    vec = [0.0] * DIM
    tokens = re.findall(r"[一-鿿]|[a-zA-Z]+|\d+", text.lower())
    grams = set(tokens)
    for a, b in zip(tokens, tokens[1:]):
        grams.add(a + b)
    for gram in grams:
        digest = hashlib.blake2b(gram.encode("utf-8"), digest_size=8).digest()
        idx = int.from_bytes(digest[:4], "big") % DIM
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        weight = 1.0 + 0.5 * (1 if len(gram) > 1 else 0)
        vec[idx] += sign * weight
    norm = sum(v * v for v in vec) ** 0.5
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec


def answer(messages: list) -> str:
    question, context = "", []
    for msg in messages or []:
        content = msg.get("content") or ""
        role = msg.get("role")
        if role == "user":
            question = content
        if role in ("system", "user") and ("上下文" in content or "来源" in content):
            context.append(content)
    snippet = " ".join(context)[:600].replace("\n", " ")
    return f"[stub 模型] 问题：{question[:200]}。依据检索到的 {len(context)} 段上下文作答：{snippet}"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _json(self, payload: dict, status: int = 200):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            return self._json({"error": {"message": "invalid json"}}, 400)

        if self.path.endswith("/embeddings"):
            inputs = payload.get("input")
            if isinstance(inputs, str):
                inputs = [inputs]
            inputs = inputs or [""]
            data = [
                {"object": "embedding", "index": i, "embedding": embed(t)}
                for i, t in enumerate(inputs)
            ]
            return self._json(
                {"object": "list", "data": data, "model": payload.get("model", "stub"),
                 "usage": {"prompt_tokens": 0, "total_tokens": 0}}
            )

        if self.path.endswith("/chat/completions"):
            messages = payload.get("messages") or []
            text = answer(messages)
            if payload.get("stream"):
                return self._stream(text)
            return self._json({
                "id": "chatcmpl-stub", "object": "chat.completion", "created": int(time.time()),
                "model": payload.get("model", "stub"),
                "choices": [{"index": 0, "message": {"role": "assistant", "content": text},
                             "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
            })

        return self._json({"error": {"message": f"unsupported path {self.path}"}}, 404)

    def _stream(self, text: str):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.end_headers()
        chunks = [text[i:i + 24] for i in range(0, len(text), 24)] or [""]
        for chunk in chunks:
            frame = {"id": "chatcmpl-stub", "object": "chat.completion.chunk",
                     "created": int(time.time()), "model": "stub",
                     "choices": [{"index": 0, "delta": {"content": chunk}, "finish_reason": None}]}
            self.wfile.write(f"data: {json.dumps(frame)}\n\n".encode("utf-8"))
            self.wfile.flush()
        done = {"id": "chatcmpl-stub", "object": "chat.completion.chunk", "created": int(time.time()),
                "model": "stub", "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]}
        self.wfile.write(f"data: {json.dumps(done)}\n\n".encode("utf-8"))
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
    print(f"stub ai server on http://127.0.0.1:{port} dim={DIM}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
