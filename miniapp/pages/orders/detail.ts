import { getDrugOrderDetail, DrugOrderRecord } from '../../utils/request';

Page({
  data: {
    id: 0,
    record: null as DrugOrderRecord | null,
  },

  onLoad(query: { id?: string }) {
    const id = Number(query.id || 0);
    this.setData({ id });
    this.loadDetail();
  },

  loadDetail() {
    getDrugOrderDetail(this.data.id).then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({ record: res.data });
      } else {
        my.showToast({ content: res.message || '加载失败', type: 'none' });
      }
    });
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      PENDING: '待支付',
      PAID: '已支付',
      DELIVERING: '配送中',
      COMPLETED: '已完成',
      CANCELLED: '已取消',
    };
    return map[status] || status;
  },

  /** 金额保留两位小数展示（后端以元为单位）。 */
  amountText(amount: number): string {
    return Number(amount || 0).toFixed(2);
  },
});
