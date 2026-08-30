import { App as AntdApp } from 'antd';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import KnowledgeBaseListPage from './KnowledgeBaseListPage';
import * as knowledgeApi from '../api/knowledge';

vi.mock('../api/knowledge', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/knowledge')>();
  return { ...original, listKnowledgeBases: vi.fn() };
});

function renderPage() {
  return render(
    <MemoryRouter>
      <AntdApp>
        <KnowledgeBaseListPage />
      </AntdApp>
    </MemoryRouter>,
  );
}

afterEach(cleanup);

describe('KnowledgeBaseListPage', () => {
  it('renders the empty state', async () => {
    vi.mocked(knowledgeApi.listKnowledgeBases).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('还没有知识库')).toBeInTheDocument();
  });

  it('renders a successful response', async () => {
    vi.mocked(knowledgeApi.listKnowledgeBases).mockResolvedValue([
      {
        id: 'kb-1',
        name: '产品手册',
        description: '产品知识',
        status: 'ready',
        documentCount: 2,
        readyDocumentCount: 1,
        createdAt: '2026-08-30T00:00:00Z',
        updatedAt: '2026-08-30T00:00:00Z',
      },
    ]);
    renderPage();
    expect(await screen.findByText('产品手册')).toBeInTheDocument();
    expect(screen.getByText('1/2 个文档已就绪')).toBeInTheDocument();
  });

  it('renders a readable error state', async () => {
    vi.mocked(knowledgeApi.listKnowledgeBases).mockRejectedValue(new Error('无法连接服务'));
    renderPage();
    expect(await screen.findByText('知识库加载失败')).toBeInTheDocument();
    expect(screen.getByText('无法连接服务')).toBeInTheDocument();
  });
});
