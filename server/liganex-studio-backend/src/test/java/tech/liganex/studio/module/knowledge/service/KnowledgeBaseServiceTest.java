package tech.liganex.studio.module.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.knowledge.dto.CreateKnowledgeBaseRequest;
import tech.liganex.studio.module.knowledge.entity.KnowledgeBase;
import tech.liganex.studio.module.knowledge.mapper.KnowledgeBaseMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseMapper mapper;

    @Test
    void createAlwaysUsesAuthenticatedOwner() {
        KnowledgeBaseService service = new KnowledgeBaseService(mapper);

        service.create(42L, new CreateKnowledgeBaseRequest(" 手册 ", " 说明 "));

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getName()).isEqualTo("手册");
        assertThat(captor.getValue().getDescription()).isEqualTo("说明");
    }

    @Test
    void foreignResourceIsIndistinguishableFromMissingResource() {
        when(mapper.selectOwnedById(42L, 9L)).thenReturn(null);
        KnowledgeBaseService service = new KnowledgeBaseService(mapper);

        assertThatThrownBy(() -> service.get(42L, 9L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
    }

    @Test
    void deleteIsIdempotentAndOwnerScoped() {
        KnowledgeBaseService service = new KnowledgeBaseService(mapper);

        service.delete(42L, 9L);
        service.delete(42L, 9L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<KnowledgeBase>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper, org.mockito.Mockito.times(2)).delete(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }
}
