import { refresh } from '@/services/auth';
import { getRefreshToken, useAuthStore } from '@/stores/auth';

// 静默续期模块（ADR-0013）
// 单飞：并发 401 共享同一次 refresh 换发，只发一个 POST /api/b/auth/refresh。
// 换发成功更新内存 access + 持久化新 refresh；失败清空登录态（业务 code=401 走 errorHandler 跳登录）。
// 换发后的内存 access 由 requestInterceptors 读取；请求先发无 auth -> 401 -> errorHandler 续期后重试。

let inflight: Promise<boolean> | null = null;

/** 取内存 access token（request 拦截器用；无则触发一次静默换发，请求带旧/无 token 出发，401 后重试）。 */
export function getAccessToken(): string | null {
  const inMemory = useAuthStore.getState().accessToken;
  if (inMemory) return inMemory;
  // 内存无 access（页面刷新后）：尝试用 refresh 恢复
  if (getRefreshToken()) {
    // 触发换发（不 await，请求仍会发出，401 后由 errorHandler 续期重试）
    void refreshAccessToken();
  }
  return null;
}

/**
 * 静默换发 access token。单飞。
 * @returns true=换发成功；false=失败（refresh 无效/过期/网络），调用方应清理登录态。
 */
export async function refreshAccessToken(): Promise<boolean> {
  if (inflight) return inflight;
  const rt = getRefreshToken();
  if (!rt) return false;

  inflight = (async () => {
    try {
      const res = await refresh({ refreshToken: rt });
      if (!res || res.code !== 200 || !res.data) {
        throw new Error(res?.message || '续期失败');
      }
      const { token, refreshToken } = res.data;
      // 轮换：持久化新 refresh，内存更新 access
      localStorage.setItem('smartmed_refresh_token', refreshToken);
      useAuthStore.getState().setAccessToken(token);
      return true;
    } catch {
      // 换发失败（refresh 无效/过期/重用检测/固定窗口到期）：清理登录态
      useAuthStore.getState().clear();
      return false;
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}
