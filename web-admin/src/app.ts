import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { RunTimeLayoutConfig } from '@umijs/max';
import { history } from '@umijs/max';

// 权限定义（Q14：access 插件，loggedIn/isDoctor）
export function access(initialState: { currentUser?: API.CurrentUser }) {
  const currentUser = initialState.currentUser;
  return {
    loggedIn: !!currentUser,
    isDoctor: currentUser?.role === 'DOCTOR',
    isAdmin: currentUser?.role === 'ADMIN',
  };
}

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
    // 退出登录
    rightContentRender: () => {
      return undefined;
    },
    // 菜单路由由 .umirc.ts routes 驱动
    route: undefined,
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
