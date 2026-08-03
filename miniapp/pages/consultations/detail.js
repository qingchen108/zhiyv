"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        id: 0,
        record: null,
        messages: [],
        prescriptions: [],
    },
    onLoad: function (query) {
        var id = Number(query.id || 0);
        this.setData({ id: id });
        this.loadDetail();
    },
    loadDetail: function () {
        var _this = this;
        var id = this.data.id;
        // 详情 / 对话 / 处方 并行加载
        (0, request_1.getConsultationDetail)(id).then(function (res) {
            if (res.code === 200 && res.data) {
                var record = res.data;
                var visitorInfo = record.visitorGender || record.visitorAge
                    ? "\uFF08".concat(record.visitorGender || '').concat(record.visitorAge ? "\u00B7 ".concat(record.visitorAge, "\u5C81") : '', "\uFF09")
                    : '';
                _this.setData({ record: Object.assign({}, record, { visitorInfo: visitorInfo }) });
            }
            else {
                my.showToast({ content: res.message || '加载失败', type: 'none' });
            }
        });
        (0, request_1.getConsultationMessages)(id).then(function (res) {
            if (res.code === 200 && res.data) {
                _this.setData({ messages: res.data });
            }
            else {
                my.showToast({ content: res.message || '对话记录加载失败', type: 'none' });
            }
        });
        (0, request_1.getConsultationPrescriptions)(id).then(function (res) {
            if (res.code === 200 && res.data) {
                _this.setData({ prescriptions: res.data });
            }
            else {
                my.showToast({ content: res.message || '处方加载失败', type: 'none' });
            }
        });
    },
    goPrescription: function (e) {
        my.navigateTo({ url: "/pages/prescriptions/detail?id=".concat(e.currentTarget.dataset.id) });
    },
    statusText: function (status) {
        var map = {
            WAITING: '待接诊',
            IN_PROGRESS: '问诊中',
            COMPLETED: '已完成',
        };
        return map[status] || status;
    },
    periodText: function (period) {
        var map = {
            MORNING: '上午',
            AFTERNOON: '下午',
            EVENING: '晚间',
        };
        return map[period] || period;
    },
    senderName: function (senderType) {
        return senderType === 'DOCTOR' ? '医生' : '我';
    },
});
