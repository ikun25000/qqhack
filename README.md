# **TXHook - QQ协议抓包分析工具**
 
> **📌 项目状态**  
> 由原作者 [fuqiuluo](https://github.com/fuqiuluo)  开发并开源，现由 [owo233](https://github.com/callng)  维护优化，修复BUG并增强功能。
 
---

**我们始终推荐在Actions下载最新构建版本，Releases可能更新不及时。[点我前往Actions](https://github.com/callng/QQHook/actions/workflows/build_ci.yml)**

## 😄 状态

**⚠️~~注意，从9.1.90(versionCode>=10248)及以上的版本开始将无法获取到完整的数据包内容~~**

~~目前会出现所谓的"Appid is invalid!"，导致无法正常登录和收发消息。~~

- 已经修复，现在可以继续获取数据包了（完整包需要配合TXHook Server）
- 至少需要升级模块版本到3.2.2，才能在9.1.90版本及以上正常使用。

## ⚠️ 警告

**在不需要抓包的时候，不要启用本模块，这可能会导致宿主性能下降。**

**在不运行模块本体的情况获取不到任何数据！！！**

也就是说，必须启动模块APP后才能正常使用。

在模块设置页面配置服务地址后才能和 TXHook Server 通讯。

---

## **🔧 核心功能**  
基于Xposed框架的QQ协议抓包模块，支持：  
- 获取TCP数据包
- 获取TEA加解密数据
- 获取ECDH密钥数据
- 获取部分MD5加密数据
- 获取完整的数据包(需配合PC版本的TXHook Server)
- 分析协议结构(需配合PC版本的TXHook Server)
- 其他功能(...)
- **理论兼容全平台QQ版本**（包括但不限于）：  
  - 标准版QQ  
  - TIM
  - QQ手表版

---
 
## **🚀 使用说明**  
1. **环境依赖**  
   - LSPosed
   - 建议Android 8.1+系统

> **⚠️ 注意**  
> 本工具仅供学习研究，使用需遵守当地法律法规。
> 部分功能可能需要手动适配新版本QQ。
 
--- 
