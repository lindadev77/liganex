import { useState } from 'react';
import { Form, Input, Button, Card, Typography, App } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { extractError } from '../api/client';

interface LoginValues {
  email: string;
  password: string;
}

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm<LoginValues>();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: LoginValues) => {
    setLoading(true);
    try {
      await login(values.email, values.password);
      message.success('登录成功');
      navigate('/', { replace: true });
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-shell">
      <Card className="auth-card">
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <img
            src="/brand/liganex-lockup.png"
            alt="Liganex"
            style={{ width: 240, maxWidth: '100%' }}
          />
        </div>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center', marginBottom: 4 }}>
          Agent 应用生态 · 一站式工作台
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center', fontSize: 13, marginTop: 0 }}>
          连接工具、平台与服务，统一管理应用、知识库与智能问答
        </Typography.Paragraph>
        <Form form={form} layout="vertical" onFinish={onFinish} disabled={loading}>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}
          >
            <Input placeholder="you@example.com" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="请输入密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: 'center' }}>
          <Link to="/register">还没有账号？立即注册</Link>
        </div>
      </Card>
    </div>
  );
}
