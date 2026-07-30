import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useRequest } from 'ahooks';
import { history, useModel } from '@umijs/max';
import { login, getMe } from '@/services/auth';
import { useAuthStore } from '@/stores/auth';

const { Title, Text } = Typography;

// 登录页（ADR-0004：手机号 + 密码登录；ADR-0005：首登改密拦截）
export default function LoginPage() {
  const { setInitialState } = useModel('@@initialState');

  const { run: onSubmit, loading } = useRequest(
    async (values: { phone: string; password: string }) => {
      const res = await login(values);
      const { token, mustChangePassword } = res.data;
      // 先存 token，再拉 /me 拿用户信息（mustChangePassword 也在 /me）
      localStorage.setItem('smartmed_token', token);
      const meRes = await getMe();
      const user = meRes.data;
      useAuthStore.getState().setAuth(token, user);
      await setInitialState((s) => ({ ...s, currentUser: user }));

      if (mustChangePassword) {
        message.warning('首次登录请修改密码');
        history.push('/change-password');
      } else {
        message.success('登录成功');
        history.push('/department');
      }
    },
    {
      manual: true,
      onError: (e: any) => {
        message.error(e?.message || '登录失败');
      },
    },
  );

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--color-background)',
      }}
    >
      <Card style={{ width: 400, boxShadow: '0 4px 24px rgba(8,145,178,0.12)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ color: 'var(--color-primary)', marginBottom: 4 }}>
            智愈 SmartMed
          </Title>
          <Text type="secondary">B 端管理后台</Text>
        </div>
        <Form layout="vertical" onFinish={onSubmit} autoComplete="off">
          <Form.Item
            label="手机号"
            name="phone"
            rules={[{ required: true, message: '请输入手机号' }]}
          >
            <Input placeholder="手机号" autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            block
            size="large"
          >
            登录
          </Button>
        </Form>
        <Text type="secondary" style={{ display: 'block', marginTop: 16, textAlign: 'center', fontSize: 12 }}>
          演示账号：13800000000 / admin123
        </Text>
      </Card>
    </div>
  );
}
