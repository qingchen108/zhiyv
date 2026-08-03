import {
  getConsultationDetail,
  getConsultationMessages,
  getConsultationPrescriptions,
  ConsultationRecord,
  ConsultationMessage,
  PrescriptionRecord,
} from '../../utils/request';

Page({
  data: {
    id: 0,
    record: null as ConsultationRecord | null,
    messages: [] as ConsultationMessage[],
    prescriptions: [] as PrescriptionRecord[],
  },

  onLoad(query: { id?: string }) {
    const id = Number(query.id || 0);
    this.setData({ id });
    this.loadDetail();
  },

  loadDetail() {
    const { id } = this.data;
    // 详情 / 对话 / 处方 并行加载
    getConsultationDetail(id).then((res) => {
      if (res.code === 200 && res.data) {
        const record = res.data;
        const visitorInfo =
          record.visitorGender || record.visitorAge
            ? `（${record.visitorGender || ''}${record.visitorAge ? `· ${record.visitorAge}岁` : ''}）`
            : '';
        this.setData({ record: Object.assign({}, record, { visitorInfo }) });
      } else {
        my.showToast({ content: res.message || '加载失败', type: 'none' });
      }
    });

    getConsultationMessages(id).then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({ messages: res.data });
      } else {
        my.showToast({ content: res.message || '对话记录加载失败', type: 'none' });
      }
    });

    getConsultationPrescriptions(id).then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({ prescriptions: res.data });
      } else {
        my.showToast({ content: res.message || '处方加载失败', type: 'none' });
      }
    });
  },

  goPrescription(e: any) {
    my.navigateTo({ url: `/pages/prescriptions/detail?id=${e.currentTarget.dataset.id}` });
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      WAITING: '待接诊',
      IN_PROGRESS: '问诊中',
      COMPLETED: '已完成',
    };
    return map[status] || status;
  },

  periodText(period: string): string {
    const map: Record<string, string> = {
      MORNING: '上午',
      AFTERNOON: '下午',
      EVENING: '晚间',
    };
    return map[period] || period;
  },

  senderName(senderType: string): string {
    return senderType === 'DOCTOR' ? '医生' : '我';
  },
});
