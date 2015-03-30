Android-GridView-PhotoGallery
===

#Instroduction
一个GridView照片列表的案例，异步加载网络图片的同时，使用LRU的缓存策略，通过内存与磁盘的二级缓存，实现GridView在滑动与加载图片时给予良好的用户体验.
#Features
通过AsyncTask加载网络图片
Lru的缓存算法将图片资源缓存到内存与磁盘
UI展示图片缩略图，防止图片过大导致的OutOfMemory
为GridView的Item回收复用造成异步加载的并发性问题提供一个很好的解决方案
#Screenshots
![image](https://github.com/Alex987965/Android-GridView-PhotoGallery/blob/master/screenshots/Screenshot_2015-03-17-22-20-10.png)
![image](https://github.com/Alex987965/Android-GridView-PhotoGallery/blob/master/screenshots/Screenshot_2015-03-17-22-20-27.png)
