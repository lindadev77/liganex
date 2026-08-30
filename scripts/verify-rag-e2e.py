#!/usr/bin/env python3
"""知识库 + 智能问答端到端联调（19 项断言）。

前置条件：
  1) 本地基础设施已起：cd infra/local-dev && docker compose up -d
  2) 后端已在 8081 起服（RAG 相关环境变量见 application.yml；可用 scripts/stub-ai-server.py
     起本地 stub 模型，无需真实 API Key）
  3) 数据库已跑过 Flyway（V8 建知识库/聊天相关表）

用法:
  python3 scripts/verify-rag-e2e.py                    # 默认打 http://127.0.0.1:8081
  LIGANEX_E2E_BASE=http://127.0.0.1:9090 python3 scripts/verify-rag-e2e.py

覆盖链路：注册登录 → 建知识库 → 录入文本 → 索引 READY → 建会话绑定知识库 →
SSE 流式问答并命中引用 → 消息历史持久化 → 两用户隔离（跨用户资源 404）→ 删除知识库后不可检索。
每次运行使用独立账号，可重复执行。
"""
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.environ.get("LIGANEX_E2E_BASE", "http://127.0.0.1:8081")
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
urllib.request.install_opener(_OPENER)
STAMP = str(int(time.time()))
results = []


