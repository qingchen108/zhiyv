import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useRequest } from 'ahooks';
import { history, useModel } from '@umijs/max';
import { changePassword } from '@/services/auth';
import { useAuthStore } from '@/stores/auth';

const { Title } = Typography;

// 修改密码页（ADR-0005 首登改密）
export default function ChangePasswordPage() {
  const { setInitialState } = useModel('@@initialState');

  const { run: onSubmit, loading } = useRequest(
    async (values: { oldPassword: string; newPassword: string }) => {
      await changePassword(values);
      // 改密成功：库里已置 must_change_password=false（后端 AuthService.changePassword）。
      // 同步更新本地用户信息（token 不重签，但该字段仅用于前端展示，无害），然后直进系统，不返登录页。
      const userStr = localStorage.getItem('smartmed_user');
      if (userStr) {
        const user = JSON.parse(userStr) as API.CurrentUser;
        user.mustChangePassword = false;
        localStorage.setItem('smartmed_user', JSON.stringify(user));
        useAuthStore.getState().setAuth(localStorage.getItem('smartmed_token')!, user);
        await setInitialState((s) => ({ ...s, currentUser: user }));
      }
      message.success('密码修改成功');
      // 整页加载进首页：绕过 SPA 路由闭包竞争（同登录页理由）。
      // 按角色进首页：DOCTOR -> /workspace，ADMIN -> /department
      const me = JSON.parse(localStorage.getItem('smartmed_user') || '{}') as API.CurrentUser;
      window.location.href = me.role === 'DOCTOR' ? '/workspace' : '/department';
    },
    {
      manual: true,
      onError: (e: any) => message.error(e?.message || '修改失败'),
    },
  );

  return (
    <div style={{ maxWidth: 480, margin: '48px auto' }}>
      <Card>
        <Title level={4} style={{ marginBottom: 24 }}>
          修改密码
        </Title>
        <Form layout="vertical" onFinish={onSubmit} autoComplete="off">
          <Form.Item label="旧密码" name="oldPassword" rules={[{ required: true, message: '请输入旧密码' }]}>
            <Input.Password placeholder="旧密码" autoComplete="current-password" autoFocus />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '新密码至少 6 位' },
            ]}
          >
            <Input.Password placeholder="新密码（至少 6 位）" autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>
            确认修改
          </Button>
        </Form>
      </Card>
    </div>
  );
}
