import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useRequest } from 'ahooks';
import { changePassword } from '@/services/auth';
import { clearAuthStorage } from '@/stores/auth';

const { Title, Text } = Typography;

// 修改密码页（ADR-0005 首登改密）
// Q15：改密成功后后端吊销该用户全部会话（须用新密码重新登录），故此处清态 + 整页重载回登录页。
export default function ChangePasswordPage() {
  const { run: onSubmit, loading } = useRequest(
    async (values: { oldPassword: string; newPassword: string }) => {
      await changePassword(values);
      message.success('密码修改成功，请重新登录');
      // 后端已吊销全部会话：清前端登录态 + 整页重载回登录（彻底脱离布局）
      clearAuthStorage();
      window.location.href = '/login';
    },
    {
      manual: true,
      onError: (e: any) => message.error(e?.message || '修改失败'),
    },
  );

  return (
    <div style={{ maxWidth: 480, margin: '48px auto' }}>
      <Card>
        <Title level={4} style={{ marginBottom: 4 }}>
          修改密码
        </Title>
        <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
          修改成功后需使用新密码重新登录
        </Text>
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
