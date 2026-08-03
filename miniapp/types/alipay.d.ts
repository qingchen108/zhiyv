/** 支付宝小程序全局类型声明 */

declare function App(options: Record<string, unknown>): void;
declare function Page(options: Record<string, unknown>): void;
declare function getApp(): any;
declare function Component(options: Record<string, unknown>): void;
declare var console: any;

declare namespace my {
  /** 发起网络请求 */
  function request(options: {
    url: string;
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    data?: Record<string, unknown>;
    headers?: Record<string, string>;
    success?: (res: { data: any; status: number; headers: Record<string, string> }) => void;
    fail?: (err: any) => void;
    complete?: () => void;
  }): void;

  /** 显示提示 */
  function showToast(options: {
    content: string;
    type?: 'success' | 'fail' | 'exception' | 'none';
    duration?: number;
  }): void;

  /** 显示确认对话框 */
  function confirm(options: {
    title?: string;
    content: string;
    confirmButtonText?: string;
    cancelButtonText?: string;
    success?: (res: { confirm: boolean }) => void;
    fail?: () => void;
  }): void;

  /** 同步存储 */
  function setStorageSync(options: { key: string; data: any }): void;

  /** 同步读取 */
  function getStorageSync(options: { key: string }): { data: any };

  /** 删除存储 */
  function removeStorageSync(options: { key: string }): void;

  /** 页面跳转 */
  function navigateTo(options: { url: string }): void;

  /** 切换 Tab */
  function switchTab(options: { url: string }): void;

  /** 返回上一页 */
  function navigateBack(options?: { delta?: number }): void;

  /** 重定向 */
  function redirectTo(options: { url: string }): void;

  /** 选择器 */
  function chooseImage(options: {
    count?: number;
    sourceType?: string[];
    success?: (res: { apFilePaths: string[] }) => void;
  }): void;

  /** 选择日期 */
  function datePicker(options: {
    format?: string;
    currentDate?: string;
    startDate?: string;
    endDate?: string;
    success?: (res: { date: string }) => void;
  }): void;

  /** 打开定位 */
  function chooseLocation(options: {
    success?: (res: { latitude: number; longitude: number; address: string; name: string }) => void;
  }): void;

  /** 拨打电话 */
  function makePhoneCall(options: { number: string }): void;

  /** 获取网络类型 */
  function getNetworkType(options: {
    success?: (res: { networkType: string }) => void;
  }): void;

  /** 显示加载 */
  function showLoading(options?: { content?: string }): void;

  /** 隐藏加载 */
  function hideLoading(): void;

  /** 停止下拉刷新 */
  function stopPullDownRefresh(): void;
}