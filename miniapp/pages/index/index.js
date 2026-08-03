Page({
    data: {
        patientName: '',
    },
    onLoad: function () {
        var app = getApp();
        this.setData({
            patientName: app.globalData.patientName || '用户',
        });
    },
    onShow: function () {
        // 刷新患者名称
        var app = getApp();
        this.setData({
            patientName: app.globalData.patientName || '用户',
        });
    },
    navigateToChat: function () {
        my.navigateTo({ url: '/pages/chat/index' });
    },
    navigateToRegistrations: function () {
        my.navigateTo({ url: '/pages/registrations/index' });
    },
    navigateToPrescriptions: function () {
        my.navigateTo({ url: '/pages/prescriptions/index' });
    },
    navigateToFamilyMembers: function () {
        my.navigateTo({ url: '/pages/family-members/index' });
    },
    navigateToProfile: function () {
        my.switchTab({ url: '/pages/profile/index' });
    },
});
