"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        members: [],
        loading: false,
        currentMemberId: 0,
        currentMemberName: '',
        patientName: '',
    },
    onShow: function () {
        var app = getApp();
        var memberId = app.globalData.currentMemberId || 0;
        this.setData({
            currentMemberId: memberId,
            patientName: app.globalData.patientName || '',
        });
        this.loadMembers();
    },
    loadMembers: function () {
        var _this = this;
        this.setData({ loading: true });
        (0, request_1.getFamilyMembers)().then(function (res) {
            if (res.code === 200 && res.data) {
                var enriched = res.data.map(function (m) { return (__assign(__assign({}, m), { firstChar: m.name ? m.name.charAt(0) : '', canDelete: m.name !== _this.data.patientName })); });
                _this.setData({ members: enriched });
                _this.updateCurrentMemberName();
            }
        }).finally(function () {
            _this.setData({ loading: false });
        });
    },
    updateCurrentMemberName: function () {
        var _this = this;
        var member = this.data.members.find(function (m) { return m.id === _this.data.currentMemberId; });
        this.setData({ currentMemberName: member ? member.name : '未知' });
    },
    // 判断是否为患者本人（通过姓名匹配）
    isSelf: function (name) {
        return this.data.patientName === name;
    },
    navigateToAdd: function () {
        my.navigateTo({ url: '/pages/family-member-edit/index' });
    },
    navigateToEdit: function (e) {
        var id = e.currentTarget.dataset.id;
        my.navigateTo({ url: "/pages/family-member-edit/index?id=".concat(id) });
    },
    // 设为当前就诊人
    onSetActive: function (e) {
        var _a = e.currentTarget.dataset, id = _a.id, name = _a.name;
        var app = getApp();
        app.globalData.currentMemberId = id;
        this.setData({ currentMemberId: id, currentMemberName: name });
        my.showToast({ content: "\u5DF2\u5207\u6362\u81F3\u300C".concat(name, "\u300D") });
    },
    // 切换回本人
    onSwitchToSelf: function () {
        var app = getApp();
        app.globalData.currentMemberId = 0;
        this.setData({ currentMemberId: 0, currentMemberName: '' });
        my.showToast({ content: '已切换回本人' });
    },
    onDelete: function (e) {
        var _this = this;
        var _a = e.currentTarget.dataset, id = _a.id, name = _a.name;
        if (this.isSelf(name)) {
            my.showToast({ content: '本人不可删除', type: 'none' });
            return;
        }
        my.confirm({
            title: '确认删除',
            content: "\u786E\u5B9A\u5220\u9664\u5BB6\u5EAD\u6210\u5458\u300C".concat(name, "\u300D\u5417\uFF1F"),
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            success: function (res) {
                if (res.confirm) {
                    (0, request_1.deleteFamilyMember)(id).then(function (res2) {
                        if (res2.code === 200) {
                            my.showToast({ content: '删除成功' });
                            _this.loadMembers();
                        }
                        else {
                            my.showToast({ content: res2.message || '删除失败', type: 'none' });
                        }
                    });
                }
            },
        });
    },
});
