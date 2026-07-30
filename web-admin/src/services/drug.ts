import { request } from '@umijs/max';
import type { PageQuery } from './department';

// 药品服务（仅 ADMIN）
export async function pageDrugs(params: PageQuery = {}) {
  return request<API.Result<API.PageResponse<API.Drug>>>('/api/b/drugs', { method: 'GET', params });
}

export async function getDrug(id: number) {
  return request<API.Result<API.Drug>>(`/api/b/drugs/${id}`, { method: 'GET' });
}

export async function createDrug(data: { name: string; specification?: string; manufacturer?: string; price: number; dosageForm?: string }) {
  return request<API.Result<API.Drug>>('/api/b/drugs', { method: 'POST', data });
}

export async function updateDrug(id: number, data: { name: string; specification?: string; manufacturer?: string; price: number; dosageForm?: string }) {
  return request<API.Result<API.Drug>>(`/api/b/drugs/${id}`, { method: 'PUT', data });
}

export async function deleteDrug(id: number) {
  return request<API.Result<null>>(`/api/b/drugs/${id}`, { method: 'DELETE' });
}
