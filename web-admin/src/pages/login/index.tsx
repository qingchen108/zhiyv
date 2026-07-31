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
      // 后端统一响应 {code,message,data}，HTTP 恒 200。errorThrower 可能因 res 结构差异未抛错，
      // 此处显式校验 code，确保密码错误等业务错误有友好提示（而非读到 undefined 报错）。
      if (!res || res.code !== 200 || !res.data) {
        throw new Error(res?.message || '登录失败');
      }
      const { token, refreshToken, mustChangePassword } = res.data;
      // 先持久化 refresh + 内存 access，再拉 /me 拿用户信息（mustChangePassword 也在 /me）
      localStorage.setItem('smartmed_refresh_token', refreshToken);
      useAuthStore.getState().setAccessToken(token);
      const meRes = await getMe();
      if (!meRes || meRes.code !== 200 || !meRes.data) {
        throw new Error(meRes?.message || '获取用户信息失败');
      }
      const user = meRes.data;
      useAuthStore.getState().setAuth(token, refreshToken, user);
      await setInitialState((s) => ({ ...s, currentUser: user }));

      if (mustChangePassword) {
        message.warning('首次登录请修改密码');
        window.location.href = '/change-password';
      } else {
        message.success('登录成功');
        // 整页加载进入首页：绕过 SPA 路由闭包竞争（onPageChange 的 currentUser 闭包此时还是旧值，
        // 用 history.replace 会被未登录拦截误判回 /login）。登录低频，整页加载可接受。
        // 按角色跳首页（06 ticket Q19）：DOCTOR -> /workspace，ADMIN -> /department
        window.location.href = user.role === 'DOCTOR' ? '/workspace' : '/department';
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
