// 智愈健康 - 小程序入口
// 启动时调 demo-login 获取 JWT，存本地缓存
// 首次使用判断：无档案 → 跳转档案填写页；有档案 → 进入对话页

const API_BASE_URL = 'http://192.168.100.128:8080';

App({
  globalData: {
    token: '',
    patientId: 0,
    patientName: '',
    currentMemberId: 0, // 当前选中的家庭成员（0=本人）
  },

  onLaunch() {
    const token = my.getStorageSync({ key: 'token' }).data;
    if (token) {
      this.globalData.token = token;
      this.globalData.patientId = my.getStorageSync({ key: 'patientId' }).data || 0;
      this.globalData.patientName = my.getStorageSync({ key: 'patientName' }).data || '';
      this.checkFirstUse();
    } else {
      this.demoLogin();
    }
  },

  demoLogin() {
    my.request({
      url: `${API_BASE_URL}/api/c/auth/demo-login`,
      method: 'POST',
      success: (res) => {
        if (res.data && res.data.code === 200) {
          const data = res.data.data;
          this.globalData.token = data.token;
          this.globalData.patientId = data.patientId;
          this.globalData.patientName = data.patientName;
          // 持久化到本地缓存
          ['token', 'patientId', 'patientName'].forEach((key) => {
            my.setStorageSync({ key, data: data[key] });
          });
          this.checkFirstUse();
        }
      },
      fail: () => {
        console.error('演示登录失败');
      },
    });
  },

  // 首次使用判断：调档案接口，无数据则引导填写
  checkFirstUse() {
    my.request({
      url: `${API_BASE_URL}/api/c/patients/me`,
      method: 'GET',
      headers: { Authorization: `Bearer ${this.globalData.token}` },
      success: (res) => {
        if (res.data && res.data.code === 200 && res.data.data) {
          const profile = res.data.data;
          if (!profile.name || profile.name === '演示患者') {
            // 无档案 → 跳转档案填写页
            my.switchTab({ url: '/pages/profile/index' });
          } else {
            // 有档案 → 进入对话页
            my.switchTab({ url: '/pages/chat/index' });
          }
        }
      },
      fail: () => {
        console.error('档案查询失败');
      },
    });
  },
});