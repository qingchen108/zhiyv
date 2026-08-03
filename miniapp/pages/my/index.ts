import { getPatientProfile } from '../../utils/request';

Page({
  data: {
    patientName: '',
    phone: '',
    gender: '',
    age: 0,
    avatarChar: '',
  },

  onShow() {
    const app = getApp();
    const name = app.globalData.patientName || '用户';
    this.setData({
      patientName: name,
      avatarChar: name.charAt(0),
    });
    this.loadProfile();
  },

  loadProfile() {
    getPatientProfile().then((res) => {
      if (res.code === 200 && res.data) {
        const name = res.data.name;
        this.setData({
          patientName: name,
          phone: res.data.phone,
          gender: res.data.gender || '',
          age: res.data.age || 0,
          avatarChar: name.charAt(0),
        });
      }
    });
  },

  navigateToProfile() {
    my.switchTab({ url: '/pages/profile/index' });
  },

  navigateToFamilyMembers() {
    my.navigateTo({ url: '/pages/family-members/index' });
  },

  // ==================== 记录查询入口 ====================

  goRegistrations() {
    // 我的页入口为本人视角，重置成员筛选后跳转
    getApp().globalData.currentMemberId = 0;
    my.switchTab({ url: '/pages/registrations/index' });
  },

  goConsultations() {
    my.navigateTo({ url: '/pages/consultations/index' });
  },

  goPrescriptions() {
    my.navigateTo({ url: '/pages/prescriptions/index' });
  },

  goOrders() {
    my.navigateTo({ url: '/pages/orders/index' });
  },

  goReminders() {
    my.navigateTo({ url: '/pages/reminders/index' });
  },
});