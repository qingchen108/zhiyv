"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        id: 0,
        record: null,
    },
    onLoad: function (query) {
        var id = Number(query.id || 0);
        this.setData({ id: id });
        this.loadDetail();
    },
    loadDetail: function () {
        var _this = this;
        (0, request_1.getDrugOrderDetail)(this.data.id).then(function (res) {
            if (res.code === 200 && res.data) {
                _this.setData({ record: res.data });
            }
            else {
                my.showToast({ content: res.message || '加载失败', type: 'none' });
            }
        });
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
