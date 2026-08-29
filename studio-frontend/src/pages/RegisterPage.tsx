import { useState } from 'react';
import { Form, Input, Button, Card, Typography, App } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { extractError } from '../api/client';

interface RegisterValues {
  email: string;
  password: string;
  displayName: string;
}

export default function RegisterPage() {
  const { register, login } = useAuth();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm<RegisterValues>();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: RegisterValues) => {
    setLoading(true);
    try {
      await register(values.email, values.password, values.displayName);
      message.success('注册成功，正在登录');
      await login(values.email, values.password);
      navigate('/', { replace: true });
    } catch (e) {
      message.error(extractError(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 4 }}>
          Liganex Studio
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          开放平台 · 注册
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
            name="displayName"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="展示名称" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, max: 128, message: '密码长度需为 8-128 位' },
            ]}
          >
            <Input.Password placeholder="8-128 位密码" autoComplete="new-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              注册并登录
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: 'center' }}>
          <Link to="/login">已有账号？去登录</Link>
        </div>
      </Card>
    </div>
  );
}
