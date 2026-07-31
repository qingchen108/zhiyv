import { request } from '@umijs/max';

// 问诊服务（仅 DOCTOR，06 ticket ADR-0011）

/** 今日待接诊列表（status=WAITING，分页）。 */
export async function todayWaiting(pageNum = 1, pageSize = 10) {
  return request<API.Result<API.PageResponse<API.Consultation>>>('/api/b/consultations/today', {
    method: 'GET',
    params: { pageNum, pageSize },
  });
}

/** 问诊详情。 */
export async function getConsultation(id: number) {
  return request<API.Result<API.Consultation>>(`/api/b/consultations/${id}`, { method: 'GET' });
}

/** 接诊：WAITING -> IN_PROGRESS。 */
export async function startConsultation(id: number) {
  return request<API.Result<API.Consultation>>(`/api/b/consultations/${id}/start`, {
    method: 'PATCH',
  });
}

/** 完成：IN_PROGRESS -> COMPLETED。 */
export async function completeConsultation(id: number) {
  return request<API.Result<API.Consultation>>(`/api/b/consultations/${id}/complete`, {
    method: 'PATCH',
  });
}

/** 保存诊断（IN_PROGRESS 可改）。 */
export async function saveDiagnosis(id: number, diagnosis: string) {
  return request<API.Result<API.Consultation>>(`/api/b/consultations/${id}/diagnosis`, {
    method: 'PATCH',
    data: { diagnosis },
  });
}

/** 消息列表（DOCTOR + PATIENT）。 */
export async function listMessages(id: number) {
  return request<API.Result<API.ConsultationMessage[]>>(`/api/b/consultations/${id}/messages`, {
    method: 'GET',
  });
}

/** 发消息（仅 DOCTOR，仅 IN_PROGRESS）。 */
export async function sendMessage(id: number, content: string) {
  return request<API.Result<API.ConsultationMessage>>(`/api/b/consultations/${id}/messages`, {
    method: 'POST',
    data: { content },
  });
}

/** 患者病历聚合。 */
export async function getMedicalRecord(id: number) {
  return request<API.Result<API.MedicalRecord>>(`/api/b/consultations/${id}/medical-record`, {
    method: 'GET',
  });
}

/** 该问诊的处方列表。 */
export async function listPrescriptionsByConsultation(id: number) {
  return request<API.Result<API.Prescription[]>>(`/api/b/consultations/${id}/prescriptions`, {
    method: 'GET',
  });
}
