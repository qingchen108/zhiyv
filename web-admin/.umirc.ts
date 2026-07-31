import { defineConfig } from '@umijs/max';

// Umi Max 配置（03 ticket Q14：ADR-0007 B 端工程初始化 + MASTER.md 设计系统）
// 插件（antd/layout/request/access/model）由 @umijs/max preset 按配置字段自动启用，无需显式声明 plugins
export default defineConfig({
  // 路由（/login 公开，其余走布局 + access 守卫）
  // 菜单分流：ADMIN 看管理菜单，DOCTOR 看工作台菜单（06 ticket Q19）
  // 首页 '/' 不在此 redirect，由 src/app.ts 的 onPageChange 按角色跳转（ADMIN->/department, DOCTOR->/workspace）
  routes: [
    // /login 独立页：layout:false 脱离全局布局（Q1 退出后/未登录时无菜单无顶栏）
    { path: '/login', component: 'login', title: '登录', layout: false },
    // 注意：不套自定义 layout 路由层（@/layouts/index）。Umi Max layout 插件已提供 ant-design-pro-layout 布局，
    // 多套一层 isLayout 的 global-layout 会导致 Layout.tsx 的 filterRoutes 无法提升 children，侧边栏菜单为空。
    // 页面路由直接平铺在此，由 layout 插件自动包裹。
    // 菜单分流：ADMIN 看管理菜单，DOCTOR 看工作台菜单（06 ticket Q19）
    // 首页 '/' 不在此 redirect，由 src/app.ts 的 onPageChange 按角色跳转（ADMIN->/department, DOCTOR->/workspace）
    // ===== DOCTOR 菜单（医生工作台，06 ticket） =====
    { path: '/workspace', name: '医生工作台', component: 'workspace', access: 'isDoctor' },
    { path: '/workspace/:consultationId', name: '问诊详情', component: 'workspace/detail', access: 'isDoctor', hideInMenu: true },
    { path: '/prescription-templates', name: '处方模板', component: 'prescriptionTemplate', access: 'isDoctor' },
    // ===== ADMIN 菜单（管理） =====
    { path: '/department', name: '科室管理', component: 'department', access: 'isAdmin' },
    { path: '/doctor', name: '医生管理', component: 'doctor', access: 'isAdmin' },
    { path: '/schedule', name: '排班管理', component: 'schedule', access: 'isAdmin' },
    { path: '/drug', name: '药品管理', component: 'drug', access: 'isAdmin' },
    // ===== 共用 =====
    { path: '/change-password', name: '修改密码', component: 'changePassword', access: 'loggedIn', hideInMenu: true },
    // 根路径占位，实际跳转由 onPageChange 处理（避免 redirect 覆盖角色判断）
    { path: '/', redirect: '/department' },
    // 兜底 404（Q3：手滑输错 URL 不再白屏）
    { path: '/*', component: '404', hideInMenu: true },
  ],
  // AntD 主题 token（MASTER.md：青色系 + 健康绿 + Figtree/Noto Sans）
  antd: {
    config: {
      token: {
        colorPrimary: '#0891B2',
        colorSuccess: '#059669',
        colorError: '#DC2626',
        colorBgBase: '#ECFEFF',
        colorTextBase: '#164E63',
        colorBorder: '#A5F3FC',
        colorBgContainer: '#FFFFFF',
        fontFamily: "'Noto Sans', 'Figtree', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        fontSize: 16,
        borderRadius: 8,
      },
    },
  },
  // 请求层（拦截器/errorHandler 见 src/app.ts）
  request: {},
  // 布局（侧边栏菜单，详细配置见 src/app.ts layout）
  layout: {
    title: '智愈 SmartMed',
    locale: false,
  },
  // 启用 Umi Max 预置插件（enableBy:config，须在此声明 key 才会加载）。
  // 三者配合：initialState 注入 getInitialState -> model 提供 useModel('@@initialState') -> access 读 initialState 算权限。
  // 不声明会导致 app.ts 里导出的 access/getInitialState key 不在 validKeys，运行时报 "register failed, invalid key"。
  access: {},
  initialState: {},
  model: {},
  // 开发代理：/api -> 后端 8080
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  // 全局样式
  styles: ['@/global.less'],
  npmClient: 'npm',
});
