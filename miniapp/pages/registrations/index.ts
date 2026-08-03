import { getRegistrations, RegistrationRecord } from '../../utils/request';

interface StatusTab {
  key: string;
  label: string;
}

Page({
  data: {
    tabs: [
      { key: '', label: '全部' },
      { key: 'REGISTERED', label: '已挂号' },
      { key: 'VISITED', label: '已就诊' },
      { key: 'CANCELLED', label: '已取消' },
    ] as StatusTab[],
    activeTab: '',
    records: [] as RegistrationRecord[],
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
    // 成员选择经 globalData 传递（tabBar 页无法带参），0 = 本人
    const app = getApp();
    const memberId = app.globalData.currentMemberId || 0;
    this.setData({
      familyMemberId: memberId,
      records: [],
      page: 1,
      finished: false,
    });
    this.loadRecords();
  },

  onPullDownRefresh() {
    this.setData({ records: [], page: 1, finished: false });
    this.loadRecords(() => {
      my.stopPullDownRefresh();
    });
  },

  onReachBottom() {
    if (this.data.finished || this.data.loading) {
      return;
    }
    this.setData({ page: this.data.page + 1 });
    this.loadRecords();
  },

  switchTab(e: { currentTarget: { dataset: { key: string } } }) {
    const key = e.currentTarget.dataset.key;
    this.setData({ activeTab: key, records: [], page: 1, finished: false });
    this.loadRecords();
  },

  loadRecords(done?: () => void) {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    getRegistrations({
      pageNum: this.data.page,
      pageSize: this.data.pageSize,
      status: this.data.activeTab || undefined,
      // 0 = 本人（后端 IS NULL 口径），>0 = 指定成员
      familyMemberId: this.data.familyMemberId,
    })
      .then((res) => {
        if (res.code === 200 && res.data) {
          const list = res.data.records || [];
          this.setData({
            records: this.data.page === 1 ? list : this.data.records.concat(list),
            total: res.data.total,
            finished: this.data.records.length + list.length >= res.data.total,
          });
        }
      })
      .finally(() => {
        this.setData({ loading: false });
        if (done) {
          done();
        }
      });
  },

  goDetail(e: { currentTarget: { dataset: { id: number } } }) {
    my.navigateTo({ url: `/pages/registrations/detail?id=${e.currentTarget.dataset.id}` });
  },

  /** 状态文案 */
  statusText(status: string): string {
    const map: Record<string, string> = {
      REGISTERED: '已挂号',
      VISITED: '已就诊',
      CANCELLED: '已取消',
    };
    return map[status] || status;
  },

  /** 班次文案 */
  periodText(period: string): string {
    const map: Record<string, string> = {
      MORNING: '上午',
      AFTERNOON: '下午',
      EVENING: '晚间',
    };
    return map[period] || period;
  },
});
