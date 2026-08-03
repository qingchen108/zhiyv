import { getFamilyMembers, deleteFamilyMember } from '../../utils/request';

Page({
  data: {
    members: [] as Array<{
      id: number;
      name: string;
      relationship: string;
      phone: string;
      gender: string;
      birthDate: string;
      age: number;
      allergyHistory: string;
    }>,
    loading: false,
    currentMemberId: 0,
    currentMemberName: '',
    patientName: '',
  },

  onShow() {
    const app = getApp();
    const memberId = app.globalData.currentMemberId || 0;
    this.setData({
      currentMemberId: memberId,
      patientName: app.globalData.patientName || '',
    });
    this.loadMembers();
  },

  loadMembers() {
    this.setData({ loading: true });
    getFamilyMembers().then((res) => {
      if (res.code === 200 && res.data) {
        const enriched = res.data.map(m => ({
          ...m,
          firstChar: m.name ? m.name.charAt(0) : '',
          canDelete: m.name !== this.data.patientName,
        }));
        this.setData({ members: enriched });
        this.updateCurrentMemberName();
      }
    }).finally(() => {
      this.setData({ loading: false });
    });
  },

  updateCurrentMemberName() {
    const member = this.data.members.find(m => m.id === this.data.currentMemberId);
    this.setData({ currentMemberName: member ? member.name : '未知' });
  },

  // 判断是否为患者本人（通过姓名匹配）
  isSelf(name: string): boolean {
    return this.data.patientName === name;
  },

  navigateToAdd() {
    my.navigateTo({ url: '/pages/family-member-edit/index' });
  },

  navigateToEdit(e: { currentTarget: { dataset: { id: number } } }) {
    const id = e.currentTarget.dataset.id;
    my.navigateTo({ url: `/pages/family-member-edit/index?id=${id}` });
  },

  // 设为当前就诊人
  onSetActive(e: { currentTarget: { dataset: { id: number; name: string } } }) {
    const { id, name } = e.currentTarget.dataset;
    const app = getApp();
    app.globalData.currentMemberId = id;
    this.setData({ currentMemberId: id, currentMemberName: name });
    my.showToast({ content: `已切换至「${name}」` });
  },

  // 切换回本人
  onSwitchToSelf() {
    const app = getApp();
    app.globalData.currentMemberId = 0;
    this.setData({ currentMemberId: 0, currentMemberName: '' });
    my.showToast({ content: '已切换回本人' });
  },

  onDelete(e: { currentTarget: { dataset: { id: number; name: string } } }) {
    const { id, name } = e.currentTarget.dataset;
    if (this.isSelf(name)) {
      my.showToast({ content: '本人不可删除', type: 'none' });
      return;
    }
    my.confirm({
      title: '确认删除',
      content: `确定删除家庭成员「${name}」吗？`,
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      success: (res) => {
        if (res.confirm) {
          deleteFamilyMember(id).then((res2) => {
            if (res2.code === 200) {
              my.showToast({ content: '删除成功' });
              this.loadMembers();
            } else {
              my.showToast({ content: res2.message || '删除失败', type: 'none' });
            }
          });
        }
      },
    });
  },
});