import { getPrescriptions, PrescriptionRecord } from '../../utils/request';

Page({
  data: {
    records: [] as PrescriptionRecord[],
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
    getPrescriptions({
      pageNum: page,
      pageSize,
      // 0/空 = 本人，>0 = 指定成员
      familyMemberId: this.data.familyMemberId || undefined,
    })
      .then((res) => {
        if (res.code === 200 && res.data) {
          // 列表预计算药品摘要（前 3 个药品名）
          const records = this.data.records.concat(
            (res.data.records || []).map((r) =>
              Object.assign({}, r, {
                drugsBrief: (r.items || []).slice(0, 3).map((i) => i.drugName).join('、'),
                drugsMore: (r.items || []).length > 3,
              }),
            ),
          );
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
    my.navigateTo({ url: `/pages/prescriptions/detail?id=${e.currentTarget.dataset.id}` });
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: '有效',
      REVOKED: '已撤销',
    };
    return map[status] || status;
  },
});
