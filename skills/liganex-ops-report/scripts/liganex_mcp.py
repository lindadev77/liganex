#!/usr/bin/env python3
"""liganex-mcp — 签名客户端：通过 HMAC 签名调用 liganex MCP 工具。

凭证解析顺序：
  1. 环境变量 LIGANEX_APP_ID / LIGANEX_APP_SECRET / LIGANEX_MCP_URL
  2. ~/.liganex/credentials（JSON，由 `setup` 命令写入）

用法：
  liganex_mcp.py setup --app-id X --app-secret Y [--url http://host:8081]
  liganex_mcp.py check                 # 用 order_query 验证凭证与权限链路
  liganex_mcp.py tools                 # 列出服务端全部工具（公开接口）
  liganex_mcp.py call <tool> [--args '{"k":"v"}'] [--arg k=v ...]
"""
import argparse
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

DEFAULT_URL = "http://127.0.0.1:8081"
CRED_FILE = Path.home() / ".liganex" / "credentials"

TOOL_SCOPES = {
    "order_query": "order:read",
    "order_write": "order:write",
    "order_update": "order:write",
    "order_ship": "order:write",
    "product_query": "product:read",
    "product_write": "product:write",
    "inventory_query": "inventory:read",
    "inventory_adjust": "inventory:write",
}


def die(msg: str, code: int = 1):
    print(f"[liganex-mcp] {msg}", file=sys.stderr)
    sys.exit(code)


def load_credentials():
    app_id = os.environ.get("LIGANEX_APP_ID")
    app_secret = os.environ.get("LIGANEX_APP_SECRET")
    url = os.environ.get("LIGANEX_MCP_URL")
    if not (app_id and app_secret):
        if CRED_FILE.exists():
            try:
                data = json.loads(CRED_FILE.read_text())
            except Exception as ex:
                die(f"凭证文件 {CRED_FILE} 解析失败：{ex}")
            app_id = app_id or data.get("appId")
            app_secret = app_secret or data.get("appSecret")
            url = url or data.get("url")
    if not app_id or not app_secret:
        die("未配置凭证。请先在开放平台创建应用拿到 appId/密钥，然后运行：\n"
            f"  {sys.argv[0]} setup --app-id <appId> --app-secret <密钥>")
    return app_id, app_secret, (url or DEFAULT_URL).rstrip("/")


