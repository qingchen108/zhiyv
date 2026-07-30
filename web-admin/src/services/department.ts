import { request } from '@umijs/max';

// 通用分页查询参数
export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
  name?: string;
}

// 科室服务（仅 ADMIN，权限后端校验）
export async function pageDepartments(params: PageQuery = {}) {
  return request<API.Result<API.PageResponse<API.Department>>>('/api/b/departments', {
    method: 'GET',
    params,
  });
}

export async function getDepartment(id: number) {
  return request<API.Result<API.Department>>(`/api/b/departments/${id}`, { method: 'GET' });
}

export async function createDepartment(data: { name: string; description?: string }) {
  return request<API.Result<API.Department>>('/api/b/departments', { method: 'POST', data });
}

export async function updateDepartment(id: number, data: { name: string; description?: string }) {
  return request<API.Result<API.Department>>(`/api/b/departments/${id}`, { method: 'PUT', data });
}

export async function deleteDepartment(id: number) {
  return request<API.Result<null>>(`/api/b/departments/${id}`, { method: 'DELETE' });
}
