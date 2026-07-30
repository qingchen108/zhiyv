import { request } from '@umijs/max';

export interface SchedulePageQuery {
  pageNum?: number;
  pageSize?: number;
  departmentId?: number;
  doctorId?: number;
  date?: string;
}

// 排班服务（仅 ADMIN，04 ticket ADR-0009）
export async function pageSchedules(params: SchedulePageQuery = {}) {
  return request<API.Result<API.PageResponse<API.Schedule>>>('/api/b/schedules', {
    method: 'GET',
    params,
  });
}

export async function getSchedule(id: number) {
  return request<API.Result<API.Schedule>>(`/api/b/schedules/${id}`, { method: 'GET' });
}

export async function createSchedule(data: {
  doctorId: number;
  departmentId: number;
  scheduleDate: string;
  timePeriod: string;
  totalSlots: number;
}) {
  return request<API.Result<API.Schedule>>('/api/b/schedules', { method: 'POST', data });
}

export async function updateSchedule(
  id: number,
  data: {
    doctorId: number;
    departmentId: number;
    scheduleDate: string;
    timePeriod: string;
    totalSlots: number;
  },
) {
  return request<API.Result<API.Schedule>>(`/api/b/schedules/${id}`, { method: 'PUT', data });
}

export async function deleteSchedule(id: number) {
  return request<API.Result<null>>(`/api/b/schedules/${id}`, { method: 'DELETE' });
}

export async function suspendSchedule(id: number) {
  return request<API.Result<API.Schedule>>(`/api/b/schedules/${id}/suspend`, { method: 'PATCH' });
}

export async function resumeSchedule(id: number) {
  return request<API.Result<API.Schedule>>(`/api/b/schedules/${id}/resume`, { method: 'PATCH' });
}

export async function adjustSlots(id: number, delta: number) {
  return request<API.Result<API.Schedule>>(`/api/b/schedules/${id}/slots`, {
    method: 'PATCH',
    data: { delta },
  });
}

export async function copyWeek(data: { sourceWeekStart: string; targetWeekStart: string }) {
  return request<API.Result<API.CopyWeekResult>>('/api/b/schedules/copy-week', {
    method: 'POST',
    data,
  });
}
