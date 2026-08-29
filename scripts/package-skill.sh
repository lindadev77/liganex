#!/usr/bin/env bash
# 把 skill 打包为客户分发 zip（一个业务域一个包）。
#
# skill 源统一存放在仓库顶层 skills/<name>/（随代码一起提交），
# 每个目录带 skill.json、自包含。产物：
#   dist/<name>-<version>.zip                     —— 交付物
#   server/.../resources/skills/<name>.zip        —— 供 GET /mcp/v1/skills/<name>.zip 下载
#   server/.../resources/skills/manifest.json     —— 供 GET /mcp/v1/skills 列表
#
# 用法: scripts/package-skill.sh            # 打包 skills/ 下全部带 skill.json 的 skill
#       scripts/package-skill.sh <name>...  # 只打包指定 skill（manifest 会重建，仅含这些）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKILLS_ROOT="$ROOT/skills"
DIST="$ROOT/dist"
RES="$ROOT/server/liganex-studio-backend/src/main/resources/skills"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

names=("$@")
if [ ${#names[@]} -eq 0 ]; then
  for d in "$SKILLS_ROOT"/*/; do
    [ -f "${d}skill.json" ] && names+=("$(basename "$d")")
  done
fi
[ ${#names[@]} -gt 0 ] || { echo "没有找到带 skill.json 的 skill"; exit 1; }

mkdir -p "$DIST" "$RES"
for name in "${names[@]}"; do
  SRC="$SKILLS_ROOT/$name"
  if [ ! -f "$SRC/skill.json" ]; then
    echo "跳过 $name（缺少 skill.json）" >&2
    continue
  fi
  VERSION=$(python3 -c "import json;print(json.load(open('$SRC/skill.json'))['version'])")
  rm -rf "$STAGE/$name"
  cp -R "$SRC" "$STAGE/$name"
  find "$STAGE/$name" -name '.DS_Store' -delete
  rm -f "$DIST/$name-$VERSION.zip"
  (cd "$STAGE" && zip -qr "$DIST/$name-$VERSION.zip" "$name")
  cp "$DIST/$name-$VERSION.zip" "$RES/$name.zip"
  echo "packaged: $DIST/$name-$VERSION.zip -> $RES/$name.zip"
done

python3 - "$SKILLS_ROOT" "$RES" "${names[@]}" <<'EOF'
import json, os, sys
root, res, names = sys.argv[1], sys.argv[2], sys.argv[3:]
entries = []
for name in names:
    path = os.path.join(root, name, "skill.json")
    if not os.path.isfile(path):
        continue
    meta = json.load(open(path))
    entries.append({
        "name": meta["name"],
        "version": meta.get("version", "0.0.0"),
        "description": meta.get("description", ""),
        "scopes": meta.get("scopes", []),
        "download": f"/mcp/v1/skills/{name}.zip",
    })
with open(os.path.join(res, "manifest.json"), "w") as f:
    json.dump(entries, f, ensure_ascii=False, indent=2)
print(f"manifest: {len(entries)} skills -> {res}/manifest.json")
EOF
