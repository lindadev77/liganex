import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  App,
  Avatar,
  Button,
  Card,
  Collapse,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import {
  cancelGeneration,
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  listMessages,
  streamMessage,
  updateConversationKnowledgeBases,
} from '../api/chat';
import { extractError } from '../api/client';
import { listKnowledgeBases } from '../api/knowledge';
import type {
  ChatConversation,
  ChatMessage,
  Citation,
  KnowledgeBase,
} from '../api/ragTypes';

interface ConversationFormValues {
  title?: string;
  knowledgeBaseIds: string[];
}

function temporaryId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function citationLocation(citation: Citation): string {
  if (citation.pageNumber) return `第 ${citation.pageNumber} 页`;
  return citation.position ?? '原文片段';
}

function CitationList({ citations }: { citations: Citation[] }) {
  if (!citations.length) return null;
  return (
    <Collapse
      ghost
      size="small"
      className="citation-collapse"
      items={[
        {
          key: 'citations',
          label: `参考来源（${citations.length}）`,
          children: (
            <List
              size="small"
              dataSource={citations}
              renderItem={(citation, index) => (
                <List.Item className="citation-item">
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Space wrap>
                      <Typography.Text strong>
                        [{index + 1}] {citation.documentName}
                      </Typography.Text>
                      <Tag>{citationLocation(citation)}</Tag>
                      {!citation.available && <Tag color="default">原文已删除</Tag>}
                    </Space>
                    <Typography.Paragraph
                      type={citation.available ? 'secondary' : undefined}
                      delete={!citation.available}
                      ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}
                      style={{ marginBottom: 0 }}
                    >
                      {citation.excerpt || '暂无引用摘要'}
                    </Typography.Paragraph>
                  </Space>
                </List.Item>
              )}
            />
          ),
        },
      ]}
    />
  );
}

