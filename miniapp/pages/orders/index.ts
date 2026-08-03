import { getDrugOrders, DrugOrderRecord } from '../../utils/request';

Page({
  data: {
    records: [] as DrugOrderRecord[],
    total: 0,
    page: 1,
    pageSize: 10,
    loading: false,
    finished: false,
    familyMemberId: 0,
  },

  onLoad(query: { familyMemberId?: string }) {
    this.setData({ familyMemberId: Number(query.familyMemberId || 0) });
  },

  onShow() {
    this.setData({ records: [], page: 1, finished: false });
    this.loadRecords();
  },

  loadRecords(done?: () => void) {
    if (this.data.loading || this.data.finished) {
      if (done) {
        done();
      }
      return;
    }
    this.setData({ loading: true });
    const { page, pageSize } = this.data;
    getDrugOrders({
      pageNum: page,
      pageSize,
      // 0/空 = 本人，>0 = 指定成员
      familyMemberId: this.data.familyMemberId || undefined,
    })
      .then((res) => {
        if (res.code === 200 && res.data) {
          const records = this.data.records.concat(res.data.records || []);
          this.setData({
            records,
            total: res.data.total,
            page: page + 1,
            finished: records.length >= res.data.total,
          });
        } else {
          my.showToast({ content: res.message || '加载失败', type: 'none' });
        }
      })
      .finally(() => {
        this.setData({ loading: false });
        if (done) {
          done();
        }
      });
  },

  onReachBottom() {
    this.loadRecords();
  },

  onPullDownRefresh() {
    this.setData({ records: [], page: 1, finished: false });
    this.loadRecords(() => {
      my.stopPullDownRefresh();
    });
  },

  goDetail(e: any) {
    my.navigateTo({ url: `/pages/orders/detail?id=${e.currentTarget.dataset.id}` });
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
