import { create } from 'zustand';

// Auth Store（ADR-0013 重构：access 内存 + refresh 持久化）
// Q11：access token 只存内存（XSS 泄露面最小，页面刷新即失，靠 refresh 静默换发恢复）。
// user 也存内存：刷新后由 getInitialState 用 refresh 换发 access 并重新拉 /me 恢复。
// refresh token 单独存 localStorage（stores/auth 的 REFRESH_KEY），刷新后仍可用。

const REFRESH_KEY = 'smartmed_refresh_token';

/** 持久化 refresh token（request 层 / getInitialState 共用）。 */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

/** 清空所有登录态（refresh + 内存 access/user）。 */
export function clearAuthStorage() {
  localStorage.removeItem(REFRESH_KEY);
  // 兼容清理旧版残留 key（smartmed_token / smartmed_user）
  localStorage.removeItem('smartmed_token');
  localStorage.removeItem('smartmed_user');
}

interface AuthState {
  accessToken: string | null;
  user: API.CurrentUser | null;
  /** 登录/换发成功后写入（refresh 持久化，access/user 内存）。 */
  setAuth: (accessToken: string, refreshToken: string, user: API.CurrentUser) => void;
  /** 仅换发 access（静默续期后刷新内存 token）。 */
  setAccessToken: (accessToken: string) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  setAuth: (accessToken, refreshToken, user) => {
    localStorage.setItem(REFRESH_KEY, refreshToken);
    set({ accessToken, user });
  },
  setAccessToken: (accessToken) => set({ accessToken }),
  clear: () => {
    clearAuthStorage();
    set({ accessToken: null, user: null });
  },
}));