export default function ChatPage() {
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [conversationForm] = Form.useForm<ConversationFormValues>();
  const scrollAnchor = useRef<HTMLDivElement>(null);
  const abortController = useRef<AbortController | undefined>(undefined);
  const [conversations, setConversations] = useState<ChatConversation[]>([]);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [activeConversation, setActiveConversation] = useState<ChatConversation>();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [loadingSidebar, setLoadingSidebar] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [loadError, setLoadError] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [knowledgeBaseOpen, setKnowledgeBaseOpen] = useState(false);
  const [selectedKnowledgeBaseIds, setSelectedKnowledgeBaseIds] = useState<string[]>([]);
  const [updatingKnowledgeBases, setUpdatingKnowledgeBases] = useState(false);
  const [generatingMessageId, setGeneratingMessageId] = useState<string>();

  const readyKnowledgeBases = useMemo(
    () => knowledgeBases.filter((base) => base.status === 'ready'),
    [knowledgeBases],
  );
  const knowledgeBaseMap = useMemo(
    () => new Map(knowledgeBases.map((base) => [base.id, base])),
    [knowledgeBases],
  );

  const loadSidebar = useCallback(async () => {
    setLoadingSidebar(true);
    try {
      const [conversationItems, baseItems] = await Promise.all([
        listConversations(),
        listKnowledgeBases(),
      ]);
      setConversations(conversationItems);
      setKnowledgeBases(baseItems);
      setLoadError(undefined);
      if (!conversationId && conversationItems.length) {
        navigate(`/chat/${conversationItems[0].id}`, { replace: true });
      }
    } catch (error) {
      setLoadError(extractError(error));
    } finally {
      setLoadingSidebar(false);
    }
  }, [conversationId, navigate]);

  useEffect(() => {
    void loadSidebar();
  }, [loadSidebar]);

  useEffect(() => {
    if (!conversationId) {
      setActiveConversation(undefined);
      setMessages([]);
      return;
    }
    let active = true;
    setLoadingMessages(true);
    Promise.all([getConversation(conversationId), listMessages(conversationId)])
      .then(([conversation, history]) => {
        if (!active) return;
        setActiveConversation(conversation);
        setMessages(history);
        setLoadError(undefined);
      })
      .catch((error: unknown) => {
        if (active) setLoadError(extractError(error));
      })
      .finally(() => {
        if (active) setLoadingMessages(false);
      });
    return () => {
      active = false;
      abortController.current?.abort();
    };
  }, [conversationId]);

  useEffect(() => {
    scrollAnchor.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  const create = async () => {
    const values = await conversationForm.validateFields();
    setCreating(true);
    try {
      const created = await createConversation(values);
      setConversations((current) => [created, ...current]);
      setCreateOpen(false);
      conversationForm.resetFields();
      navigate(`/chat/${created.id}`);
      message.success('会话已创建');
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setCreating(false);
    }
  };

  const removeConversation = async (id: string) => {
    try {
      await deleteConversation(id);
      const remaining = conversations.filter((item) => item.id !== id);
      setConversations(remaining);
      if (conversationId === id) {
        navigate(remaining.length ? `/chat/${remaining[0].id}` : '/chat', { replace: true });
      }
      message.success('会话已删除');
    } catch (error) {
      message.error(extractError(error));
    }
  };

  const openKnowledgeBaseSelection = () => {
    setSelectedKnowledgeBaseIds(activeConversation?.knowledgeBaseIds ?? []);
    setKnowledgeBaseOpen(true);
  };

  const saveKnowledgeBaseSelection = async () => {
    if (!conversationId || selectedKnowledgeBaseIds.length === 0) {
      message.warning('请至少选择一个可用知识库');
      return;
    }
    setUpdatingKnowledgeBases(true);
    try {
      const updated = await updateConversationKnowledgeBases(
        conversationId,
        selectedKnowledgeBaseIds,
      );
      setActiveConversation(updated);
      setConversations((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      setKnowledgeBaseOpen(false);
      message.success('会话知识库已更新');
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setUpdatingKnowledgeBases(false);
    }
  };

  const runStream = async (value: string, assistantId: string, appendQuestion: boolean) => {
    if (!conversationId || generatingMessageId) return;
    const controller = new AbortController();
    abortController.current = controller;
    setGeneratingMessageId(assistantId);
    const now = new Date().toISOString();
    if (appendQuestion) {
      const userMessage: ChatMessage = {
        id: temporaryId('user'),
        conversationId,
        role: 'user',
        content: value,
        status: 'completed',
        citations: [],
        createdAt: now,
      };
      const assistantMessage: ChatMessage = {
        id: assistantId,
        conversationId,
        role: 'assistant',
        content: '',
        status: 'streaming',
        citations: [],
        createdAt: now,
      };
      setMessages((current) => [...current, userMessage, assistantMessage]);
    } else {
      setMessages((current) =>
        current.map((item) =>
          item.id === assistantId
            ? { ...item, content: '', status: 'streaming', citations: [], errorMessage: undefined }
            : item,
        ),
      );
    }

    try {
      await streamMessage(
        conversationId,
        { question: value },
        {
          onToken: (event) => {
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId ? { ...item, content: item.content + event.content } : item,
              ),
            );
          },
          onDone: (event) => {
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      id: event.messageId,
                      content: event.content ?? item.content,
                      citations: event.citations,
                      status: 'completed',
                    }
                  : item,
              ),
            );
          },
          onError: (event) => {
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      status: event.code === 'CANCELLED' ? 'cancelled' : 'failed',
                      errorMessage: event.code === 'CANCELLED' ? undefined : event.message,
                    }
                  : item,
              ),
            );
          },
        },
        controller.signal,
      );
      setConversations((current) =>
        current.map((item) =>
          item.id === conversationId ? { ...item, updatedAt: new Date().toISOString() } : item,
        ),
      );
    } catch (error) {
      if (controller.signal.aborted) {
        setMessages((current) =>
          current.map((item) =>
            item.id === assistantId ? { ...item, status: 'cancelled' } : item,
          ),
        );
      } else {
        const errorMessage = extractError(error);
        setMessages((current) =>
          current.map((item) =>
            item.id === assistantId ? { ...item, status: 'failed', errorMessage } : item,
          ),
        );
        message.error(errorMessage);
      }
    } finally {
      abortController.current = undefined;
      setGeneratingMessageId(undefined);
    }
  };

  const send = () => {
    const value = question.trim();
    if (!value || !conversationId || generatingMessageId) return;
    setQuestion('');
    void runStream(value, temporaryId('assistant'), true);
  };

  const stop = async () => {
    if (!conversationId || !generatingMessageId) return;
    abortController.current?.abort();
    try {
      await cancelGeneration(conversationId);
    } catch (error) {
      message.warning(`本地生成已停止，服务端取消确认失败：${extractError(error)}`);
    }
  };

  const retry = (assistantMessageId: string) => {
    if (generatingMessageId) return;
    const assistantIndex = messages.findIndex((item) => item.id === assistantMessageId);
    const userMessage = messages
      .slice(0, assistantIndex)
      .reverse()
      .find((item) => item.role === 'user');
    if (!userMessage) {
      message.error('找不到对应的问题，无法重试');
      return;
    }
    void runStream(userMessage.content, assistantMessageId, false);
  };

  return (
    <div className="chat-shell">
      <Card
        className="conversation-panel"
        title="会话"
        extra={
          <Button type="primary" size="small" onClick={() => setCreateOpen(true)}>
            新建
          </Button>
        }
        styles={{ body: { padding: 8 } }}
      >
        <Spin spinning={loadingSidebar}>
          <List
            dataSource={conversations}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" /> }}
            renderItem={(item) => (
              <List.Item
                className={`conversation-item ${conversationId === item.id ? 'is-active' : ''}`}
                onClick={() => navigate(`/chat/${item.id}`)}
                actions={[
                  <Popconfirm
                    key="delete"
                    title="删除此会话？"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={(event) => {
                      event?.stopPropagation();
                      return removeConversation(item.id);
                    }}
                  >
                    <Button
                      type="text"
                      danger
                      size="small"
                      onClick={(event) => event.stopPropagation()}
                    >
                      删除
                    </Button>
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  title={<Typography.Text ellipsis>{item.title}</Typography.Text>}
                  description={`${item.knowledgeBaseIds.length} 个知识库`}
                />
              </List.Item>
            )}
          />
        </Spin>
      </Card>

      <Card
        className="chat-main"
        title={activeConversation?.title ?? '智能问答'}
        extra={
          <Space wrap>
            {activeConversation?.knowledgeBaseIds.map((id) => (
              <Tag key={id} color={knowledgeBaseMap.get(id)?.status === 'ready' ? 'blue' : 'default'}>
                {knowledgeBaseMap.get(id)?.name ?? '已删除的知识库'}
              </Tag>
            ))}
            {activeConversation && (
              <Button size="small" onClick={openKnowledgeBaseSelection}>
                调整知识库
              </Button>
            )}
          </Space>
        }
        styles={{ body: { padding: 0, minHeight: 0 } }}
      >
        {loadError && (
          <Alert
            type="error"
            showIcon
            closable
            message="数据加载失败"
            description={loadError}
            onClose={() => setLoadError(undefined)}
            className="chat-alert"
          />
        )}
        {!conversationId ? (
          <div className="chat-empty">
            <Empty description="创建会话并选择知识库后，即可开始问答">
              <Button type="primary" onClick={() => setCreateOpen(true)}>
                新建会话
              </Button>
            </Empty>
          </div>
        ) : (
          <>
            <div className="message-list" aria-live="polite">
              {loadingMessages ? (
                <div className="chat-empty"><Spin /></div>
              ) : messages.length === 0 ? (
                <div className="chat-empty">
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="问一个与所选知识库有关的问题吧" />
                </div>
              ) : (
                messages
                  .filter((item) => item.role !== 'system')
                  .map((item) => (
                    <div key={item.id} className={`message-row message-${item.role}`}>
                      <Avatar className="message-avatar">{item.role === 'user' ? '你' : 'AI'}</Avatar>
                      <div className="message-content-wrap">
                        <div className="message-bubble">
                          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                            {item.content || (item.status === 'streaming' ? '正在思考…' : '暂无内容')}
                          </Typography.Paragraph>
                        </div>
                        {item.status === 'streaming' && <Typography.Text type="secondary">生成中…</Typography.Text>}
                        {item.status === 'cancelled' && (
                          <Space>
                            <Tag>已停止</Tag>
                            <Button type="link" size="small" onClick={() => retry(item.id)}>重试</Button>
                          </Space>
                        )}
                        {item.status === 'failed' && (
                          <Alert
                            type="error"
                            showIcon
                            message={item.errorMessage ?? '回答生成失败'}
                            action={<Button size="small" onClick={() => retry(item.id)}>重试</Button>}
                          />
                        )}
                        {item.role === 'assistant' && <CitationList citations={item.citations} />}
                      </div>
                    </div>
                  ))
              )}
              <div ref={scrollAnchor} />
            </div>
            <div className="composer">
              <Input.TextArea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onPressEnter={(event) => {
                  if (!event.shiftKey) {
                    event.preventDefault();
                    send();
                  }
                }}
                autoSize={{ minRows: 2, maxRows: 6 }}
                maxLength={4000}
                placeholder="输入问题，Shift + Enter 换行"
                disabled={Boolean(generatingMessageId)}
              />
              <Space className="composer-actions">
                <Typography.Text type="secondary">回答仅基于当前会话所选知识库</Typography.Text>
                {generatingMessageId ? (
                  <Button danger onClick={() => void stop()}>停止生成</Button>
                ) : (
                  <Button type="primary" disabled={!question.trim()} onClick={send}>发送</Button>
                )}
              </Space>
            </div>
          </>
        )}
      </Card>

      <Modal
        title="新建知识库问答"
        open={createOpen}
        okText="创建会话"
        cancelText="取消"
        confirmLoading={creating}
        onOk={() => void create()}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        {readyKnowledgeBases.length === 0 && (
          <Alert
            type="warning"
            showIcon
            message="暂无可用知识库"
            description="请先在知识库管理中添加文档，并等待文档处理完成。"
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={conversationForm} layout="vertical" preserve={false}>
          <Form.Item name="title" label="会话名称" rules={[{ max: 100 }]}>
            <Input placeholder="可选，例如：产品手册问答" />
          </Form.Item>
          <Form.Item
            name="knowledgeBaseIds"
            label="知识库"
            rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个可用知识库' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择知识库"
              options={readyKnowledgeBases.map((base) => ({ label: base.name, value: base.id }))}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="调整会话知识库"
        open={knowledgeBaseOpen}
        okText="保存"
        cancelText="取消"
        confirmLoading={updatingKnowledgeBases}
        okButtonProps={{ disabled: selectedKnowledgeBaseIds.length === 0 }}
        onOk={() => void saveKnowledgeBaseSelection()}
        onCancel={() => setKnowledgeBaseOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          后续问题只会检索这里选择的知识库，历史回答和引用不会改变。
        </Typography.Paragraph>
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          value={selectedKnowledgeBaseIds}
          onChange={setSelectedKnowledgeBaseIds}
          placeholder="请至少选择一个知识库"
          options={readyKnowledgeBases.map((base) => ({ label: base.name, value: base.id }))}
          optionFilterProp="label"
        />
      </Modal>
    </div>
  );
}
