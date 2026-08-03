// 智愈健康 - 小程序入口
// 启动时调 demo-login 获取 JWT，存本地缓存
// 首次使用判断：无档案 → 跳转档案填写页；有档案 → 进入对话页
var API_BASE_URL = 'http://192.168.100.128:8080';
App({
    globalData: {
        token: '',
        patientId: 0,
        patientName: '',
        currentMemberId: 0, // 当前选中的家庭成员（0=本人）
    },
    onLaunch: function () {
        var token = my.getStorageSync({ key: 'token' }).data;
        if (token) {
            this.globalData.token = token;
            this.globalData.patientId = my.getStorageSync({ key: 'patientId' }).data || 0;
            this.globalData.patientName = my.getStorageSync({ key: 'patientName' }).data || '';
            this.checkFirstUse();
        }
        else {
            this.demoLogin();
        }
    },
    demoLogin: function () {
        var _this = this;
        my.request({
            url: "".concat(API_BASE_URL, "/api/c/auth/demo-login"),
            method: 'POST',
            success: function (res) {
                if (res.data && res.data.code === 200) {
                    var data_1 = res.data.data;
                    _this.globalData.token = data_1.token;
                    _this.globalData.patientId = data_1.patientId;
                    _this.globalData.patientName = data_1.patientName;
                    // 持久化到本地缓存
                    ['token', 'patientId', 'patientName'].forEach(function (key) {
                        my.setStorageSync({ key: key, data: data_1[key] });
                    });
                    _this.checkFirstUse();
                }
            },
            fail: function () {
                console.error('演示登录失败');
            },
        });
    },
    // 首次使用判断：调档案接口，无数据则引导填写
    checkFirstUse: function () {
        my.request({
            url: "".concat(API_BASE_URL, "/api/c/patients/me"),
            method: 'GET',
            headers: { Authorization: "Bearer ".concat(this.globalData.token) },
            success: function (res) {
                if (res.data && res.data.code === 200 && res.data.data) {
                    var profile = res.data.data;
                    if (!profile.name || profile.name === '演示患者') {
                        // 无档案 → 跳转档案填写页
                        my.switchTab({ url: '/pages/profile/index' });
                    }
                    else {
                        // 有档案 → 进入对话页
                        my.switchTab({ url: '/pages/chat/index' });
                    }
                }
            },
            fail: function () {
                console.error('档案查询失败');
            },
        });
    },
});
