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

// ==================== 记录查询（08 ticket） ====================

/** 通用分页结构 */
export interface PageData<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/** 挂号记录 */
export interface RegistrationRecord {
  id: number;
  regNo: string;
  patientId: number;
  familyMemberId?: number;
  visitorName: string;
  scheduleId: number;
  doctorId: number;
  doctorName: string;
  departmentName: string;
  scheduleDate: string;
  timePeriod: string;
  status: string;
  createdAt: string;
}

/** 问诊记录 */
export interface ConsultationRecord {
  id: number;
  registrationId: number;
  patientId: number;
  doctorId: number;
  doctorName: string;
  departmentName: string;
  regNo: string;
  scheduleDate: string;
  timePeriod: string;
  visitorName: string;
  visitorGender?: string;
  visitorAge?: number;
  preDiagnosis?: string;
  preDiagnosisBrief?: string;
  diagnosis?: string;
  status: string;
  createdAt: string;
}

/** 对话消息 */
export interface ConsultationMessage {
  id: number;
  senderType: string;
  content: string;
  createdAt: string;
}

/** 处方记录 */
export interface PrescriptionItem {
  id: number;
  drugId: number;
  drugName: string;
  usageMethod?: string;
  dosage?: string;
  frequency?: string;
  remark?: string;
}

export interface PrescriptionRecord {
  id: number;
  consultationId: number;
  patientId: number;
  doctorId: number;
  doctorName: string;
  diagnosis: string;
  advice?: string;
  status: string;
  items?: PrescriptionItem[];
  createdAt: string;
}

/** 购药订单 */
export interface DrugOrderRecord {
  id: number;
  patientId: number;
  prescriptionId: number;
  pharmacyId: number;
  pharmacyName: string;
  pharmacyAddress?: string;
  totalAmount: number;
  status: string;
  deliveryInfo?: string;
  createdAt: string;
}

/** 用药提醒 */
export interface ReminderRecord {
  id: number;
  prescriptionId: number;
  drugId: number;
  drugName: string;
  nextRemindAt: string;
  frequency?: string;
  dosage?: string;
  remark?: string;
  status: string;
}

/** 健康档案汇总（以实际就诊人为中心） */
export interface HealthProfile {
  visitorName: string;
  visitorGender?: string;
  visitorAge?: number;
  allergyHistory?: string;
  registrations?: Array<{
    id: number;
    regNo: string;
    doctorName: string;
    departmentName: string;
    scheduleDate: string;
    timePeriod: string;
    status: string;
    createdAt: string;
  }>;
  consultations?: Array<{
    id: number;
    doctorName: string;
    diagnosis?: string;
    status: string;
    createdAt: string;
  }>;
  prescriptions?: PrescriptionRecord[];
  orders?: DrugOrderRecord[];
  reminders?: ReminderRecord[];
}

/** 挂号列表（status: REGISTERED/VISITED/CANCELLED，familyMemberId: 0=本人/空=全部） */
export function getRegistrations(params: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
  familyMemberId?: number;
}) {
  return request<PageData<RegistrationRecord>>({ url: '/api/c/registrations', data: params });
}

/** 挂号详情 */
export function getRegistrationDetail(id: number) {
  return request<RegistrationRecord>({ url: `/api/c/registrations/${id}` });
}

/** 取消挂号（就诊前 2h 以上） */
export function cancelRegistration(id: number) {
  return request<RegistrationRecord>({ url: `/api/c/registrations/${id}/cancel`, method: 'PATCH' });
}

/** 问诊记录列表 */
export function getConsultations(params: {
  pageNum?: number;
  pageSize?: number;
  familyMemberId?: number;
}) {
  return request<PageData<ConsultationRecord>>({ url: '/api/c/records/consultations', data: params });
}

/** 问诊详情 */
export function getConsultationDetail(id: number) {
  return request<ConsultationRecord>({ url: `/api/c/records/consultations/${id}` });
}

/** 问诊对话记录 */
export function getConsultationMessages(id: number) {
  return request<ConsultationMessage[]>({ url: `/api/c/records/consultations/${id}/messages` });
}

/** 问诊处方列表 */
export function getConsultationPrescriptions(id: number) {
  return request<PrescriptionRecord[]>({ url: `/api/c/records/consultations/${id}/prescriptions` });
}

/** 处方列表 */
export function getPrescriptions(params: {
  pageNum?: number;
  pageSize?: number;
  familyMemberId?: number;
}) {
  return request<PageData<PrescriptionRecord>>({ url: '/api/c/records/prescriptions', data: params });
}

/** 处方详情 */
export function getPrescriptionDetail(id: number) {
  return request<PrescriptionRecord>({ url: `/api/c/records/prescriptions/${id}` });
}

/** 购药订单列表 */
export function getDrugOrders(params: {
  pageNum?: number;
  pageSize?: number;
  familyMemberId?: number;
}) {
  return request<PageData<DrugOrderRecord>>({ url: '/api/c/records/orders', data: params });
}

/** 购药订单详情 */
export function getDrugOrderDetail(id: number) {
  return request<DrugOrderRecord>({ url: `/api/c/records/orders/${id}` });
}

/** 用药提醒列表 */
export function getReminders(familyMemberId?: number) {
  return request<ReminderRecord[]>({
    url: '/api/c/records/reminders',
    data: familyMemberId ? { familyMemberId } : {},
  });
}

/** 健康档案汇总 */
export function getHealthProfile(familyMemberId?: number) {
  return request<HealthProfile>({
    url: '/api/c/records/health-profile',
    data: familyMemberId ? { familyMemberId } : {},
  });
}