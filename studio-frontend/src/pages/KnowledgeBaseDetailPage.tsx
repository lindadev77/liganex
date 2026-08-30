import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  App,
  Breadcrumb,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  type TableProps,
  type UploadFile,
} from 'antd';
import { Link, useParams } from 'react-router-dom';
import {
  DOCUMENT_POLLING_STATUSES,
  createTextDocument,
  deleteDocument,
  getKnowledgeBase,
  listDocuments,
  retryDocument,
  uploadDocument,
  validateKnowledgeFile,
} from '../api/knowledge';
import { extractError } from '../api/client';
import type { DocumentStatus, KnowledgeBase, KnowledgeDocument } from '../api/ragTypes';

const STATUS_LABEL: Record<DocumentStatus, string> = {
  pending: '等待处理',
  processing: '处理中',
  ready: '已就绪',
  failed: '处理失败',
  deleting: '删除中',
};

const STATUS_COLOR: Record<DocumentStatus, string> = {
  pending: 'default',
  processing: 'processing',
  ready: 'success',
  failed: 'error',
  deleting: 'warning',
};

interface TextFormValues {
  title: string;
  content: string;
}

function formatSize(bytes: number): string {
  if (!bytes) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default function KnowledgeBaseDetailPage() {
  const { id = '' } = useParams();
  const { message } = App.useApp();
  const [textForm] = Form.useForm<TextFormValues>();
  const pollingTimer = useRef<number | undefined>(undefined);
  const [knowledgeBase, setKnowledgeBase] = useState<KnowledgeBase>();
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [addOpen, setAddOpen] = useState(false);
  const [addMode, setAddMode] = useState<'file' | 'text'>('file');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [saving, setSaving] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [operatingId, setOperatingId] = useState<string>();

  const load = useCallback(
    async (silent = false) => {
      if (!id) return;
      if (!silent) setLoading(true);
      try {
        const [base, docs] = await Promise.all([getKnowledgeBase(id), listDocuments(id)]);
        setKnowledgeBase(base);
        setDocuments(docs);
        setLoadError(undefined);
      } catch (error) {
        if (!silent) setLoadError(extractError(error));
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [id],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const shouldPoll = useMemo(
    () => documents.some((document) => DOCUMENT_POLLING_STATUSES.has(document.status)),
    [documents],
  );

  useEffect(() => {
    window.clearTimeout(pollingTimer.current);
    if (shouldPoll) {
      pollingTimer.current = window.setTimeout(() => void load(true), 2000);
    }
    return () => window.clearTimeout(pollingTimer.current);
  }, [load, shouldPoll, documents]);

  const resetAddModal = () => {
    setFileList([]);
    setUploadProgress(0);
    textForm.resetFields();
  };

  const addDocument = async () => {
    setSaving(true);
    try {
      if (addMode === 'text') {
        const values = await textForm.validateFields();
        await createTextDocument(id, values);
      } else {
        const file = (fileList[0]?.originFileObj ?? fileList[0]) as File | undefined;
        if (!file) {
          message.warning('请选择要上传的文件');
          return;
        }
        const validationError = validateKnowledgeFile(file);
        if (validationError) {
          message.error(validationError);
          return;
        }
        await uploadDocument(id, file, setUploadProgress);
      }
      message.success('文档已提交，正在后台处理');
      setAddOpen(false);
      resetAddModal();
      await load(true);
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setSaving(false);
    }
  };

  const retry = async (documentId: string) => {
    setOperatingId(documentId);
    try {
      await retryDocument(id, documentId);
      message.success('已重新提交处理');
      await load(true);
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setOperatingId(undefined);
    }
  };

  const remove = async (documentId: string) => {
    setOperatingId(documentId);
    try {
      await deleteDocument(id, documentId);
      message.success('文档已删除');
      setDocuments((current) => current.filter((document) => document.id !== documentId));
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setOperatingId(undefined);
    }
  };

  const columns: TableProps<KnowledgeDocument>['columns'] = [
    {
      title: '文档',
      dataIndex: 'name',
      render: (name: string, document) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {document.sourceType.toUpperCase()} · {formatSize(document.sizeBytes)}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '处理状态',
      dataIndex: 'status',
      width: 240,
      render: (status: DocumentStatus, document) => (
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status]}</Tag>
          {(status === 'pending' || status === 'processing' || status === 'deleting') && (
            <Progress
              percent={document.progress}
              size="small"
              status={status === 'deleting' ? 'exception' : 'active'}
              showInfo={document.progress > 0}
            />
          )}
          {status === 'failed' && document.errorMessage && (
            <Typography.Text type="danger" ellipsis={{ tooltip: document.errorMessage }}>
              {document.errorMessage}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: '分块数',
      dataIndex: 'chunkCount',
      width: 100,
      render: (count: number) => count || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, document) => (
        <Space>
          {document.status === 'failed' && (
            <Button
              type="link"
              loading={operatingId === document.id}
              onClick={() => void retry(document.id)}
            >
              重试
            </Button>
          )}
          <Popconfirm
            title="删除文档？"
            description="删除后文档会立即停止参与问答检索。"
            okText="删除"
            cancelText="取消"
            onConfirm={() => remove(document.id)}
          >
            <Button
              type="link"
              danger
              disabled={document.status === 'deleting'}
              loading={operatingId === document.id}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Breadcrumb
        items={[
          { title: <Link to="/knowledge/bases">知识库管理</Link> },
          { title: knowledgeBase?.name ?? '知识库详情' },
        ]}
      />
      {loadError && (
        <Alert
          type="error"
          showIcon
          message="知识库详情加载失败"
          description={loadError}
          action={<Button onClick={() => void load()}>重试</Button>}
        />
      )}
      <Card loading={loading} title={knowledgeBase?.name ?? '知识库详情'}>
        <Descriptions column={{ xs: 1, sm: 2, lg: 3 }}>
          <Descriptions.Item label="说明">
            {knowledgeBase?.description || '暂无说明'}
          </Descriptions.Item>
          <Descriptions.Item label="文档">
            {documents.filter((document) => document.status === 'ready').length}/{documents.length} 已就绪
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={knowledgeBase?.status === 'ready' ? 'green' : 'default'}>
              {knowledgeBase?.status === 'ready' ? '可用' : '不可用'}
            </Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card
        title="知识文档"
        extra={
          <Button type="primary" onClick={() => setAddOpen(true)}>
            添加文档
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={documents}
          pagination={false}
          locale={{ emptyText: <Empty description="还没有文档，请上传文件或录入文本" /> }}
        />
      </Card>

      <Modal
        title="添加知识文档"
        open={addOpen}
        okText="提交处理"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void addDocument()}
        onCancel={() => {
          setAddOpen(false);
          resetAddModal();
        }}
        destroyOnHidden
      >
        <Segmented
          block
          value={addMode}
          options={[
            { label: '上传文件', value: 'file' },
            { label: '录入文本', value: 'text' },
          ]}
          onChange={(value) => setAddMode(value as 'file' | 'text')}
          style={{ marginBottom: 20 }}
        />
        {addMode === 'file' ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Upload.Dragger
              accept=".txt,.md,.markdown,.pdf,text/plain,text/markdown,application/pdf"
              maxCount={1}
              fileList={fileList}
              beforeUpload={(file) => {
                const error = validateKnowledgeFile(file);
                if (error) {
                  message.error(error);
                  return Upload.LIST_IGNORE;
                }
                setFileList([file]);
                return false;
              }}
              onRemove={() => {
                setFileList([]);
              }}
            >
              <Typography.Title level={5}>点击或拖拽文件到此处</Typography.Title>
              <Typography.Text type="secondary">
                支持 TXT、Markdown、PDF，单个文件不超过 10 MB
              </Typography.Text>
            </Upload.Dragger>
            {saving && uploadProgress > 0 && <Progress percent={uploadProgress} />}
          </Space>
        ) : (
          <Form form={textForm} layout="vertical" preserve={false}>
            <Form.Item
              name="title"
              label="文档名称"
              rules={[
                { required: true, whitespace: true, message: '请输入文档名称' },
                { max: 160, message: '名称不能超过 160 个字符' },
              ]}
            >
              <Input placeholder="例如：退款政策" />
            </Form.Item>
            <Form.Item
              name="content"
              label="文档内容"
              rules={[
                { required: true, whitespace: true, message: '请输入文档内容' },
                { max: 200_000, message: '文本内容不能超过 20 万字符' },
              ]}
            >
              <Input.TextArea rows={10} showCount maxLength={200_000} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Space>
  );
}
