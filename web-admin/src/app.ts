import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { RunTimeLayoutConfig } from '@umijs/max';
import { history } from '@umijs/max';

// 权限定义已迁移至 src/access.ts（access 插件读 @/access 的默认导出），
// 不再在 app.ts 里导出 access，避免与 access 插件职责重复。

// 请求层拦截器（ADR-0007）：注入 JWT + 401 跳登录
export const request = {
  requestInterceptors: [
    (config: any) => {
      const token = localStorage.getItem('smartmed_token');
      if (token) {
        config.headers = { ...config.headers, Authorization: `Bearer ${token}` };
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response: any) => {
      return response;
    },
  ],
  errorConfig: {
    errorThrower(res: any) {
      // 后端统一响应 {code,message,data}，HTTP 恒 200；业务 code!=200 视为错误
      if (res?.data?.code && res.data.code !== 200) {
        const err = new Error(res.data.message || '请求失败') as any;
        err.code = res.data.code;
        err.data = res.data;
        throw err;
      }
    },
    errorHandler(err: any) {
      const code = err?.code ?? err?.data?.code;
      if (code === 401) {
        // 未登录/过期：清 token 跳登录
        localStorage.removeItem('smartmed_token');
        localStorage.removeItem('smartmed_user');
        if (history.location.pathname !== '/login') {
          history.push('/login');
        }
        return;
      }
      // 其他错误抛 message（由调用方 message.error 处理）
      throw err;
    },
  },
};

// 布局配置（Umi Max layout 插件：侧边栏 + 顶栏）
export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  const { currentUser } = initialState ?? {};
  return {
    title: '智愈 SmartMed',
    logo: false,
    menu: {
      locale: false,
    },
    layout: 'mix',
    contentWidth: 'Fluid',
    fixedHeader: true,
    fixSiderbar: true,
    currentUser: currentUser as any,
    // 页面切换：未登录拦截 + 首页按角色重定向
    onPageChange: (location: { pathname: string }) => {
      // 公开路由白名单（不拦截）
      const PUBLIC_PATHS = ['/login'];
      // 未登录访问任何受保护页 -> 跳登录（避免 access 路由渲染 403）
      if (!currentUser && !PUBLIC_PATHS.includes(location.pathname)) {
        history.replace('/login');
        return;
      }
      // 已登录时的首页按角色重定向（06 ticket Q19）：DOCTOR -> /workspace，ADMIN -> /department
      if (location.pathname === '/') {
        const role = currentUser?.role;
        history.replace(role === 'DOCTOR' ? '/workspace' : '/department');
      }
    },
    // 注意：勿设 route: undefined。Layout.tsx 用 {...runtimeConfig} 展开会覆盖 ProLayout 的 route 属性，
    // 导致菜单数据算成空数组、侧边栏不显示。route 由 Layout.tsx 从 clientRoutes 算好后传入，不可覆盖。
    // 退出登录（顶栏头像下拉，rightContentRender 不设以保留默认用户区 + logout 入口）
    logout: () => {
      localStorage.removeItem('smartmed_token');
      localStorage.removeItem('smartmed_user');
      history.push('/login');
    },
    // 顶栏右侧用户信息
    avatarProps: currentUser
      ? {
          title: currentUser.username,
          size: 'small',
        }
      : undefined,
  } as any;
};

// 全局初始化：从 localStorage 恢复登录态
export async function getInitialState(): Promise<{
  currentUser?: API.CurrentUser;
  settings?: Partial<LayoutSettings>;
}> {
  const token = localStorage.getItem('smartmed_token');
  const userStr = localStorage.getItem('smartmed_user');
  if (token && userStr) {
    try {
      const currentUser = JSON.parse(userStr) as API.CurrentUser;
      return { currentUser };
    } catch {
      localStorage.removeItem('smartmed_token');
      localStorage.removeItem('smartmed_user');
    }
  }
  return {};
}

// 根容器：注入 AntD 主题 token（MASTER.md 配色：青色系 + 健康绿）
import { RootFC } from '@umijs/max';
export const rootContainer: RootFC = (children) => {
  // 主题 token 通过 .umirc.ts 的 antd.config 注入更标准，此处仅做 wrapper 占位
  return children;
};
