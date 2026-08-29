package tech.liganex.studio.module.mcp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Skill 分发包注册与下载（公开，无鉴权）：一个业务域一个包，
 * 客户部署后从这里选包下载，发到 agent 终端（Qoder / workbuddy 等）
 * 即可对话调用开放平台对应业务域的接口。
 *
 * <p>包与清单由 {@code scripts/package-skill.sh} 生成：每个带
 * {@code skill.json} 的 skill 打成 {@code <name>.zip}，并汇总出
 * {@code manifest.json}，两者都放在 {@code resources/skills/}。
 * 下载只按清单中的文件名提供，杜绝任意路径读取。
 */
@RestController
@RequestMapping("/mcp/v1/skills")
@RequiredArgsConstructor
public class SkillPackageController {

    private static final ClassPathResource MANIFEST =
            new ClassPathResource("skills/manifest.json");

    private final ObjectMapper objectMapper;

    /** 可用 skill 包清单：名称、版本、说明、所需权限、下载地址。 */
    @GetMapping
    public List<Map<String, Object>> list() throws IOException {
        return manifest();
    }

    @GetMapping("/{name}.zip")
    public ResponseEntity<byte[]> download(@PathVariable String name) throws IOException {
        Map<String, Object> entry = manifest().stream()
                .filter(e -> name.equals(e.get("name")))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "skill 包不存在: " + name + "，可用清单见 GET /mcp/v1/skills"));
        ClassPathResource zip = new ClassPathResource("skills/" + name + ".zip");
        if (!zip.exists()) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "skill 包文件缺失，请重新运行 scripts/package-skill.sh");
        }
        byte[] bytes = zip.getContentAsByteArray();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + ".zip\"")
                .contentLength(bytes.length)
                .body(bytes);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> manifest() throws IOException {
        if (!MANIFEST.exists()) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "skill 清单未生成，请先运行 scripts/package-skill.sh");
        }
        return objectMapper.readValue(MANIFEST.getContentAsByteArray(), List.class);
    }
}