def call(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw_body = resp.read().decode()
            return resp.status, (raw_body if raw else json.loads(raw_body))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode()
        try:
            return e.code, json.loads(raw_body)
        except json.JSONDecodeError:
            return e.code, raw_body


def stream_ask(conversation_id, question, token):
    """解析命名事件格式的 SSE：event: token|done|error + data: {...}"""
    req = urllib.request.Request(
        f"{BASE}/api/v1/chat/conversations/{conversation_id}/messages/stream",
        data=json.dumps({"question": question}).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + token)
    tokens, citations, done, errors = [], [], False, []
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            event_name, data_lines = None, []
            for raw in resp:
                line = raw.decode("utf-8").rstrip("\n").rstrip("\r")
                if line == "":
                    if event_name and data_lines:
                        payload = json.loads("\n".join(data_lines))
                        if event_name == "token":
                            tokens.append(payload.get("token") or "")
                        elif event_name == "done":
                            done = True
                            citations = payload.get("citations") or []
                        elif event_name == "error":
                            errors.append(payload)
                    event_name, data_lines = None, []
                    continue
                if line.startswith("event:"):
                    event_name = line[6:].strip()
                elif line.startswith("data:"):
                    data_lines.append(line[5:].strip())
    except Exception as ex:  # noqa: BLE001
        errors.append({"exception": str(ex)})
    return tokens, citations, done, errors


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'} | {name} | {str(detail)[:120]}")


def register(email):
    status, body = call("POST", "/api/v1/auth/register", body={
        "email": email, "password": "Liganex@2026", "displayName": "QA"})
    return status, body


def login(email):
    status, body = call("POST", "/api/v1/auth/login", body={
        "email": email, "password": "Liganex@2026"})
    token = (body or {}).get("data", {}).get("accessToken") if isinstance(body, dict) else None
    return status, token


# ---------- 1. 两个用户注册登录 ----------
email_a = f"raga_{STAMP}@liganex.dev"
email_b = f"ragb_{STAMP}@liganex.dev"
sa, _ = register(email_a)
sb, _ = register(email_b)
check("注册用户 A/B", sa in (200, 201) and sb in (200, 201), f"A={sa} B={sb}")
_, token_a = login(email_a)
_, token_b = login(email_b)
check("登录取得 JWT", bool(token_a) and bool(token_b), f"A={'有' if token_a else '无'} B={'有' if token_b else '无'}")

# ---------- 2. 建知识库 ----------
st, body = call("POST", "/api/v1/knowledge-bases", token_a, {"name": "联调知识库", "description": "E2E"})
kb_id = (body or {}).get("data", {}).get("id") if isinstance(body, dict) else None
check("创建知识库", st in (200, 201) and kb_id is not None, f"status={st} id={kb_id}")

# ---------- 3. 录入文本 ----------
doc_text = ("订单查询接口说明：调用 order_query 工具，需要 order:read 权限，"
            "支持按地区 region、状态 status 与时间区间过滤，分页返回订单列表。"
            "发货使用 order_ship，需要订单号 orderNo、承运商 carrier 与运单号 trackingNo。")
st, body = call("POST", f"/api/v1/knowledge-bases/{kb_id}/documents/text", token_a,
                {"title": "订单接口手册", "content": doc_text})
doc_id = (body or {}).get("data", {}).get("id") if isinstance(body, dict) else None
check("录入文本文档", st in (200, 201) and doc_id is not None, f"status={st} id={doc_id}")

# ---------- 4. 等待索引完成 ----------
status_now, deadline = "", time.time() + 90
while time.time() < deadline:
    st, body = call("GET", f"/api/v1/knowledge-bases/{kb_id}/documents/{doc_id}", token_a)
    status_now = (body or {}).get("data", {}).get("status") if isinstance(body, dict) else "?"
    if status_now in ("READY", "FAILED"):
        break
    time.sleep(2)
check("文档索引完成（READY）", status_now == "READY", f"status={status_now}")

# ---------- 5. 建会话并绑定知识库 ----------
st, body = call("POST", "/api/v1/chat/conversations", token_a,
                {"title": "联调会话", "knowledgeBaseIds": [kb_id]})
conv_id = (body or {}).get("data", {}).get("id") if isinstance(body, dict) else None
check("创建会话并绑定知识库", st in (200, 201) and conv_id is not None, f"status={st} id={conv_id}")

# ---------- 6. SSE 问答 ----------
tokens, citations, done, errors = stream_ask(conv_id, "订单查询接口怎么调用，需要什么权限？", token_a)
answer = "".join(tokens)
check("SSE 无错误事件", not errors, str(errors)[:120])
check("SSE 收到 token 流", len(tokens) > 0, f"token 数={len(tokens)}")
check("SSE 收到 done 事件", done, f"answer 长度={len(answer)}")
check("回答带引用（检索命中）", len(citations) > 0, f"引用数={len(citations)}")
check("引用指向本次录入的文档",
      all(str(c.get("documentId")) == str(doc_id) for c in citations) if citations else False,
      json.dumps(citations, ensure_ascii=False)[:120] if citations else "无引用")

# ---------- 7. 消息历史（L0 持久化） ----------
st, body = call("GET", f"/api/v1/chat/conversations/{conv_id}/messages", token_a)
msgs = (body or {}).get("data") or []
roles = {m.get("role") for m in msgs} if isinstance(msgs, list) else set()
check("消息历史含 USER 与 ASSISTANT", {"USER", "ASSISTANT"} <= roles, f"条数={len(msgs)} roles={roles}")

# ---------- 8. 用户隔离 ----------
st, body = call("GET", "/api/v1/knowledge-bases", token_b)
kbs_b = (body or {}).get("data") or []
check("用户 B 看不到 A 的知识库", all(str(k.get("id")) != str(kb_id) for k in kbs_b), f"B 的知识库数={len(kbs_b)}")
st, _ = call("GET", f"/api/v1/knowledge-bases/{kb_id}", token_b)
check("用户 B 直接访问 A 知识库 → 404", st == 404, f"status={st}")
st, _ = call("GET", f"/api/v1/knowledge-bases/{kb_id}/documents", token_b)
check("用户 B 访问 A 知识库文档 → 404", st == 404, f"status={st}")
st, _ = call("GET", f"/api/v1/chat/conversations/{conv_id}", token_b)
check("用户 B 访问 A 会话 → 404", st == 404, f"status={st}")

# ---------- 9. 删除知识库后再问 ----------
st, _ = call("DELETE", f"/api/v1/knowledge-bases/{kb_id}", token_a)
check("删除知识库", st in (200, 204), f"status={st}")
conv2 = conv_id  # 复用原会话：其绑定的知识库已删，检索范围应为空
tokens2, citations2, done2, errors2 = stream_ask(conv2, "订单查询接口怎么调用？", token_a)
check("删除后问答未报错", not errors2, str(errors2)[:120])
check("删除后问答不再引用已删内容", len(citations2) == 0, f"引用数={len(citations2)} done={done2}")

# ---------- 汇总 ----------
passed = sum(1 for _, ok, _ in results if ok)
print("\n" + "=" * 60)
print(f"通过 {passed}/{len(results)}")
for name, ok, detail in results:
    if not ok:
        print(f"  未通过: {name} -> {detail}")
print("=" * 60)
