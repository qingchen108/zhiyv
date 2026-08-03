"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        id: 0,
        record: null,
        canCancel: false,
        canceling: false,
    },
    onLoad: function (query) {
        var id = Number(query.id || 0);
        this.setData({ id: id });
        this.loadDetail();
    },
    loadDetail: function () {
        var _this = this;
        (0, request_1.getRegistrationDetail)(this.data.id).then(function (res) {
            if (res.code === 200 && res.data) {
                var record = res.data;
                _this.setData({
                    record: record,
                    // 仅 REGISTERED 状态且未过 2h 可取消（后端也会校验，前端先行判断）
                    canCancel: _this.checkCancellable(record),
                });
            }
            else {
                my.showToast({ content: res.message || '加载失败', type: 'none' });
            }
        });
    },
    /** 就诊前 2 小时以上可取消：与后端 TimePeriod 起止时间对齐（8:00/14:00/18:00），后端强校验，此处仅粗略判断。 */
    checkCancellable: function (record) {
        var _a;
        if (record.status !== 'REGISTERED') {
            return false;
        }
        var startHour = { MORNING: 8, AFTERNOON: 14, EVENING: 18 };
        var hour = (_a = startHour[record.timePeriod]) !== null && _a !== void 0 ? _a : 8;
        var start = new Date("".concat(record.scheduleDate, "T").concat(String(hour).padStart(2, '0'), ":00:00"));
        var now = new Date();
        // 距开始时间不足 2 小时 → 不可取消
        return start.getTime() - now.getTime() >= 2 * 60 * 60 * 1000;
    },
    onCancel: function () {
        var _this = this;
        if (this.data.canceling) {
            return;
        }
        my.confirm({
            title: '取消挂号',
            content: '确定取消该挂号吗？取消后号源将释放。',
            confirmButtonText: '确定取消',
            cancelButtonText: '再想想',
            success: function (r) {
                if (r.confirm) {
                    _this.doCancel();
                }
            },
        });
    },
    doCancel: function () {
        var _this = this;
        this.setData({ canceling: true });
        (0, request_1.cancelRegistration)(this.data.id)
            .then(function (res) {
            if (res.code === 200 && res.data) {
                my.showToast({ content: '取消成功' });
                _this.setData({ record: res.data, canCancel: false });
            }
            else {
                my.showToast({ content: res.message || '取消失败', type: 'none' });
            }
        })
            .finally(function () {
            _this.setData({ canceling: false });
        });
    },
    statusText: function (status) {
        var map = {
            REGISTERED: '已挂号',
            VISITED: '已就诊',
            CANCELLED: '已取消',
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
});
