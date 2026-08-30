import { describe, expect, it } from 'vitest';
import { parseKnowledgeBase, parseKnowledgeDocument, validateKnowledgeFile } from './knowledge';

describe('knowledge response parser', () => {
  it('normalizes backend DTO fields and status values', () => {
    expect(
      parseKnowledgeBase({
        id: 1,
        name: '产品手册',
        description: '测试',
        status: 'ACTIVE',
        createdAt: '2026-08-30T00:00:00Z',
        updatedAt: '2026-08-30T00:00:00Z',
      }),
    ).toMatchObject({ id: '1', name: '产品手册', status: 'ready' });

    expect(
      parseKnowledgeDocument({
        id: 2,
        knowledgeBaseId: 1,
        title: '使用说明.pdf',
        sourceType: 'FILE',
        mediaType: 'application/pdf',
        status: 'PROCESSING',
        progress: 45,
        chunkCount: 0,
        sizeBytes: 2048,
        errorSummary: null,
        createdAt: '2026-08-30T00:00:00Z',
        updatedAt: '2026-08-30T00:00:00Z',
      }),
    ).toMatchObject({
      id: '2',
      knowledgeBaseId: '1',
      name: '使用说明.pdf',
      sourceType: 'pdf',
      status: 'processing',
      progress: 45,
    });
  });

  it('validates upload type and size', () => {
    expect(validateKnowledgeFile({ name: 'readme.md', size: 12, type: 'text/markdown' })).toBeNull();
    expect(validateKnowledgeFile({ name: 'image.png', size: 12, type: 'image/png' })).toContain('仅支持');
    expect(
      validateKnowledgeFile({ name: 'large.pdf', size: 11 * 1024 * 1024, type: 'application/pdf' }),
    ).toContain('10 MB');
  });
});
