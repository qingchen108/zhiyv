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
        (0, request_1.getPrescriptionDetail)(this.data.id).then(function (res) {
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
            ACTIVE: '有效',
            REVOKED: '已撤销',
        };
        return map[status] || status;
    },
});
