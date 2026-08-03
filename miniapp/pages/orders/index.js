"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
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
        this.setData({ records: [], page: 1, finished: false });
        this.loadRecords();
    },
    loadRecords: function (done) {
        var _this = this;
        if (this.data.loading || this.data.finished) {
            if (done) {
                done();
            }
            return;
        }
        this.setData({ loading: true });
        var _a = this.data, page = _a.page, pageSize = _a.pageSize;
        (0, request_1.getDrugOrders)({
            pageNum: page,
            pageSize: pageSize,
            // 0/空 = 本人，>0 = 指定成员
            familyMemberId: this.data.familyMemberId || undefined,
        })
            .then(function (res) {
            if (res.code === 200 && res.data) {
                var records = _this.data.records.concat(res.data.records || []);
                _this.setData({
                    records: records,
                    total: res.data.total,
                    page: page + 1,
                    finished: records.length >= res.data.total,
                });
            }
            else {
                my.showToast({ content: res.message || '加载失败', type: 'none' });
            }
        })
            .finally(function () {
            _this.setData({ loading: false });
            if (done) {
                done();
            }
        });
    },
    onReachBottom: function () {
        this.loadRecords();
    },
    onPullDownRefresh: function () {
        this.setData({ records: [], page: 1, finished: false });
        this.loadRecords(function () {
            my.stopPullDownRefresh();
        });
    },
    goDetail: function (e) {
        my.navigateTo({ url: "/pages/orders/detail?id=".concat(e.currentTarget.dataset.id) });
    },
    statusText: function (status) {
        var map = {
            PENDING: '待支付',
            PAID: '已支付',
            DELIVERING: '配送中',
            COMPLETED: '已完成',
            CANCELLED: '已取消',
        };
        return map[status] || status;
    },
    /** 金额保留两位小数展示（后端以元为单位）。 */
    amountText: function (amount) {
        return Number(amount || 0).toFixed(2);
    },
});
