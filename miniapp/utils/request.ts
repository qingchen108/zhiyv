/** 智愈健康 API 基础配置 */
const API_BASE_URL = 'http://192.168.100.128:8080';

interface RequestOptions {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  data?: Record<string, unknown>;
  hideLoading?: boolean;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
}

/**
 * 通用请求封装
 * 自动注入 Authorization header，401 时自动重新登录
 */
export function request<T = unknown>(options: RequestOptions): Promise<ApiResponse<T>> {
  return new Promise((resolve, reject) => {
    const token = my.getStorageSync({ key: 'token' }).data || '';

    my.request({
      url: `${API_BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success: (res) => {
        const response = res.data as ApiResponse<T>;
        if (response.code === 401) {
          // token 过期，重新登录
          getApp().demoLogin();
          reject(new Error('登录已过期，请重试'));
          return;
        }
        resolve(response);
      },
      fail: (err) => {
        console.error('请求失败', options.url, err);
        reject(err);
      },
    });
  });
}

/** 获取患者档案 */
export function getPatientProfile() {
  return request<{
    id: number;
    name: string;
    phone: string;
    gender: string;
    birthDate: string;
    age: number;
    allergyHistory: string;
  }>({ url: '/api/c/patients/me' });
}

/** 更新患者档案 */
export function updatePatientProfile(data: {
  name?: string;
  gender?: string;
  birthDate?: string;
  allergyHistory?: string;
}) {
  return request({ url: '/api/c/patients/me', method: 'PUT', data });
}

/** 获取家庭成员列表 */
export function getFamilyMembers() {
  return request<Array<{
    id: number;
    name: string;
    relationship: string;
    phone: string;
    gender: string;
    birthDate: string;
    age: number;
    allergyHistory: string;
    createdAt: string;
  }>>({ url: '/api/c/patients/family-members' });
}

/** 新增家庭成员 */
export function addFamilyMember(data: {
  name: string;
  relationship: string;
  phone?: string;
  gender?: string;
  birthDate?: string;
  allergyHistory?: string;
}) {
  return request({ url: '/api/c/patients/family-members', method: 'POST', data });
}

/** 更新家庭成员 */
export function updateFamilyMember(id: number, data: {
  name: string;
  relationship: string;
  phone?: string;
  gender?: string;
  birthDate?: string;
  allergyHistory?: string;
}) {
  return request({ url: `/api/c/patients/family-members/${id}`, method: 'PUT', data });
}

/** 删除家庭成员 */
export function deleteFamilyMember(id: number) {
  return request({ url: `/api/c/patients/family-members/${id}`, method: 'DELETE' });
}