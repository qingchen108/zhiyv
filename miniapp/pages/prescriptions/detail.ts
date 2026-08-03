import { getPrescriptionDetail, PrescriptionRecord } from '../../utils/request';

Page({
  data: {
    id: 0,
    record: null as PrescriptionRecord | null,
  },

  onLoad(query: { id?: string }) {
    const id = Number(query.id || 0);
    this.setData({ id });
    this.loadDetail();
  },

  loadDetail() {
    getPrescriptionDetail(this.data.id).then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({ record: res.data });
      } else {
        my.showToast({ content: res.message || '加载失败', type: 'none' });
      }
    });
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: '有效',
      REVOKED: '已撤销',
    };
    return map[status] || status;
  },
});
