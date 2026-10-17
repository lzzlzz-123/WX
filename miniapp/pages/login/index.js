const api = require('../../common/wx-api')

// 创意提示：每次进登录页随机挑一条，给用户一个"现在登录"的理由。
const CREATIVE_TIPS = [
  { title: '水泥会凝固，好价格也会', sub: '登录锁定今日集采价，明天可能就是另一个行情了' },
  { title: '别人还在问价，你已经下单', sub: '一键登录，报价、下单、配送，快人一步' },
  { title: '一车钢筋的距离', sub: '只差这一次登录。手机号一键完成，不用记密码' },
  { title: '工地不等人，好价不隔夜', sub: '登录后询价单直达采购顾问，当天给价' },
  { title: '砖头不会自己跑到工地', sub: '但登录后，它们真的会。配送全程可追踪' }
]

const AGREEMENT_TEXTS = {
  user: '筑云清松是建筑材料一站式采购平台。注册即表示你同意：\n\n1. 你提供的信息真实有效；\n2. 平台报价以采购顾问最终确认为准；\n3. 订单、配送、售后按平台规则执行；\n4. 禁止利用平台从事违法违规活动。\n\n（此处为演示文本，上线前请替换为正式协议）',
  privacy: '我们仅收集以下信息用于提供服务：\n\n1. 手机号——用于登录识别与订单联系；\n2. 微信头像、昵称——用于账户展示（可选）；\n3. 收货地址——用于配送。\n\n我们不会向任何第三方出售你的个人信息。\n\n（此处为演示文本，上线前请替换为正式隐私政策）'
}

Page({
  data: {
    tip: CREATIVE_TIPS[0],
    agreed: false,
    submitting: false,
    shake: false,
    // 短信验证码登录（测试短信服务）
    loginMode: 'oneClick', // oneClick | sms
    smsPhone: '',
    smsCode: '',
    smsSending: false,
    countdown: 0
  },
  onLoad() {
    const tip = CREATIVE_TIPS[Math.floor(Math.random() * CREATIVE_TIPS.length)]
    this.setData({ tip })
  },
  toggleAgreement() {
    this.setData({ agreed: !this.data.agreed })
  },
  showAgreement(event) {
    const type = event.currentTarget.dataset.type
    wx.showModal({
      title: type === 'privacy' ? '隐私政策' : '用户协议',
      content: AGREEMENT_TEXTS[type] || AGREEMENT_TEXTS.user,
      showCancel: false,
      confirmText: '我知道了'
    })
  },
  remindAgree() {
    // 未勾选时不弹微信授权面板，先提示并抖动勾选框吸引注意。
    wx.showToast({ title: '请先阅读并勾选协议', icon: 'none' })
    this.setData({ shake: true })
    setTimeout(() => this.setData({ shake: false }), 600)
  },
  switchMode(event) {
    this.setData({ loginMode: event.currentTarget.dataset.mode })
  },
  onSmsPhoneInput(event) {
    this.setData({ smsPhone: event.detail.value })
  },
  onSmsCodeInput(event) {
    this.setData({ smsCode: event.detail.value })
  },
  validPhone(phone) {
    return /^1[3-9]\d{9}$/.test(phone)
  },
  startCountdown(seconds) {
    this.setData({ countdown: seconds })
    const timer = setInterval(() => {
      const next = this.data.countdown - 1
      this.setData({ countdown: next })
      if (next <= 0) clearInterval(timer)
    }, 1000)
  },
  async onSendSmsCode() {
    if (this.data.smsSending || this.data.countdown > 0) return
    if (!this.validPhone(this.data.smsPhone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    this.setData({ smsSending: true })
    try {
      const result = await api.sendSmsCode(this.data.smsPhone)
      this.startCountdown(Number(result?.resendSeconds || 60))
      // 测试短信服务：验证码直接回显，自动填入并提示。
      if (result?.testCode) {
        this.setData({ smsCode: result.testCode })
        wx.showToast({ title: `测试验证码 ${result.testCode}`, icon: 'none', duration: 2500 })
      } else {
        wx.showToast({ title: '验证码已发送', icon: 'success' })
      }
    } catch (error) {
      wx.showToast({ title: error?.message || '发送失败，请重试', icon: 'none' })
    } finally {
      this.setData({ smsSending: false })
    }
  },
  async onSmsLogin() {
    if (this.data.submitting) return
    if (!this.data.agreed) {
      this.remindAgree()
      return
    }
    if (!this.validPhone(this.data.smsPhone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return
    }
    if (!/^\d{6}$/.test(this.data.smsCode)) {
      wx.showToast({ title: '请输入 6 位验证码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await api.loginWithSms(this.data.smsPhone, this.data.smsCode)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack({
          fail: () => wx.switchTab({ url: '/pages/mine/index' })
        })
      }, 600)
    } catch (error) {
      wx.showToast({ title: error?.message || '登录失败，请重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  async onGetPhoneNumber(event) {
    if (this.data.submitting) return
    const code = event?.detail?.code
    if (!code) {
      wx.showToast({ title: '需要授权手机号才能登录', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await api.loginWithPhone(code)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack({
          fail: () => wx.switchTab({ url: '/pages/mine/index' })
        })
      }, 600)
    } catch (error) {
      wx.showToast({ title: error?.message || '登录失败，请重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  browse() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
