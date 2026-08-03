import { getPatientProfile } from '../../utils/request';

Page({
  data: {
    patientName: '',
    phone: '',
    gender: '',
    age: 0,
  },

  onShow() {
    const app = getApp();
    this.setData({
      patientName: app.globalData.patientName || '用户',
    });
    this.loadProfile();
  },

  loadProfile() {
    getPatientProfile().then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({
          patientName: res.data.name,
          phone: res.data.phone,
          gender: res.data.gender || '',
          age: res.data.age || 0,
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
});