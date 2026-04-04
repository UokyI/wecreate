# WeCreate 系统

本系统允许用户通过自然语言输入需求，并通过调用 Qwen-Agent 自动实现这些需求。
[项目的来路归途](https://www.cnblogs.com/uoky/p/19821366)
## 工作原理

1. 用户通过 Web 界面输入自然语言需求Or文件
2. 系统使用需求调用 Qwen Code CLI Yolo模式（以后可能会支持更多CLI）
3.  Qwen Code CLI 自行决定要执行的操作并执行它们
4. 单需求：执行进度通过服务器发送事件（SSE）流式传输给用户
5. 多需求：返回追踪ID，CLI自行执行
6. 每个项目，有一个记忆文件夹，每一次完成任务，AI会整理对应的记忆进行自检（目前还比较简单，就是消耗了更多的Token）

## 设置说明

1. 运行前，需要自行安装Qwen Code CLI [Windows可参考博文](https://www.cnblogs.com/uoky/p/19217841)  其它的OS自行配置即可，环境没有要求
2. 系统会自动根据where qwen找到qwen code cli的安装路径并自行配置到系统中
3. 可以直接在设定页面中，自行配置qwen code cli路径

## 运行说明
1. JDK 1.8
2. Maven项目
3. DB:Mysql，需要运行sql文件夹下的建库sql 
4. OS没有要求
