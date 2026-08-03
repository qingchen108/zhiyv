import { getFamilyMembers, addFamilyMember, updateFamilyMember } from '../../utils/request';

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

  onLoad(query: { id?: string }) {
    if (query.id) {
      const memberId = parseInt(query.id, 10);
      this.setData({ memberId, isEdit: true });
      this.loadMember(memberId);
    }
  },

  loadMember(memberId: number) {
    getFamilyMembers().then((res) => {
      if (res.code === 200 && res.data) {
        const member = res.data.find((m) => m.id === memberId);
        if (member) {
          const genderIndex = member.gender === '男' ? 0 : member.gender === '女' ? 1 : -1;
          const relationshipIndex = this.data.relationshipOptions.indexOf(member.relationship);
          this.setData({
            name: member.name,
            relationship: member.relationship,
            phone: member.phone || '',
            gender: member.gender || '',
            birthDate: member.birthDate || '',
            allergyHistory: member.allergyHistory || '',
            genderIndex,
            relationshipIndex,
          });
        }
      }
    });
  },

  onNameInput(e: { detail: { value: string } }) {
    this.setData({ name: e.detail.value });
  },

  onRelationshipChange(e: { detail: { value: number } }) {
    const relationship = this.data.relationshipOptions[e.detail.value];
    this.setData({ relationship, relationshipIndex: e.detail.value });
  },

  onGenderChange(e: { detail: { value: number } }) {
    const gender = this.data.genderOptions[e.detail.value];
    this.setData({ gender, genderIndex: e.detail.value });
  },

  onBirthDateChange(e: { detail: { value: string } }) {
    this.setData({ birthDate: e.detail.value });
  },

  onPhoneInput(e: { detail: { value: string } }) {
    this.setData({ phone: e.detail.value });
  },

  onAllergyInput(e: { detail: { value: string } }) {
    this.setData({ allergyHistory: e.detail.value });
  },

  onSave() {
    if (!this.data.name) {
      my.showToast({ content: '请输入姓名', type: 'none' });
      return;
    }
    if (!this.data.relationship) {
      my.showToast({ content: '请选择关系', type: 'none' });
      return;
    }

    this.setData({ saving: true });
    const data = {
      name: this.data.name,
      relationship: this.data.relationship,
      phone: this.data.phone || undefined,
      gender: this.data.gender || undefined,
      birthDate: this.data.birthDate || undefined,
      allergyHistory: this.data.allergyHistory || undefined,
    };

    const promise = this.data.isEdit
      ? updateFamilyMember(this.data.memberId, data)
      : addFamilyMember(data);

    promise.then((res) => {
      if (res.code === 200) {
        my.showToast({ content: this.data.isEdit ? '修改成功' : '添加成功' });
        my.navigateBack();
      } else {
        my.showToast({ content: res.message || '操作失败', type: 'none' });
      }
    }).finally(() => {
      this.setData({ saving: false });
    });
  },
});