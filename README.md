# ArknightsServer
这是一个明日方舟本地服务端,对应的客户端是CN 2.7.81 Windows|CN 2.7.81 Android|Global 37.7.41 Android,该服务端理论上支持多人游戏.本仓库不提供将数据代理到本地的方法,请自行解决 

## 支持客户端
|   设备  | 是否支持  |
|:-------:|:---:|
|  CN Windows | Support 支持|
|    CN Android   | Support 支持|
|    CN IOS   | Not Planned 未计划|
|    Global Android   | Support 支持|
|    Global Windows   | Not Support 暂不支持|
|    Global IOS   | Not Planned 未计划|

## 如何在外部修改密码和注册？
双击项目根目录的 `login.html` 在网页里完成

# 更新日志
请阅读[更新日志](./Update.md)

# 2.4.61老版本用户注意！
若您是从2.4.61版本的服务端迁移至2.7.81请注意，因新版用户数据文件与旧版有较大的差别，所以所有数据库中的用户数据需要清空！

## 如何部署本服务端?
请阅读[操作指南](./HowToSetUp.md)

## 配置文件
### 可更改
```
"server"： {
"http": //服务启动端口
"enableServer": //是否允许玩家进入
"GMKey": //管理员密钥
},
"timer": //设置虚假时间
"pay": {
"cashDoubleCount": //源石充值是否翻倍
"time": //客户端询问次数(询问多少次后直接提示支付成功)
},
"database":{
"host": //MySQL主机地址
"port": //MySQL端口
"dbname": //数据库名
"user"://用户名
"password"://密码
},
"yostar": {
"mailer": {
"enable": //是否启用国际版邮件发送
"account": "example@qq.com", //你的邮箱
"password": "", //密码/授权码
"smtp": "smtp.qq.com", //smtp服务器
"smtp_port": 465 //smtp服务器端口
}
}
"battle":{
"dropRate":1, //掉落倍数
"debug": //是否启动调试模式(战斗无论是否成功都是三星)
},
"basement": {
"produceSpeed": 1000 //基建生产速率
},
"network": //设置network配置，如果更改端口这里需要更改URL
```

## 怎么启动/解决中文乱码问题
请使用java17以上(JDK25以下)的java版本在CMD或Powershell中运行,MySQL(8.0以上)请自行准备,不要忘记改config.json中的设置

1、首先运行 `chcp 65001`  
2、在项目根目录运行`hypergryph-0.5.0.0 Alpha.jar`
```bash
chcp 65001
java -jar "hypergryph-0.5.0.0 Alpha.jar"
```

## 已知问题

1. 肉鸽玩法  
2. stage.json不完善  
3. 可能有更多问题

## 如何注册?
## 新账户首次登陆点击 开始唤醒 时可能会在加载进度100%之后返回主界面，此时再次点击 开始唤醒 即可开始游戏
### 注册成功后 不会!!! 自动登录

在登录界面选择 `账号管理` ,再选择 `登录其他账号` 通过手机号+验证码登录(手机号为为11位随便填,验证码也是6位随便填),成功注册后进入手机号+密码登录界面,默认密码为ArkNightsLoongling001

## 进度
|   目标功能  | 进度  | 完成情况 | 备注 |
|:-------:|:---:|:----:|:---:|
|  获取网络配置 | 已完成 | 已完成  |  |
|    登录   | 已完成 | 已完成  |  |
|    注册   | 已完成 | 已完成  |  |
| SyncData | 已完成 | 已完成  |  |
|  多人身份同步 | 已完成 | 已完成  |  |
|    剧情   | 已完成 | 已完成  |  |
|    战斗   | 已完成 | 已完成 | 可能有问题 |
|   定向寻访  | 已完成 | 已完成  |  |
|  公开寻访   | 修复中 | 未完成  |  |
