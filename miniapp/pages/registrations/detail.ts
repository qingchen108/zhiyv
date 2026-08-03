import { getRegistrationDetail, cancelRegistration, RegistrationRecord } from '../../utils/request';

Page({
  data: {
    id: 0,
    record: null as RegistrationRecord | null,
    canCancel: false,
    canceling: false,
  },

  onLoad(query: { id?: string }) {
    const id = Number(query.id || 0);
    this.setData({ id });
    this.loadDetail();
  },

  loadDetail() {
    getRegistrationDetail(this.data.id).then((res) => {
      if (res.code === 200 && res.data) {
        const record = res.data;
        this.setData({
          record,
          // 仅 REGISTERED 状态且未过 2h 可取消（后端也会校验，前端先行判断）
          canCancel: this.checkCancellable(record),
        });
      } else {
        my.showToast({ content: res.message || '加载失败', type: 'none' });
      }
    });
  },

  /** 就诊前 2 小时以上可取消：与后端 TimePeriod 起止时间对齐（8:00/14:00/18:00），后端强校验，此处仅粗略判断。 */
  checkCancellable(record: RegistrationRecord): boolean {
    if (record.status !== 'REGISTERED') {
      return false;
    }
    const startHour: Record<string, number> = { MORNING: 8, AFTERNOON: 14, EVENING: 18 };
    const hour = startHour[record.timePeriod] ?? 8;
    const start = new Date(`${record.scheduleDate}T${String(hour).padStart(2, '0')}:00:00`);
    const now = new Date();
    // 距开始时间不足 2 小时 → 不可取消
    return start.getTime() - now.getTime() >= 2 * 60 * 60 * 1000;
  },

  onCancel() {
    if (this.data.canceling) {
      return;
    }
    my.confirm({
      title: '取消挂号',
      content: '确定取消该挂号吗？取消后号源将释放。',
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      success: (r) => {
        if (r.confirm) {
          this.doCancel();
        }
      },
    });
  },

  doCancel() {
    this.setData({ canceling: true });
    cancelRegistration(this.data.id)
      .then((res) => {
        if (res.code === 200 && res.data) {
          my.showToast({ content: '取消成功' });
          this.setData({ record: res.data, canCancel: false });
        } else {
          my.showToast({ content: res.message || '取消失败', type: 'none' });
        }
      })
      .finally(() => {
        this.setData({ canceling: false });
      });
  },

  statusText(status: string): string {
    const map: Record<string, string> = {
      REGISTERED: '已挂号',
      VISITED: '已就诊',
      CANCELLED: '已取消',
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
});
