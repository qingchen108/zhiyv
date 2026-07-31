import { request } from '@umijs/max';

// 后端统一响应 { code, message, data }，HTTP 恒 200（ADR-0003）
// request 插件的 errorThrower 已把 code!=200 转成 throw，正常返回即 code=200

/** B 端登录：手机号 + 密码 -> access + refresh（ADR-0013）。 */
export async function login(data: { phone: string; password: string }) {
  return request<API.Result<API.LoginResult>>(
    '/api/auth/login',
    { method: 'POST', data },
  );
}

/** B 端 refresh 换发：refresh token -> 新 access + 新 refresh（ADR-0013）。 */
export async function refresh(data: { refreshToken: string }) {
  return request<API.Result<API.LoginResult>>(
    '/api/b/auth/refresh',
    { method: 'POST', data },
  );
}

/** B 端退出：吊销当前会话（Q13）。 */
export async function logoutApi() {
  return request<API.Result<null>>('/api/b/auth/logout', { method: 'POST' });
}

/** 当前用户信息（零 DB，从 JWT claim 解析）。 */
export async function getMe() {
  return request<API.Result<API.CurrentUser>>('/api/b/auth/me', { method: 'GET' });
}

/** 修改密码（ADR-0005 首登改密）。 */
export async function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request<API.Result<null>>('/api/b/auth/change-password', { method: 'POST', data });
}
