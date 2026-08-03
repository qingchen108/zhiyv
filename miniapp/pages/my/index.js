"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        patientName: '',
        phone: '',
        gender: '',
        age: 0,
        avatarChar: '',
    },
    onShow: function () {
        var app = getApp();
        var name = app.globalData.patientName || '用户';
        this.setData({
            patientName: name,
            avatarChar: name.charAt(0),
        });
        this.loadProfile();
    },
    loadProfile: function () {
        var _this = this;
        (0, request_1.getPatientProfile)().then(function (res) {
            if (res.code === 200 && res.data) {
                var name = res.data.name;
                _this.setData({
                    patientName: name,
                    phone: res.data.phone,
                    gender: res.data.gender || '',
                    age: res.data.age || 0,
                    avatarChar: name.charAt(0),
                });
            }
        });
    },
    navigateToProfile: function () {
        my.switchTab({ url: '/pages/profile/index' });
    },
    navigateToFamilyMembers: function () {
        my.navigateTo({ url: '/pages/family-members/index' });
    },
    // ==================== 记录查询入口 ====================
    goRegistrations: function () {
        // 我的页入口为本人视角，重置成员筛选后跳转
        getApp().globalData.currentMemberId = 0;
        my.switchTab({ url: '/pages/registrations/index' });
    },
    goConsultations: function () {
        my.navigateTo({ url: '/pages/consultations/index' });
    },
    goPrescriptions: function () {
        my.navigateTo({ url: '/pages/prescriptions/index' });
    },
    goOrders: function () {
        my.navigateTo({ url: '/pages/orders/index' });
    },
    goReminders: function () {
        my.navigateTo({ url: '/pages/reminders/index' });
    },
});
