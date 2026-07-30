import { create } from 'zustand';
import { persist } from 'zustand/middleware';

// Auth Store（ADR-0007：Zustand 镜像 + localStorage 持久化 token）
interface AuthState {
  token: string | null;
  user: API.CurrentUser | null;
  setAuth: (token: string, user: API.CurrentUser) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => {
        // localStorage 单独存（request 拦截器读取，不走 store）
        localStorage.setItem('smartmed_token', token);
        localStorage.setItem('smartmed_user', JSON.stringify(user));
        set({ token, user });
      },
      clear: () => {
        localStorage.removeItem('smartmed_token');
        localStorage.removeItem('smartmed_user');
        set({ token: null, user: null });
      },
    }),
    {
      name: 'smartmed-auth', // store 持久化 key（镜像）
    },
  ),
);
