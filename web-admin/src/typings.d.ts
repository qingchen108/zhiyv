// 全局类型补充
declare module 'umi';
declare module '@umijs/max';

declare namespace API {
  // 当前用户（/api/b/auth/me 响应）
  interface CurrentUser {
    userId: number;
    username: string;
    role: 'ADMIN' | 'DOCTOR';
    doctorId?: number;
    mustChangePassword?: boolean;
  }

  // 统一响应 { code, message, data }
  interface Result<T = any> {
    code: number;
    message: string;
    data: T;
  }

  // 分页响应
  interface PageResponse<T = any> {
    records: T[];
    total: number;
    page: number;
    size: number;
  }

  // 科室
  interface Department {
    id: number;
    hospitalId?: number;
    name: string;
    description?: string;
  }

  // 医生
  interface Doctor {
    id: number;
    departmentId: number;
    name: string;
    gender?: string;
    birthDate?: string;
    age?: number;
    title?: string;
    specialty?: string;
    avatarUrl?: string;
    intro?: string;
    goodRate?: number;
    phone?: string;
  }

  // 药品
  interface Drug {
    id: number;
    name: string;
    specification?: string;
    manufacturer?: string;
    price: number;
    dosageForm?: string;
  }
}
