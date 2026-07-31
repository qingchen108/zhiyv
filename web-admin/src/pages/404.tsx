import { Button, Result } from 'antd';
import { history } from '@umijs/max';

// 404 页面（Q3：自定义兜底，消除手滑输错 URL 的白板体验）
export default function NotFoundPage() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="抱歉，您访问的页面不存在"
      extra={
        <Button type="primary" onClick={() => history.replace('/')}>
          返回首页
        </Button>
      }
    />
  );
}