def rpc(url: str, method: str, params=None, app_id=None, app_secret=None):
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method,
                       **({"params": params} if params is not None else {})},
                      ensure_ascii=False)
    headers = {"Content-Type": "application/json"}
    if app_secret is not None:
        ts = str(int(time.time()))
        nonce = uuid.uuid4().hex
        canonical = f"POST\n/mcp/v1\n{ts}\n{nonce}\n{body}"
        sig = hmac.new(app_secret.encode(), canonical.encode(),
                       hashlib.sha256).hexdigest()
        headers.update({"x-app-id": app_id, "x-timestamp": ts,
                        "x-nonce": nonce, "x-signature": sig})
    req = urllib.request.Request(url + "/mcp/v1", data=body.encode(),
                                 headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            reply = json.loads(resp.read().decode())
    except urllib.error.URLError as ex:
        die(f"无法连接 {url}：{ex.reason}。请确认后端已启动（端口 8081）。")
    except Exception as ex:
        die(f"请求失败：{ex}")
    if "error" in reply:
        err = reply["error"]
        hint = ""
        msg = err.get("message", "")
        if "签名" in msg or "signature" in msg.lower():
            hint = "（提示：密钥错误，或该应用密钥已轮换，请重新在开放平台获取）"
        elif "未授权" in msg or "scope" in msg.lower():
            hint = "（提示：该应用未绑定此工具对应的权限，请到开放平台应用详情页勾选）"
        elif "重放" in msg or "nonce" in msg.lower():
            hint = "（提示：同一请求重复发送，重试即可）"
        die(f"RPC 错误 {err.get('code')}: {msg}{hint}")
    return reply.get("result")


def unwrap_tool_result(result):
    content = result.get("content") or []
    texts = [c.get("text", "") for c in content if c.get("type") == "text"]
    text = "\n".join(texts)
    try:
        return json.loads(text)
    except Exception:
        return text


def cmd_setup(args):
    url = args.url or DEFAULT_URL
    CRED_FILE.parent.mkdir(parents=True, exist_ok=True)
    CRED_FILE.write_text(json.dumps(
        {"appId": args.app_id, "appSecret": args.app_secret, "url": url},
        ensure_ascii=False, indent=2))
    os.chmod(CRED_FILE, 0o600)
    print(f"凭证已保存到 {CRED_FILE}（仅本人可读）")
    try:
        result = rpc(url, "tools/list")
        names = [t["name"] for t in result["tools"]]
        print(f"连通性正常，服务端共 {len(names)} 个工具：{', '.join(sorted(names))}")
    except SystemExit:
        print("警告：无法连通服务端，凭证已保存，可稍后运行 check 验证。",
              file=sys.stderr)


def cmd_check(_args):
    app_id, app_secret, url = load_credentials()
    print(f"appId={app_id}  服务端={url}")
    result = rpc(url, "tools/call",
                 {"name": "order_query", "arguments": {"size": 1}},
                 app_id, app_secret)
    data = unwrap_tool_result(result)
    print("凭证验证通过：签名 + order:read 权限均正常。")
    print(json.dumps(data, ensure_ascii=False, indent=2))


def cmd_tools(_args):
    app_id, app_secret, url = load_credentials()
    result = rpc(url, "tools/list")
    for t in result["tools"]:
        scope = TOOL_SCOPES.get(t["name"], "?")
        props = t.get("inputSchema", {}).get("properties", {})
        params = ", ".join(props) if props else "(无参数)"
        print(f"- {t['name']}  [{scope}]  {t.get('description', '')}")
        print(f"    参数: {params}")


def cmd_call(args):
    app_id, app_secret, url = load_credentials()
    arguments = {}
    if args.args:
        try:
            arguments.update(json.loads(args.args))
        except Exception as ex:
            die(f"--args 不是合法 JSON：{ex}")
    for kv in args.arg or []:
        if "=" not in kv:
            die(f"--arg 需要 k=v 形式：{kv}")
        k, v = kv.split("=", 1)
        try:
            arguments[k] = json.loads(v)
        except Exception:
            arguments[k] = v
    if args.tool not in TOOL_SCOPES:
        print(f"警告：{args.tool} 不是已知工具（已知：{', '.join(sorted(TOOL_SCOPES))}）",
              file=sys.stderr)
    result = rpc(url, "tools/call",
                 {"name": args.tool, "arguments": arguments},
                 app_id, app_secret)
    data = unwrap_tool_result(result)
    print(json.dumps(data, ensure_ascii=False, indent=2)
          if not isinstance(data, str) else data)


def main():
    parser = argparse.ArgumentParser(description="liganex MCP 签名客户端")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("setup", help="保存应用凭证并做连通性检查")
    p.add_argument("--app-id", required=True)
    p.add_argument("--app-secret", required=True)
    p.add_argument("--url", default=None, help=f"服务端地址，默认 {DEFAULT_URL}")
    p.set_defaults(func=cmd_setup)

    p = sub.add_parser("check", help="用 order_query 验证凭证与权限")
    p.set_defaults(func=cmd_check)

    p = sub.add_parser("tools", help="列出服务端工具及所需权限")
    p.set_defaults(func=cmd_tools)

    p = sub.add_parser("call", help="签名调用一个工具")
    p.add_argument("tool")
    p.add_argument("--args", default=None, help="JSON 形式参数，如 '{\"region\":\"US\"}'")
    p.add_argument("--arg", action="append", help="k=v 形式参数，可重复；值会尝试按 JSON 解析")
    p.set_defaults(func=cmd_call)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
