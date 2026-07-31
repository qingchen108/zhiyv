import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { RunTimeLayoutConfig, request as umiRequest } from '@umijs/max';
import { history } from '@umijs/max';
import { Avatar, Dropdown, message } from 'antd';
import type { MenuProps } from 'antd';
import { LogoutOutlined, KeyOutlined } from '@ant-design/icons';
import { logoutApi, getMe } from '@/services/auth';
import { refreshAccessToken, getAccessToken } from '@/services/refresh';
import { clearAuthStorage, getRefreshToken, useAuthStore } from '@/stores/auth';

// 权限定义已迁移至 src/access.ts（access 插件读 @/access 的默认导出），
// 不再在 app.ts 里导出 access，避免与 access 插件职责重复。

// 请求层拦截器（ADR-0007；07 增强 ADR-0013：access 内存 + 静默续期）
export const request = {
  requestInterceptors: [
    (config: any) => {
      const token = getAccessToken();
      if (token) {
        config.headers = { ...config.headers, Authorization: `Bearer ${token}` };
      }
      return config;
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
    async errorHandler(err: any, opts: any) {
      const code = err?.code ?? err?.data?.code;
      // 仅对真实 401（未登录/过期）做静默续期；业务性 401（旧密码错误等）不续期直接抛出
      if (code !== 401 || opts?.url?.includes('/api/b/auth/refresh')) {
        throw err;
      }
      // 静默续期 + 重试：一次成功换发后带新 token 重放原请求
      const ok = await refreshAccessToken();
      if (ok) {
        // 换发成功：同步内存 token 后重试（换发内部已 setAccessToken）
        const token = getAccessToken();
        if (token) {
          const retryConfig = { ...opts, headers: { ...opts?.headers, Authorization: `Bearer ${token}` } };
          return umiRequest(retryConfig.url, retryConfig);
        }
      }
      // 续期失败（refresh 失效/过期/固定窗口到期/重用检测/无 refresh）：清登录态 + 跳登录
      clearAuthStorage();
      if (history.location.pathname !== '/login') {
        history.replace('/login');
      }
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
    // 退出登录（由 rightRender 自定义下拉菜单处理，这里兜底：清态 + 整页重载）
    logout: () => {
      clearAuthStorage();
      window.location.href = '/login';
    },
    // 自定义右上角下拉菜单：修改密码 + 退出登录
    rightRender: (initialState) => {
      const user = initialState?.currentUser;
      if (!user) return null;
      const doLogout = async () => {
        // 先调后端吊销（Q13），再清态 + 整页重载（彻底脱离布局，重置所有前端状态）
        try {
          await logoutApi();
        } catch {
          // 吊销接口失败不阻塞退出（本地依旧清态）
        } finally {
          clearAuthStorage();
          window.location.href = '/login';
        }
      };
      const menuItems: MenuProps['items'] = [
        {
          key: 'change-password',
          icon: <KeyOutlined />,
          label: '修改密码',
          onClick: () => history.push('/change-password'),
        },
        {
          key: 'logout',
          icon: <LogoutOutlined />,
          label: '退出登录',
          onClick: () => {
            doLogout();
            message.loading({ content: '正在退出登录…', key: 'logout', duration: 0 });
          },
        },
      ];
      return (
        <Dropdown menu={{ items: menuItems }}>
          <span style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Avatar size="small" style={{ backgroundColor: '#0891B2' }}>
              {user.username?.charAt(0)?.toUpperCase()}
            </Avatar>
            <span>{user.username}</span>
          </span>
        </Dropdown>
      );
    },
  } as any;
};

// 全局初始化：从 refresh token 恢复登录态（Q11：access 内存，刷新后用 refresh 静默换发）
export async function getInitialState(): Promise<{
  currentUser?: API.CurrentUser;
  settings?: Partial<LayoutSettings>;
}> {
  // 无 refresh token -> 未登录
  if (!getRefreshToken()) {
    return {};
  }
  // 有 refresh：换发 access -> 拉 /me 拿用户信息
  const ok = await refreshAccessToken();
  if (!ok) {
    return {};
  }
  try {
    const meRes = await getMe();
    if (!meRes || meRes.code !== 200 || !meRes.data) {
      clearAuthStorage();
      return {};
    }
    const user = meRes.data;
    useAuthStore.getState().setAuth(useAuthStore.getState().accessToken!, getRefreshToken()!, user);
    return { currentUser: user };
  } catch {
    clearAuthStorage();
    return {};
  }
}

// 根容器：注入 AntD 主题 token（MASTER.md 配色：青色系 + 健康绿）
import { RootFC } from '@umijs/max';
export const rootContainer: RootFC = (children) => {
  // 主题 token 通过 .umirc.ts 的 antd.config 注入更标准，此处仅做 wrapper 占位
  return children;
};
