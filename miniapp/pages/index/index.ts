Page({
  data: {
    patientName: '',
  },

  onLoad() {
    const app = getApp();
    this.setData({
      patientName: app.globalData.patientName || '用户',
    });
  },

  onShow() {
    // 刷新患者名称
    const app = getApp();
    this.setData({
      patientName: app.globalData.patientName || '用户',
    });
  },

  navigateToChat() {
    my.navigateTo({ url: '/pages/chat/index' });
  },

  navigateToRegistrations() {
    my.navigateTo({ url: '/pages/registrations/index' });
  },

  navigateToPrescriptions() {
    my.navigateTo({ url: '/pages/prescriptions/index' });
  },

  navigateToFamilyMembers() {
    my.navigateTo({ url: '/pages/family-members/index' });
  },

  navigateToProfile() {
    my.switchTab({ url: '/pages/profile/index' });
  },
});