import { defineConfig } from '@umijs/max';

// Umi Max 配置（03 ticket Q14：ADR-0007 B 端工程初始化 + MASTER.md 设计系统）
// 插件（antd/layout/request/access/model）由 @umijs/max preset 按配置字段自动启用，无需显式声明 plugins
export default defineConfig({
  // 路由（/login 公开，其余走布局 + access 守卫）
  routes: [
    { path: '/login', component: 'login', title: '登录' },
    { path: '/', redirect: '/department' },
    {
      path: '/',
      component: '@/layouts/index',
      routes: [
        { path: '/department', name: '科室管理', component: 'department', access: 'loggedIn' },
        { path: '/doctor', name: '医生管理', component: 'doctor', access: 'loggedIn' },
        { path: '/schedule', name: '排班管理', component: 'schedule', access: 'loggedIn' },
        { path: '/drug', name: '药品管理', component: 'drug', access: 'loggedIn' },
        { path: '/change-password', name: '修改密码', component: 'changePassword', access: 'loggedIn' },
      ],
    },
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
