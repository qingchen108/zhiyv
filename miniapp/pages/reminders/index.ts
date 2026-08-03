import { getReminders, ReminderRecord } from '../../utils/request';

Page({
  data: {
    records: [] as ReminderRecord[],
    loading: false,
    familyMemberId: 0,
  },

  onLoad(query: { familyMemberId?: string }) {
    this.setData({ familyMemberId: Number(query.familyMemberId || 0) });
  },

  onShow() {
    this.loadRecords();
  },

  loadRecords() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    getReminders(
      // 0/空 = 本人，>0 = 指定成员
      this.data.familyMemberId || undefined,
    )
      .then((res) => {
        if (res.code === 200 && res.data) {
          this.setData({ records: res.data });
        } else {
          my.showToast({ content: res.message || '加载失败', type: 'none' });
        }
      })
      .finally(() => {
        this.setData({ loading: false });
      });
  },

  onPullDownRefresh() {
    this.loadRecords();
    my.stopPullDownRefresh();
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: '进行中',
      DONE: '已结束',
    };
    return map[status] || status;
  },
});
