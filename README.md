### 写在前面的碎碎念

- 前一阵子终于结完了一个外包，把最后留的视频播放的坑给填上了
- 本着能用原生组件就不用不第三方组件的愚蠢态度，最后也算是把功能实现了，长时间跑下来也没有问题，放心食用
- 也是看了不少官方文档和博客，加上是异地协助花了不少时间，不过最后还是把东西整出来了


### SurfaceView + MediaPlayer  实现列表循环播放视频

# 正文
### SurfaceView + MediaPlayer
简单介绍一下，了解的可以直接跳过
SurfaceView是一个View组件在XML里面引入，负责把视频播放区域画出来
MediaPlayer是真正的播放对象，存入视频来源、进度、声音大小、视频的宽度大小
> 这里的视频宽度和SurfaceView的宽度有区别 SurfaceView是画布大小 视频宽度是播放出来有效的大小 可以通过MediaPlayer进行自适应

使用SurfaceView+MediaPlayer播放一个视频的过程是
```java
// player 拿到viewHolder
payer.setDisplay(surfaceView.getHolder());
// 设置来源监听函数等一系列参数
player.setDataSource("视频来源 可为url或者本地地址");
// 设置完成后进入Initialized状态 需要prepare才可以播放
player.prepare();
// 开始播放
player.start();
```
再贴一张生命周期图

说一下实现播放列表的第一个思路
每一个播放地址弄一个MediaPlayer
建立一个Map或者List存入这些MediaPlayer
先开始播放第一个视频
另起线程初始化剩下的player 并使用OnCompletionListener监听完成进行播放的自动切换
以此来达到循环的目的

周期上面画的图可以看到一个player播放完成之后进入stoped 不可以调用start()
所以一开始尝试再次prepare()
但是还是播放不出来 就算再调用seekTo(0)
依旧无法播放

于是尝试暴力模式 每次播放完成后
new一个新的Player进行下次播放并替代List里面的对象
一开始认为这样不存在内存泄漏问题会被系统回收
遗漏了一个点 **MediaPlayer只有release之后才会被回收**
所以跑了一段时间之后就会因为内存泄漏崩掉
> 顺便插一句<br />MediaPlayer实际上实现部分是C++代码调用了大量系统资源<br />最多能有多少并没有给出来但是是有一个上限的<br />不再使用一定要release释放掉

所以最后的解决方案是
player播放完成后 调用reset()方法进入Idle状态
重新进行参数设置
下次调用依旧是一个新的player并且重复使用了player
