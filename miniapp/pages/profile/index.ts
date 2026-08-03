import {
  getPatientProfile,
  updatePatientProfile,
  getFamilyMembers,
  getHealthProfile,
  HealthProfile,
} from '../../utils/request';

interface MemberItem {
  id: number; // 0 = 本人
  name: string;
  relationship?: string;
  gender?: string;
  birthDate?: string;
  age?: number;
  allergyHistory?: string;
}

Page({
  data: {
    members: [{ id: 0, name: '本人' }] as MemberItem[],
    activeIndex: 0,
    activeMemberId: 0,
    // 当前成员基本信息（本人可编辑）
    name: '',
    phone: '',
    gender: '',
    birthDate: '',
    age: 0,
    allergyHistory: '',
    genderOptions: ['男', '女'],
    genderIndex: -1,
    modified: false,
    _original: {} as Record<string, unknown>,
    // 档案汇总
    regCount: 0,
    consultCount: 0,
    prescCount: 0,
    orderCount: 0,
    remindCount: 0,
    loading: false,
  },

  onLoad() {
    // 默认本人视角，同步全局成员选择（避免残留上次会话的选择）
    getApp().globalData.currentMemberId = 0;
    this.loadFamilyMembers();
  },

  onShow() {
    // 家庭成员可能变化，回来时刷新
    if (this.data.members.length > 1 || this.data.members[0].name !== '本人') {
      this.loadFamilyMembers();
    }
  },

  loadFamilyMembers() {
    getFamilyMembers().then((res) => {
      if (res.code === 200 && res.data) {
        const members: MemberItem[] = [{ id: 0, name: '本人' }].concat(
          res.data.map((m) => ({
            id: m.id,
            name: m.name,
            relationship: m.relationship,
            gender: m.gender,
            birthDate: m.birthDate,
            age: m.age,
            allergyHistory: m.allergyHistory,
          })),
        );
        this.setData({ members });
        // 若当前选中成员已不存在则回到本人
        if (this.data.activeMemberId > 0 && !members.some((m) => m.id === this.data.activeMemberId)) {
          this.switchMemberById(0);
        } else {
          this.loadActiveProfile();
        }
      } else {
        this.loadActiveProfile();
      }
    });
  },

  /** 切换成员 tab（onTap 事件） */
  switchMember(e: any) {
    const id = Number(e.currentTarget.dataset.id || 0);
    const index = this.data.members.findIndex((m) => m.id === id);
    if (index < 0 || index === this.data.activeIndex) {
      return;
    }
    this.switchMemberById(id);
  },

  switchMemberById(id: number) {
    const index = this.data.members.findIndex((m) => m.id === id);
    // 同步全局成员选择（挂号 tab 页从 globalData 读取，tabBar 页无法带参）
    const app = getApp();
    app.globalData.currentMemberId = id;
    this.setData({
      activeIndex: Math.max(index, 0),
      activeMemberId: id,
      name: '',
      gender: '',
      birthDate: '',
      age: 0,
      allergyHistory: '',
      genderIndex: -1,
      modified: false,
      _original: {},
      regCount: 0,
      consultCount: 0,
      prescCount: 0,
      orderCount: 0,
      remindCount: 0,
    });
    this.loadActiveProfile();
  },

  loadActiveProfile() {
    const memberId = this.data.activeMemberId;
    this.setData({ loading: true });
    getHealthProfile(memberId > 0 ? memberId : undefined).then((res) => {
      this.setData({ loading: false });
      if (res.code === 200 && res.data) {
        const p: HealthProfile = res.data;
        const member = this.data.members.find((m) => m.id === this.data.activeMemberId);
        this.setData({
          name: p.visitorName || (member ? member.name : ''),
          gender: p.visitorGender || '',
          birthDate: member && member.birthDate ? member.birthDate : '',
          age: p.visitorAge || 0,
          allergyHistory: p.allergyHistory || '',
          genderIndex: p.visitorGender === '男' ? 0 : p.visitorGender === '女' ? 1 : -1,
          regCount: (p.registrations || []).length,
          consultCount: (p.consultations || []).length,
          prescCount: (p.prescriptions || []).length,
          orderCount: (p.orders || []).length,
          remindCount: (p.reminders || []).length,
          _original: {
            name: p.visitorName,
            gender: p.visitorGender,
            birthDate: member && member.birthDate ? member.birthDate : '',
            allergyHistory: p.allergyHistory,
          },
        });
      } else if (res.code !== 200) {
        my.showToast({ content: res.message || '加载失败', type: 'none' });
      }
    });
    // 本人额外加载档案（含手机号，供编辑展示）
    if (memberId === 0) {
      getPatientProfile().then((res) => {
        if (res.code === 200 && res.data) {
          const d = res.data;
          this.setData({
            name: d.name,
            phone: d.phone,
            gender: d.gender || '',
            birthDate: d.birthDate || '',
            age: d.age || 0,
            allergyHistory: d.allergyHistory || '',
            genderIndex: d.gender === '男' ? 0 : d.gender === '女' ? 1 : -1,
            _original: {
              name: d.name,
              gender: d.gender,
              birthDate: d.birthDate,
              allergyHistory: d.allergyHistory,
            },
          });
        }
      });
    }
  },

  // ==================== 本人资料编辑 ====================

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
        this.loadActiveProfile();
      } else {
        my.showToast({ content: res.message || '保存失败', type: 'none' });
      }
    });
  },

  // ==================== 记录入口 ====================

  goRegistrations() {
    // 挂号记录是 tabBar 页，不能带参 navigateTo；成员选择已写入 globalData.currentMemberId
    my.switchTab({ url: '/pages/registrations/index' });
  },

  goConsultations() {
    my.navigateTo({ url: `/pages/consultations/index?familyMemberId=${this.data.activeMemberId}` });
  },

  goPrescriptions() {
    my.navigateTo({ url: `/pages/prescriptions/index?familyMemberId=${this.data.activeMemberId}` });
  },

  goOrders() {
    my.navigateTo({ url: `/pages/orders/index?familyMemberId=${this.data.activeMemberId}` });
  },

  goReminders() {
    my.navigateTo({ url: `/pages/reminders/index?familyMemberId=${this.data.activeMemberId}` });
  },

  navigateToFamilyMembers() {
    my.navigateTo({ url: '/pages/family-members/index' });
  },
});
