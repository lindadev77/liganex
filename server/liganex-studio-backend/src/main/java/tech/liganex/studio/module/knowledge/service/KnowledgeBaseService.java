package tech.liganex.studio.module.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.dto.CreateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.dto.KnowledgeBaseResponse;
import tech.liganex.studio.module.knowledge.dto.UpdateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.entity.KnowledgeBase;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeBaseMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Transactional
    public KnowledgeBaseResponse create(Long ownerUserId, CreateKnowledgeBaseRequest request) {
        Instant now = Instant.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setOwnerUserId(ownerUserId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);
        return KnowledgeBaseResponse.from(knowledgeBase);
    }

    public List<KnowledgeBaseResponse> list(Long ownerUserId) {
        return knowledgeBaseMapper.selectAllOwned(ownerUserId).stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    public KnowledgeBaseResponse get(Long ownerUserId, Long knowledgeBaseId) {
        return KnowledgeBaseResponse.from(requireOwned(ownerUserId, knowledgeBaseId));
    }

    @Transactional
    public KnowledgeBaseResponse update(
            Long ownerUserId,
            Long knowledgeBaseId,
            UpdateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = requireOwned(ownerUserId, knowledgeBaseId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setUpdatedAt(Instant.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        return KnowledgeBaseResponse.from(knowledgeBase);
    }

    @Transactional
    public void delete(Long ownerUserId, Long knowledgeBaseId) {
        knowledgeBaseMapper.delete(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getOwnerUserId, ownerUserId)
                .eq(KnowledgeBase::getId, knowledgeBaseId));
    }

    public KnowledgeBase requireOwned(Long ownerUserId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOwnedById(ownerUserId, knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBase;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
