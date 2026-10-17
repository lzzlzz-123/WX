const api = require('../../common/wx-api')

Page({
  data: {
    avatarPreview: '',
    nickname: '',
    saving: false
  },
  onLoad() {
    if (!api.currentUser()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    const cached = wx.getStorageSync('userProfile') || {}
    const nickname = cached.nickname && cached.nickname !== '微信用户' ? cached.nickname : ''
    this.tempAvatar = ''
    this.setData({ avatarPreview: api.fileUrl(cached.avatarUrl), nickname })
    // Pull the latest server-side profile so edits from another device show up.
    api.refreshProfile()
      .then(profile => {
        if (!this.tempAvatar) this.setData({ avatarPreview: api.fileUrl(profile.avatarUrl) })
        if (!this.data.nickname && profile.nickname && profile.nickname !== '微信用户') {
          this.setData({ nickname: profile.nickname })
        }
      })
      .catch(() => {})
  },
  onChooseAvatar(event) {
    const avatarUrl = event?.detail?.avatarUrl
    if (!avatarUrl) return
    this.tempAvatar = avatarUrl
    this.setData({ avatarPreview: avatarUrl })
  },
  onNicknameInput(event) {
    this.setData({ nickname: event.detail.value || '' })
  },
  async onSave() {
    if (this.data.saving) return
    const nickname = this.data.nickname.trim()
    if (!nickname) {
      wx.showToast({ title: '请填写昵称', icon: 'none' })
      return
    }
    if (!this.data.avatarPreview) {
      wx.showToast({ title: '请选择头像', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      await api.ensureUser()
      if (this.tempAvatar) await api.uploadAvatar(this.tempAvatar)
      await api.updateProfile({ nickname })
      this.tempAvatar = ''
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/mine/index' }) }), 600)
    } catch (error) {
      wx.showToast({ title: error?.message || '保存失败，请重试', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
