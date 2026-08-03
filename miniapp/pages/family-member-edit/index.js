"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var request_1 = require("../../utils/request");
Page({
    data: {
        memberId: 0,
        isEdit: false,
        name: '',
        relationship: '',
        phone: '',
        gender: '',
        birthDate: '',
        allergyHistory: '',
        genderOptions: ['男', '女'],
        genderIndex: -1,
        relationshipOptions: ['配偶', '父亲', '母亲', '儿子', '女儿', '兄弟', '姐妹', '其他'],
        relationshipIndex: -1,
        saving: false,
    },
    onLoad: function (query) {
        if (query.id) {
            var memberId = parseInt(query.id, 10);
            this.setData({ memberId: memberId, isEdit: true });
            this.loadMember(memberId);
        }
    },
    loadMember: function (memberId) {
        var _this = this;
        (0, request_1.getFamilyMembers)().then(function (res) {
            if (res.code === 200 && res.data) {
                var member = res.data.find(function (m) { return m.id === memberId; });
                if (member) {
                    var genderIndex = member.gender === '男' ? 0 : member.gender === '女' ? 1 : -1;
                    var relationshipIndex = _this.data.relationshipOptions.indexOf(member.relationship);
                    _this.setData({
                        name: member.name,
                        relationship: member.relationship,
                        phone: member.phone || '',
                        gender: member.gender || '',
                        birthDate: member.birthDate || '',
                        allergyHistory: member.allergyHistory || '',
                        genderIndex: genderIndex,
                        relationshipIndex: relationshipIndex,
                    });
                }
            }
        });
    },
    onNameInput: function (e) {
        this.setData({ name: e.detail.value });
    },
    onRelationshipChange: function (e) {
        var relationship = this.data.relationshipOptions[e.detail.value];
        this.setData({ relationship: relationship, relationshipIndex: e.detail.value });
    },
    onGenderChange: function (e) {
        var gender = this.data.genderOptions[e.detail.value];
        this.setData({ gender: gender, genderIndex: e.detail.value });
    },
    onBirthDateChange: function (e) {
        this.setData({ birthDate: e.detail.value });
    },
    onPhoneInput: function (e) {
        this.setData({ phone: e.detail.value });
    },
    onAllergyInput: function (e) {
        this.setData({ allergyHistory: e.detail.value });
    },
    onSave: function () {
        var _this = this;
        if (!this.data.name) {
            my.showToast({ content: '请输入姓名', type: 'none' });
            return;
        }
        if (!this.data.relationship) {
            my.showToast({ content: '请选择关系', type: 'none' });
            return;
        }
        this.setData({ saving: true });
        var data = {
            name: this.data.name,
            relationship: this.data.relationship,
            phone: this.data.phone || undefined,
            gender: this.data.gender || undefined,
            birthDate: this.data.birthDate || undefined,
            allergyHistory: this.data.allergyHistory || undefined,
        };
        var promise = this.data.isEdit
            ? (0, request_1.updateFamilyMember)(this.data.memberId, data)
            : (0, request_1.addFamilyMember)(data);
        promise.then(function (res) {
            if (res.code === 200) {
                my.showToast({ content: _this.data.isEdit ? '修改成功' : '添加成功' });
                my.navigateBack();
            }
            else {
                my.showToast({ content: res.message || '操作失败', type: 'none' });
            }
        }).finally(function () {
            _this.setData({ saving: false });
        });
    },
});
