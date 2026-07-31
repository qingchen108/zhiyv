import { request } from '@umijs/max';

// 处方与处方模板服务（仅 DOCTOR，06 ticket）

/** 开方（含禁忌检测，返回 warnings）。 */
export async function createPrescription(data: {
  consultationId: number;
  diagnosis?: string;
  advice?: string;
  force?: boolean;
  items: API.PrescriptionItem[];
}) {
  return request<API.Result<API.PrescriptionCreateResponse>>('/api/b/prescriptions', {
    method: 'POST',
    data,
  });
}

/** 处方详情。 */
export async function getPrescription(id: number) {
  return request<API.Result<API.Prescription>>(`/api/b/prescriptions/${id}`, { method: 'GET' });
}

/** 本人模板列表（分页）。 */
export async function pageTemplates(pageNum = 1, pageSize = 10) {
  return request<API.Result<API.PageResponse<API.PrescriptionTemplate>>>(
    '/api/b/prescription-templates',
    { method: 'GET', params: { pageNum, pageSize } },
  );
}

/** 新建模板。 */
export async function createTemplate(data: {
  name: string;
  applicableDiagnosis?: string;
  advice?: string;
  items: API.PrescriptionItem[];
}) {
  return request<API.Result<API.PrescriptionTemplate>>('/api/b/prescription-templates', {
    method: 'POST',
    data,
  });
}

/** 编辑模板。 */
export async function updateTemplate(
  id: number,
  data: {
    name: string;
    applicableDiagnosis?: string;
    advice?: string;
    items: API.PrescriptionItem[];
  },
) {
  return request<API.Result<API.PrescriptionTemplate>>(`/api/b/prescription-templates/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 删除模板。 */
export async function deleteTemplate(id: number) {
  return request<API.Result<null>>(`/api/b/prescription-templates/${id}`, { method: 'DELETE' });
}
