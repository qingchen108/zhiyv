import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useRequest } from 'ahooks';
import { history, useModel } from '@umijs/max';
import { changePassword } from '@/services/auth';

const { Title } = Typography;

// 修改密码页（ADR-0005 首登改密）
export default function ChangePasswordPage() {
  const { setInitialState } = useModel('@@initialState');

  const { run: onSubmit, loading } = useRequest(
    async (values: { oldPassword: string; newPassword: string }) => {
      await changePassword(values);
      message.success('密码修改成功，请重新登录');
      // 改密后须重登（token 里 mustChangePassword 仍是旧值）
      localStorage.removeItem('smartmed_token');
      localStorage.removeItem('smartmed_user');
      await setInitialState((s) => ({ ...s, currentUser: undefined }));
      history.push('/login');
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
