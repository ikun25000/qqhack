# **TXHook - QQ协议抓包分析工具**
 
> **📌 项目状态**  
> 由原作者 [fuqiuluo](https://github.com/fuqiuluo)  开发并开源，现由 [owo233](https://github.com/callng)  维护优化，修复BUG并增强功能。
 
---

## 😅 功能异常
**⚠️注意，暂不支持9.1.90(versionCode>=10248)及以上的版本。**

- 目前会出现所谓的"Appid is invalid!"，导致无法正常登录和收发消息。

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
   - xposed框架（或LSPosed等衍生框架）
   - 建议Android 7.0+系统

> **⚠️ 注意**  
> 本工具仅供学习研究，使用需遵守当地法律法规。
> 部分功能可能需要手动适配新版本QQ。
 
--- 
