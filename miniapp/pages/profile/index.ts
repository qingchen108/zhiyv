import { getPatientProfile, updatePatientProfile, getFamilyMembers } from '../../utils/request';

Page({
  data: {
    patientName: '',
    name: '',
    phone: '',
    gender: '',
    birthDate: '',
    age: 0,
    allergyHistory: '',
    genderOptions: ['男', '女'],
    genderIndex: -1,
    modified: false,
    familyMembers: [] as Array<{
      id: number;
      name: string;
      relationship: string;
    }>,
    // 原始数据用于比较
    _original: {} as Record<string, unknown>,
  },

  onLoad() {
    this.loadProfile();
    this.loadFamilyMembers();
  },

  onShow() {
    this.loadFamilyMembers();
  },

  loadProfile() {
    getPatientProfile().then((res) => {
      if (res.code === 200 && res.data) {
        const data = res.data;
        this.setData({
          patientName: data.name,
          name: data.name,
          phone: data.phone,
          gender: data.gender || '',
          birthDate: data.birthDate || '',
          age: data.age || 0,
          allergyHistory: data.allergyHistory || '',
          genderIndex: data.gender === '男' ? 0 : data.gender === '女' ? 1 : -1,
          _original: {
            name: data.name,
            gender: data.gender,
            birthDate: data.birthDate,
            allergyHistory: data.allergyHistory,
          },
        });
      }
    });
  },

  loadFamilyMembers() {
    getFamilyMembers().then((res) => {
      if (res.code === 200 && res.data) {
        this.setData({ familyMembers: res.data });
      }
    });
  },

  onNameInput(e: { detail: { value: string } }) {
    this.setData({ name: e.detail.value, modified: true });
  },

  onGenderChange(e: { detail: { value: number } }) {
    const gender = this.data.genderOptions[e.detail.value];
    this.setData({ gender, genderIndex: e.detail.value, modified: true });
  },

  onBirthDateChange(e: { detail: { value: string } }) {
    this.setData({ birthDate: e.detail.value, modified: true });
  },

  onAllergyInput(e: { detail: { value: string } }) {
    this.setData({ allergyHistory: e.detail.value, modified: true });
  },

  onSave() {
    const data: Record<string, string> = {};
    if (this.data.name !== this.data._original.name) {
      data.name = this.data.name;
    }
    if (this.data.gender !== this.data._original.gender) {
      data.gender = this.data.gender;
    }
    if (this.data.birthDate !== this.data._original.birthDate) {
      data.birthDate = this.data.birthDate;
    }
    if (this.data.allergyHistory !== this.data._original.allergyHistory) {
      data.allergyHistory = this.data.allergyHistory;
    }

    if (Object.keys(data).length === 0) {
      my.showToast({ content: '没有需要修改的内容', type: 'none' });
      return;
    }

    updatePatientProfile(data).then((res) => {
      if (res.code === 200) {
        my.showToast({ content: '保存成功' });
        this.setData({ modified: false });
        this.loadProfile();
      } else {
        my.showToast({ content: res.message || '保存失败', type: 'none' });
      }
    });
  },

  navigateToFamilyMembers() {
    my.navigateTo({ url: '/pages/family-members/index' });
  },
});