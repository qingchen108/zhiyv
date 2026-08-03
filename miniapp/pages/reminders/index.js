"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        records: [],
        loading: false,
        familyMemberId: 0,
    },
    onLoad: function (query) {
        this.setData({ familyMemberId: Number(query.familyMemberId || 0) });
    },
    onShow: function () {
        this.loadRecords();
    },
    loadRecords: function () {
        var _this = this;
        if (this.data.loading) {
            return;
        }
        this.setData({ loading: true });
        (0, request_1.getReminders)(
        // 0/空 = 本人，>0 = 指定成员
        this.data.familyMemberId || undefined)
            .then(function (res) {
            if (res.code === 200 && res.data) {
                _this.setData({ records: res.data });
            }
            else {
                my.showToast({ content: res.message || '加载失败', type: 'none' });
            }
        })
            .finally(function () {
            _this.setData({ loading: false });
        });
    },
    onPullDownRefresh: function () {
        this.loadRecords();
        my.stopPullDownRefresh();
    },
    statusText: function (status) {
        var map = {
            ACTIVE: '进行中',
            DONE: '已结束',
        };
        return map[status] || status;
    },
});
