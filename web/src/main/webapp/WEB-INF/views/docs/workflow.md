# 概述

由若干个任务组成，用来定义任务之间的依赖关系，将多个任务以串行或并行地方式连接成一个整体。除此之外，任务自身的两次执行之间也会存在依赖关系（自依赖）。工作流中不允许重复的节点和循环依赖。
工作流可以分为私有工作流和公共工作流。
# 基本概念

| 概念    | 说明            | 
|:------------: |:----------------:|
| 名称 | 工作流名称唯一且不可更改|
| 调度表达式 | 使用quartz表达式，兼容了linux下的crontab表达式。平台支持分钟级别的调度。|
| 超时时间 | 任务/工作流执行超过这个时间，用户会收到报警，但并会影响任务/工作流继续执行|
| 重试次数 | 工作流中的任务会根据任务自身的重试次数执行重试|
| 允许跳过 | 配置为允许时，同一任务/工作流的两次调度，时间上如果出现重叠，后一次调度会被跳过。|
| 允许并行 | 配置为允许时，同一任务/工作流的两次调度，时间上如果出现重叠，后一次会立刻运行,与前一次调度并行。|
| 触发条件 | 完成即触发，后一次调度在前一次完成的情况下触发<br/>成功才触发，后一次调度在前一次成功的情况下触发|
| 任务延迟时间 | 任务相对于工作流的调度时间延迟执行的分钟数|

#分类
##私有工作流
私有工作流是将一组完成特定功能的任务串联在一起的工作流，有以下一些特点
* 只允许有一个终止节点
* 权限仅限于同组的用户
* 其中只要有一个任务运行失败，整个工作流即失败

##公共工作流
公共工作流是由admin创建的工作流，被所有用户共享。
* 允许有多个终止节点
* 每个团队有一个共同的其实节点，即`teamname_root_task`,团队内的所有任务都配置在这个根节点的下面，并且可以依赖其他团队配置在同一工作流中的任务节点
* 其中有一个任务运行失败时，依赖他的任务节点都失败，其他节点正常执行
* 每个团队看到的工作流状态并不相同，他们看到的是自己团队的节点运行的状况
