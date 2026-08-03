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
exports.request = request;
exports.getPatientProfile = getPatientProfile;
exports.updatePatientProfile = updatePatientProfile;
exports.getFamilyMembers = getFamilyMembers;
exports.addFamilyMember = addFamilyMember;
exports.updateFamilyMember = updateFamilyMember;
exports.deleteFamilyMember = deleteFamilyMember;
exports.getRegistrations = getRegistrations;
exports.getRegistrationDetail = getRegistrationDetail;
exports.cancelRegistration = cancelRegistration;
exports.getConsultations = getConsultations;
exports.getConsultationDetail = getConsultationDetail;
exports.getConsultationMessages = getConsultationMessages;
exports.getConsultationPrescriptions = getConsultationPrescriptions;
exports.getPrescriptions = getPrescriptions;
exports.getPrescriptionDetail = getPrescriptionDetail;
exports.getDrugOrders = getDrugOrders;
exports.getDrugOrderDetail = getDrugOrderDetail;
exports.getReminders = getReminders;
exports.getHealthProfile = getHealthProfile;
/** 智愈健康 API 基础配置 */
var API_BASE_URL = 'http://192.168.100.128:8080';
/**
 * 通用请求封装
 * 自动注入 Authorization header，401 时自动重新登录
 */
function request(options) {
    return new Promise(function (resolve, reject) {
        var token = my.getStorageSync({ key: 'token' }).data || '';
        my.request({
            url: "".concat(API_BASE_URL).concat(options.url),
            method: options.method || 'GET',
            data: options.data,
            headers: __assign({ 'Content-Type': 'application/json' }, (token ? { Authorization: "Bearer ".concat(token) } : {})),
            success: function (res) {
                var response = res.data;
                if (response.code === 401) {
                    // token 过期，重新登录
                    getApp().demoLogin();
                    reject(new Error('登录已过期，请重试'));
                    return;
                }
                resolve(response);
            },
            fail: function (err) {
                console.error('请求失败', options.url, err);
                reject(err);
            },
        });
    });
}
/** 获取患者档案 */
function getPatientProfile() {
    return request({ url: '/api/c/patients/me' });
}
/** 更新患者档案 */
function updatePatientProfile(data) {
    return request({ url: '/api/c/patients/me', method: 'PUT', data: data });
}
/** 获取家庭成员列表 */
function getFamilyMembers() {
    return request({ url: '/api/c/patients/family-members' });
}
/** 新增家庭成员 */
function addFamilyMember(data) {
    return request({ url: '/api/c/patients/family-members', method: 'POST', data: data });
}
/** 更新家庭成员 */
function updateFamilyMember(id, data) {
    return request({ url: "/api/c/patients/family-members/".concat(id), method: 'PUT', data: data });
}
/** 删除家庭成员 */
function deleteFamilyMember(id) {
    return request({ url: "/api/c/patients/family-members/".concat(id), method: 'DELETE' });
}
/** 挂号列表（status: REGISTERED/VISITED/CANCELLED，familyMemberId: 0=本人/空=全部） */
function getRegistrations(params) {
    return request({ url: '/api/c/registrations', data: params });
}
/** 挂号详情 */
function getRegistrationDetail(id) {
    return request({ url: "/api/c/registrations/".concat(id) });
}
/** 取消挂号（就诊前 2h 以上） */
function cancelRegistration(id) {
    return request({ url: "/api/c/registrations/".concat(id, "/cancel"), method: 'PATCH' });
}
/** 问诊记录列表 */
function getConsultations(params) {
    return request({ url: '/api/c/records/consultations', data: params });
}
/** 问诊详情 */
function getConsultationDetail(id) {
    return request({ url: "/api/c/records/consultations/".concat(id) });
}
/** 问诊对话记录 */
function getConsultationMessages(id) {
    return request({ url: "/api/c/records/consultations/".concat(id, "/messages") });
}
/** 问诊处方列表 */
function getConsultationPrescriptions(id) {
    return request({ url: "/api/c/records/consultations/".concat(id, "/prescriptions") });
}
/** 处方列表 */
function getPrescriptions(params) {
    return request({ url: '/api/c/records/prescriptions', data: params });
}
/** 处方详情 */
function getPrescriptionDetail(id) {
    return request({ url: "/api/c/records/prescriptions/".concat(id) });
}
/** 购药订单列表 */
function getDrugOrders(params) {
    return request({ url: '/api/c/records/orders', data: params });
}
/** 购药订单详情 */
function getDrugOrderDetail(id) {
    return request({ url: "/api/c/records/orders/".concat(id) });
}
/** 用药提醒列表 */
function getReminders(familyMemberId) {
    return request({
        url: '/api/c/records/reminders',
        data: familyMemberId ? { familyMemberId: familyMemberId } : {},
    });
}
/** 健康档案汇总 */
function getHealthProfile(familyMemberId) {
    return request({
        url: '/api/c/records/health-profile',
        data: familyMemberId ? { familyMemberId: familyMemberId } : {},
    });
}
