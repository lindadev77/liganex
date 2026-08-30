import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../api/knowledge';
import { extractError } from '../api/client';
import type { KnowledgeBase } from '../api/ragTypes';

interface KnowledgeBaseFormValues {
  name: string;
  description?: string;
}

export default function KnowledgeBaseListPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm<KnowledgeBaseFormValues>();
  const [items, setItems] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [editing, setEditing] = useState<KnowledgeBase | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      setItems(await listKnowledgeBases());
    } catch (error) {
      setLoadError(extractError(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (item: KnowledgeBase) => {
    setEditing(item);
    form.setFieldsValue({ name: item.name, description: item.description });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateKnowledgeBase(editing.id, values);
        message.success('知识库已更新');
      } else {
        await createKnowledgeBase(values);
        message.success('知识库已创建');
      }
      setModalOpen(false);
      form.resetFields();
      await load();
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    setDeletingId(id);
    try {
      await deleteKnowledgeBase(id);
      message.success('知识库已删除');
      setItems((current) => current.filter((item) => item.id !== id));
    } catch (error) {
      message.error(extractError(error));
    } finally {
      setDeletingId(undefined);
    }
  };

  return (
    <Card
      title={
        <div>
          <div>知识库管理</div>
          <Typography.Text type="secondary" style={{ fontSize: 13, fontWeight: 400 }}>
            上传文档或录入文本，构建可被智能问答检索的知识来源
          </Typography.Text>
        </div>
      }
      extra={
        <Button type="primary" onClick={openCreate}>
          新建知识库
        </Button>
      }
    >
      {loadError && (
        <Alert
          type="error"
          showIcon
          message="知识库加载失败"
          description={loadError}
          action={<Button onClick={() => void load()}>重试</Button>}
          style={{ marginBottom: 16 }}
        />
      )}

      <List
        loading={loading}
        dataSource={items}
        locale={{
          emptyText: loading ? null : (
            <Empty description="还没有知识库">
              <Button type="primary" onClick={openCreate}>
                创建第一个知识库
              </Button>
            </Empty>
          ),
        }}
        grid={{ gutter: 16, xs: 1, sm: 1, md: 2, xl: 3 }}
        renderItem={(item) => (
          <List.Item>
            <Card
              hoverable
              className="knowledge-card"
              onClick={() => navigate(`/knowledge/bases/${item.id}`)}
              actions={[
                <Button
                  type="link"
                  key="edit"
                  onClick={(event) => {
                    event.stopPropagation();
                    openEdit(item);
                  }}
                >
                  编辑
                </Button>,
                <span key="delete" onClick={(event) => event.stopPropagation()}>
                  <Popconfirm
                    title="删除知识库？"
                    description="删除后文档会立即停止参与检索，此操作不可撤销。"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true, loading: deletingId === item.id }}
                    onConfirm={() => remove(item.id)}
                  >
                    <Button type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                </span>,
              ]}
            >
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                  <Typography.Title level={4} ellipsis style={{ margin: 0 }}>
                    {item.name}
                  </Typography.Title>
                  <Tag color={item.status === 'ready' ? 'green' : 'default'}>
                    {item.status === 'ready' ? '可用' : item.status === 'deleting' ? '删除中' : '已停用'}
                  </Tag>
                </Space>
                <Typography.Paragraph
                  type="secondary"
                  ellipsis={{ rows: 2 }}
                  style={{ minHeight: 44, margin: 0 }}
                >
                  {item.description || '暂无说明'}
                </Typography.Paragraph>
                <Typography.Text type="secondary">
                  {item.documentCount === undefined
                    ? '进入详情管理知识文档'
                    : `${item.readyDocumentCount ?? 0}/${item.documentCount} 个文档已就绪`}
                </Typography.Text>
              </Space>
            </Card>
          </List.Item>
        )}
      />

      <Modal
        title={editing ? '编辑知识库' : '新建知识库'}
        open={modalOpen}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void save()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="名称"
            rules={[
              { required: true, whitespace: true, message: '请输入知识库名称' },
              { max: 80, message: '名称不能超过 80 个字符' },
            ]}
          >
            <Input placeholder="例如：产品使用手册" autoFocus />
          </Form.Item>
          <Form.Item
            name="description"
            label="说明"
            rules={[{ max: 500, message: '说明不能超过 500 个字符' }]}
          >
            <Input.TextArea rows={4} placeholder="说明知识库包含的内容和使用范围" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
