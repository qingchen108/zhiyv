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

  // 排班（04 ticket，ADR-0009）
  type TimePeriod = 'MORNING' | 'AFTERNOON' | 'EVENING';
  type ScheduleStatus = 'PUBLISHED' | 'SUSPENDED';

  interface Schedule {
    id: number;
    doctorId: number;
    doctorName?: string;
    departmentId: number;
    departmentName?: string;
    scheduleDate: string;
    startTime: string;
    endTime: string;
    timePeriod: TimePeriod;
    totalSlots: number;
    remainingSlots: number;
    status: ScheduleStatus;
  }

  interface CopyWeekResult {
    skipped: number;
    created: number;
  }

  // ==================== 06 医生工作台 ====================

  // 问诊状态：WAITING -> IN_PROGRESS -> COMPLETED 单向不可回退（ADR-0011）
  type ConsultationStatus = 'WAITING' | 'IN_PROGRESS' | 'COMPLETED';

  // 问诊视图对象（待接诊列表 + 问诊详情共用）
  interface Consultation {
    id: number;
    registrationId?: number;
    patientId?: number;
    doctorId?: number;
    doctorName?: string;
    departmentName?: string;
    regNo?: string;
    scheduleDate?: string;
    timePeriod?: TimePeriod;
    visitorName?: string;
    visitorGender?: string;
    visitorAge?: number;
    // 预问诊摘要全文（详情页用，06 阶段为 null，13 填充）
    preDiagnosis?: string;
    // 预问诊摘要截取前 80 字（列表用，null 时显示"暂无"）
    preDiagnosisBrief?: string;
    diagnosis?: string;
    status: ConsultationStatus;
    createdAt?: string;
  }

  // 问诊消息（DOCTOR/PATIENT）
  interface ConsultationMessage {
    id: number;
    senderType: 'DOCTOR' | 'PATIENT';
    content: string;
    createdAt: string;
  }

  // 处方明细
  interface PrescriptionItem {
    id?: number;
    drugId: number;
    drugName?: string;
    usageMethod?: string;
    dosage?: string;
    frequency?: string;
    remark?: string;
  }

  // 处方视图对象
  interface Prescription {
    id: number;
    consultationId: number;
    patientId: number;
    doctorId: number;
    diagnosis?: string;
    advice?: string;
    status: 'ACTIVE' | 'REVOKED';
    items: PrescriptionItem[];
    createdAt?: string;
  }

  // 禁忌警告
  interface ContraindicationWarning {
    type: 'ALLERGY' | 'INTERACTION';
    drugName: string;
    targetName: string;
    description: string;
  }

  // 开方响应（处方 + 警告）
  interface PrescriptionCreateResponse {
    prescription: Prescription;
    warnings: ContraindicationWarning[];
  }

  // 处方模板
  interface PrescriptionTemplate {
    id: number;
    doctorId: number;
    name: string;
    applicableDiagnosis?: string;
    advice?: string;
    items: PrescriptionItem[];
    createdAt?: string;
    updatedAt?: string;
  }

  // 病历聚合 - 挂号摘要
  interface RegistrationSummary {
    id: number;
    regNo: string;
    doctorName?: string;
    departmentName?: string;
    scheduleDate?: string;
    timePeriod?: TimePeriod;
    status: string;
    createdAt?: string;
  }

  // 病历聚合 - 问诊摘要
  interface ConsultationSummary {
    id: number;
    doctorName?: string;
    diagnosis?: string;
    status: ConsultationStatus;
    createdAt?: string;
  }

  // 病历聚合视图（以实际就诊人为中心）
  interface MedicalRecord {
    visitorName: string;
    visitorGender?: string;
    visitorAge?: number;
    allergyHistory?: string;
    registrations: RegistrationSummary[];
    consultations: ConsultationSummary[];
    prescriptions: Prescription[];
  }
}
