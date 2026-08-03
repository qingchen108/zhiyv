"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        tabs: [
            { key: '', label: '全部' },
            { key: 'REGISTERED', label: '已挂号' },
            { key: 'VISITED', label: '已就诊' },
            { key: 'CANCELLED', label: '已取消' },
        ],
        activeTab: '',
        records: [],
        total: 0,
        page: 1,
        pageSize: 10,
        loading: false,
        finished: false,
        familyMemberId: 0,
    },
    onLoad: function (query) {
        this.setData({ familyMemberId: Number(query.familyMemberId || 0) });
    },
    onShow: function () {
        // 成员选择经 globalData 传递（tabBar 页无法带参），0 = 本人
        var app = getApp();
        var memberId = app.globalData.currentMemberId || 0;
        this.setData({
            familyMemberId: memberId,
            records: [],
            page: 1,
            finished: false,
        });
        this.loadRecords();
    },
    onPullDownRefresh: function () {
        this.setData({ records: [], page: 1, finished: false });
        this.loadRecords(function () {
            my.stopPullDownRefresh();
        });
    },
    onReachBottom: function () {
        if (this.data.finished || this.data.loading) {
            return;
        }
        this.setData({ page: this.data.page + 1 });
        this.loadRecords();
    },
    switchTab: function (e) {
        var key = e.currentTarget.dataset.key;
        this.setData({ activeTab: key, records: [], page: 1, finished: false });
        this.loadRecords();
    },
    loadRecords: function (done) {
        var _this = this;
        if (this.data.loading) {
            return;
        }
        this.setData({ loading: true });
        (0, request_1.getRegistrations)({
            pageNum: this.data.page,
            pageSize: this.data.pageSize,
            status: this.data.activeTab || undefined,
            // 0 = 本人（后端 IS NULL 口径），>0 = 指定成员
            familyMemberId: this.data.familyMemberId,
        })
            .then(function (res) {
            if (res.code === 200 && res.data) {
                var list = res.data.records || [];
                _this.setData({
                    records: _this.data.page === 1 ? list : _this.data.records.concat(list),
                    total: res.data.total,
                    finished: _this.data.records.length + list.length >= res.data.total,
                });
            }
        })
            .finally(function () {
            _this.setData({ loading: false });
            if (done) {
                done();
            }
        });
    },
    goDetail: function (e) {
        my.navigateTo({ url: "/pages/registrations/detail?id=".concat(e.currentTarget.dataset.id) });
    },
    /** 状态文案 */
    statusText: function (status) {
        var map = {
            REGISTERED: '已挂号',
            VISITED: '已就诊',
            CANCELLED: '已取消',
        };
        return map[status] || status;
    },
    /** 班次文案 */
    periodText: function (period) {
        var map = {
            MORNING: '上午',
            AFTERNOON: '下午',
            EVENING: '晚间',
        };
        return map[period] || period;
    },
});
