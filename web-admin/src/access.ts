// access 插件入口（Q14：路由级权限）
// access 插件生成的运行时会 import 本文件的【默认导出】（@/access），
// 故此处用 export default，而非写在 app.ts 的命名导出。
// 入参 initialState 来自 useModel('@@initialState')，即 app.ts 的 getInitialState 返回值。

export default function access(initialState: { currentUser?: API.CurrentUser }) {
  const currentUser = initialState?.currentUser;
  return {
    loggedIn: !!currentUser,
    isDoctor: currentUser?.role === 'DOCTOR',
    isAdmin: currentUser?.role === 'ADMIN',
  };
}