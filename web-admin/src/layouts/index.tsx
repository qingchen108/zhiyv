import { Outlet } from '@umijs/max';

// 布局壳：Umi Max layout 插件提供侧边栏/顶栏，此处仅作内容出口
export default function Layout() {
  return <Outlet />;
}
