import { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Card,
  Modal,
  Form,
  Input,
  Select,
  Tag,
  Alert,
  Typography,
  Space,
  Drawer,
  App,
  type TableProps,
} from 'antd';
import {
  createApp,
  getAppPermissions,
  listMyApps,
  listPermissions,
  bindPermissions,
  type AppResponse,
  type AppSecretResponse,
  type PermissionDTO,
} from '../api/openApp';
import { extractError } from '../api/client';

function fmtTime(ts?: string): string {
  if (!ts) return '-';
  return ts.replace('T', ' ').replace(/Z$/, '').replace(/\.\d+$/, '');
}

interface SkillPackage {
  name: string;
  version: string;
  description: string;
  scopes: string[];
  download: string;
}

export default function OpenAppPage() {
  const { message } = App.useApp();
  const [apps, setApps] = useState<AppResponse[]>([]);
  const [loadingApps, setLoadingApps] = useState(false);
  const [catalog, setCatalog] = useState<PermissionDTO[]>([]);

  const [skillsOpen, setSkillsOpen] = useState(false);
  const [skills, setSkills] = useState<SkillPackage[]>([]);
  const [loadingSkills, setLoadingSkills] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [createForm] = Form.useForm<{ name: string }>();
  const [secret, setSecret] = useState<AppSecretResponse | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [manageApp, setManageApp] = useState<AppResponse | null>(null);
  const [currentPerms, setCurrentPerms] = useState<string[]>([]);
  const [permLoading, setPermLoading] = useState(false);
  const [savingPerm, setSavingPerm] = useState(false);

  const loadApps = async () => {
    setLoadingApps(true);
    try {
      setApps(await listMyApps());
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setLoadingApps(false);
    }
  };

  const openSkills = async () => {
    setSkillsOpen(true);
    setLoadingSkills(true);
    try {
      const resp = await fetch('/mcp/v1/skills');
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      setSkills(await resp.json());
    } catch (e) {
      message.error('获取 Skill 包清单失败，请先运行 scripts/package-skill.sh');
    } finally {
      setLoadingSkills(false);
    }
  };

  const scopeLabel = (code: string) =>
    catalog.find((p) => p.code === code)?.name ?? code;

  useEffect(() => {
    void loadApps();
    void (async () => {
      try {
        setCatalog(await listPermissions());
      } catch (e) {
        message.error(extractError(e));
      }
    })();
  }, []);

  const onCreateOk = async () => {
    const values = await createForm.validateFields();
    setCreateLoading(true);
    try {
      const result = await createApp(values.name);
      setSecret(result);
      setCreateOpen(false);
      createForm.resetFields();
      message.success('应用创建成功');
      await loadApps();
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setCreateLoading(false);
    }
  };

  const openDrawer = async (app: AppResponse) => {
    setManageApp(app);
    setDrawerOpen(true);
    setPermLoading(true);
    try {
      setCurrentPerms(await getAppPermissions(app.appId));
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setPermLoading(false);
    }
  };

  const savePerms = async () => {
    if (!manageApp) return;
    setSavingPerm(true);
    try {
      await bindPermissions(manageApp.appId, currentPerms);
      message.success('权限已保存');
      setDrawerOpen(false);
      await loadApps();
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setSavingPerm(false);
    }
  };

  const columns: TableProps<AppResponse>['columns'] = [
    { title: '应用名称', dataIndex: 'name', key: 'name' },
    {
      title: 'App ID',
      dataIndex: 'appId',
      key: 'appId',
      render: (v: string) => <Typography.Text copyable>{v}</Typography.Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (v: string) =>
        v === 'ACTIVE' ? <Tag color="green">ACTIVE</Tag> : <Tag>{v}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', render: (v: string) => fmtTime(v) },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Button type="link" onClick={() => openDrawer(record)}>
          管理权限
        </Button>
      ),
    },
  ];

  return (
    <Card
      title={
        <div>
          <div>我的应用</div>
          <Typography.Text type="secondary" style={{ fontSize: 13, fontWeight: 400 }}>
            创建应用并配置接口权限，供 Agent 通过 MCP 调用你的数据与能力
          </Typography.Text>
        </div>
      }
      extra={
        <Space>
          <Button onClick={openSkills}>Skill 包</Button>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            创建应用
          </Button>
        </Space>
      }
    >
      <Table
        rowKey="appId"
        loading={loadingApps}
        columns={columns}
        dataSource={apps}
        pagination={false}
        locale={{ emptyText: '还没有应用，点击右上角「创建应用」' }}
      />

      {/* 创建应用弹窗 */}
      <Modal
        title="创建应用"
        open={createOpen}
        onOk={onCreateOk}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={createLoading}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="name"
            label="应用名称"
            rules={[{ required: true, message: '请输入应用名称' }]}
          >
            <Input placeholder="例如：我的 MCP 客户端" maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 一次性密钥展示 */}
      <Modal
        title="应用创建成功"
        open={secret !== null}
        onCancel={() => setSecret(null)}
        footer={[
          <Button type="primary" key="ok" onClick={() => setSecret(null)}>
            我已保存
          </Button>,
        ]}
        closable={false}
        maskClosable={false}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="App Secret 仅在此展示一次，请立即妥善保存。"
        />
        <Typography.Paragraph>
          <Typography.Text strong>App ID：</Typography.Text>
          <Typography.Text copyable>{secret?.appId}</Typography.Text>
        </Typography.Paragraph>
        <Typography.Paragraph>
          <Typography.Text strong>App Secret：</Typography.Text>
        </Typography.Paragraph>
        <Input.TextArea value={secret?.appSecret} readOnly autoSize={{ minRows: 2, maxRows: 4 }} />
      </Modal>

      {/* 权限管理抽屉 */}
      <Drawer
        title={manageApp ? `管理权限 · ${manageApp.name}` : '管理权限'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={420}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={savingPerm} onClick={savePerms}>
              保存
            </Button>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="勾选该应用可调用的接口权限集，保存后即时生效。"
        />
        <Form layout="vertical">
          <Form.Item label="接口权限">
            <Select
              mode="multiple"
              loading={permLoading}
              value={currentPerms}
              onChange={setCurrentPerms}
              placeholder="请选择接口权限"
              options={catalog
                .filter((p) => p.opened)
                .map((p) => ({
                  value: p.code,
                  label: `${p.name}（${p.code}）`,
                }))}
            />
          </Form.Item>
        </Form>
        {currentPerms.length > 0 && (
          <Typography.Paragraph type="secondary">
            已选 {currentPerms.length} 项：{currentPerms.join('、')}
          </Typography.Paragraph>
        )}
      </Drawer>

      {/* Skill 包清单 */}
      <Modal
        title="Skill 包（按业务域分发）"
        open={skillsOpen}
        onCancel={() => setSkillsOpen(false)}
        footer={[
          <Button key="close" onClick={() => setSkillsOpen(false)}>
            关闭
          </Button>,
        ]}
        width={620}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="下载后解包，把目录放进 agent 终端的 skill 目录，再把应用的 appId 与一次性密钥交给 agent 即可。"
        />
        {loadingSkills ? (
          <Typography.Paragraph type="secondary">加载中…</Typography.Paragraph>
        ) : skills.length === 0 ? (
          <Typography.Paragraph type="secondary">
            暂无可下载的 Skill 包（服务端请先运行 scripts/package-skill.sh）。
          </Typography.Paragraph>
        ) : (
          skills.map((s) => (
            <Card
              key={s.name}
              size="small"
              style={{ marginBottom: 12 }}
              title={
                <Space>
                  <Typography.Text strong>{s.name}</Typography.Text>
                  <Tag>v{s.version}</Tag>
                </Space>
              }
              extra={
                <Button type="link" href={s.download}>
                  下载
                </Button>
              }
            >
              <Typography.Paragraph style={{ marginBottom: 8 }}>
                {s.description}
              </Typography.Paragraph>
              <Space size={[4, 4]} wrap>
                {s.scopes.map((code) => (
                  <Tag key={code}>{scopeLabel(code)}</Tag>
                ))}
              </Space>
            </Card>
          ))
        )}
      </Modal>
    </Card>
  );
}
