import { request } from '@umijs/max';

export interface DoctorPageQuery {
  pageNum?: number;
  pageSize?: number;
  departmentId?: number;
  name?: string;
}

// 医生服务（ADMIN 全部 + DOCTOR 编辑本人）
export async function pageDoctors(params: DoctorPageQuery = {}) {
  return request<API.Result<API.PageResponse<API.Doctor>>>('/api/b/doctors', {
    method: 'GET',
    params,
  });
}

export async function getDoctor(id: number) {
  return request<API.Result<API.Doctor>>(`/api/b/doctors/${id}`, { method: 'GET' });
}

export async function getMeDoctor() {
  return request<API.Result<API.Doctor>>('/api/b/doctors/me', { method: 'GET' });
}

export async function createDoctor(data: Partial<API.Doctor> & { password?: string }) {
  return request<API.Result<API.Doctor>>('/api/b/doctors', { method: 'POST', data });
}

export async function updateDoctor(id: number, data: Partial<API.Doctor> & { password?: string }) {
  return request<API.Result<API.Doctor>>(`/api/b/doctors/${id}`, { method: 'PUT', data });
}

/** DOCTOR 编辑本人（仅 specialty/avatarUrl/intro）。 */
export async function updateMeDoctor(data: { specialty?: string; avatarUrl?: string; intro?: string }) {
  return request<API.Result<API.Doctor>>('/api/b/doctors/me', { method: 'PUT', data });
}

export async function deleteDoctor(id: number) {
  return request<API.Result<null>>(`/api/b/doctors/${id}`, { method: 'DELETE' });
}
