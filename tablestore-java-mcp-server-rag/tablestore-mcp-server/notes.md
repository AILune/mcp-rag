## JVM 高频八股

频繁 GC 的优化思路

类加载过程

类加载器与双亲委派模型

1、JVM的内存区域划分。哪些区域是线程私有的，哪些是线程共享
2、Java对象的创建过程。在JVM层面，一个对象是如何从无到有被分配内存的
3、系统频繁发生Full GC，你会从哪些方面去排查？
4、什么是Java内存模型（JMM）它的主要目的是什么
5、Java的类加载机制。什么是双亲委派模型
6、判断一个对象是否可以被回收？介绍一下常用的算法
7、哪些对象可以作为GC Roots？
8、常见的垃圾回收算法
9、分代收集理论。年轻代和老年代分别采用什么回收算法？为什么
10、常见的垃圾收集器有哪些？请简要介绍一下CMS和G1收集器的特点及区别
11、什么是Minor GC、Major GC 和 Full GC？它们分别发生在什么时候
12、JVM 调优的参数都有哪些

13、JVM的组成部分



**频繁GC的优化思路**

**1、问题定位**

通过设置 -XX:+PrintGCDetails JVM 参数来查看详细 GC 日志，重点关注以下内容：

- Minor GC 和 Full GC 的频率和耗时
- GC 前后，新生代、老年代、元空间的使用情况
- GC 后有多少对象从年轻代晋升到了老年代

**2、问题分析与解决**

**如果是  Minor GC 频繁，通常的原因有：**

**新生代空间不足：**新建的对象快速填满了 Eden 区。此时可以通过**增大堆内存**、**调整新生代与老年代比例**、调整 Eden 区与 Survivor 区比例等方法解决

**对象创建频率过高：**代码中创建了大量的临时对象，比如 for 循环中每次循环都创建一个 String 对象，或者每次循环都使用 + 来拼接字符串对象。此时可以通过**使用 StringBuilder，通过在循环外初始化，循环内 append() 拼接**来解决频繁创建临时对象的问题。

**如果是 Full GC 频繁，通常的原因有：**

**内存泄漏：**比如 ThreadLocal 未 remove()、本地缓存没有设置 TTL 等。

**老年代空间不足：**可能是大对象太多，迅速填满了老年代。可以通过**设置大对象阈值、减少大对象的创建**来解决。

**元空间不足：**可能是加载的类信息太多，可以调整元空间的大小，同时避免频繁加载类。

**显式调用了 System.gc()：**通过添加 `-XX:+DisableExplicitGC` 来禁用显式调用，观察Full GC频率是否下降。

**CMS 并发收集失败：**可以通过选择更合适的垃圾收集器比如 G1



**类加载过程**

Java 中类加载过程主要分为三个大的阶段：加载、链接和初始化，链接又分为验证、准备和解析三个阶段

**加载阶段**主要完成三件事情：

- 由类加载器通过类的全限定名（比如 `com.example.MyClass`）找到对应的 `.class` 字节码文件，并获取二进制字节流。获取类全限定名的方式：

```
从本地文件系统加载
Class.forName("com.example.MyClass");
从JAR包加载、从网络加载、动态代理生成等
```

- 将字节流转化为方法区的运行时数据结构
- 在堆中生成一个代表该类的 `java.lang.Class` 对象，作为访问方法区中该对象数据的入口

**验证阶段**用于验证字节码文件是否符合规范，比如魔数、版本号等

**准备阶段**用于为静态变量分配内存空间，并赋默认值

**解析阶段**用于将常量池中的符号引用替换为直接引用，指向具体的内存地址

**初始化阶段**是类加载过程的最后一步，这一步真正执行编写的 Java 代码。

**核心工作：**执行类的构造器方法 `<clinit>()`，为**静态变量赋值**并**执行静态代码块**。

触发初始化的时机（主动引用）：JVM规范严格规定了有且只有六种情况必须立即对类进行初始化：

1. 遇到 `new`、`getstatic`、`putstatic` 或 `invokestatic` 这四条字节码指令时（典型场景：创建类的实例、读取或设置一个类的静态字段、调用一个类的静态方法）。
2. 使用 `java.lang.reflect` 包的方法对类进行反射调用时。
3. 当初始化一个类时，如果其父类还没有进行过初始化，则需要先触发其父类的初始化。
4. 当虚拟机启动时，用户需要指定一个要执行的主类（包含 `main()` 方法的那个类），虚拟机会先初始化这个主类。
5. 当使用JDK 7的动态语言支持时...（这个比较少用，可以简略）。
6. 当一个接口定义了`default`方法，如果这个接口的实现类发生了初始化，那该接口要在其之前被初始化。

**线程安全**：JVM会保证一个类的 `<clinit>()` 方法在多线程环境中被正确地**加锁和同步**。如果多个线程同时去初始化一个类，那么只有一个线程会去执行这个类的 `<clinit>()` 方法，其他线程都需要阻塞等待。



**类加载器与双亲委派模型**

- 类加载器（ClassLoader）：负责执行类加载过程的加载阶段。
  - **启动类加载器（Bootstrap ClassLoader）**：加载核心库，即`jre/lib`下的类，比如 `rt.jar`。
  - **扩展类加载器（Extension ClassLoader）**：加载扩展库，即`jre/lib/ext`下的类。
  - **应用程序类加载器（Application ClassLoader）**：也叫系统类加载器，加载用户类路径（Classpath）下的类，包括自定义的类以及外部 jar 包中的类。
- 双亲委派模型（Parents Delegation Model）好处：
  1. **避免类的重复加载**：保证一个类在JVM中只有唯一的`Class`对象。
  2. **防止核心 API 库被篡改**：如果自己写一个`java.lang.String`类，双亲委派模型会保证最终加载的还是`rt.jar`中的官方`String`类，防止了恶意代码的注入。



**1. JVM的内存区域划分。哪些区域是线程私有的，哪些是线程共享？**

JVM的内存区域主要划分为以下几块：

- **线程私有（随线程生灭）：**
  1. **程序计数器（Program Counter Register）：** 记录当前线程执行的字节码指令地址。为了线程切换后能恢复到正确的执行位置，每个线程都有独立的程序计数器。
  2. **Java虚拟机栈（Java Virtual Machine Stack）：** 描述Java方法执行的线程内存模型。每个方法执行时都会创建一个栈帧，用于存储局部变量表、操作数栈、动态链接、方法出口等信息。
  3. **本地方法栈（Native Method Stack）：** 为虚拟机使用到的Native方法服务。
- **线程共享（随虚拟机或GC堆生灭）：**
  1. **Java堆（Heap）：** 被所有线程共享的一块内存区域，在虚拟机启动时创建。**几乎所有的对象实例和数组都在这里分配内存**。它是垃圾收集器管理的主要区域。
  2. **方法区（Method Area）：** 用于存储已被虚拟机加载的**类型信息、常量、静态变量、即时编译器编译后的代码缓存**等数据。通常被称为“永久代”或“元空间”（JDK 8+）。

------

**2. Java对象的创建过程。在JVM层面，一个对象是如何从无到有被分配内存的？**

当虚拟机遇到一条 `new` 指令时，会执行以下步骤：

1. **类加载检查：** 检查该指令的参数能否在常量池中定位到一个类的符号引用，并检查这个类是否已被加载、解析和初始化过。如果没有，则先执行相应的类加载过程。
2. **分配内存：** 对象所需内存的大小在类加载完成后便可完全确定。从Java堆中划分一块确定大小的内存出来。分配方式有两种：
   - **指针碰撞（Bump the Pointer）：** 如果堆内存是绝对规整的，用过的内存放一边，空闲的内存放另一边，中间放一个指针作为分界点指示器。分配内存就是把指针向空闲空间移动与对象大小相等的距离。
   - **空闲列表（Free List）：** 如果堆内存不是规整的，虚拟机必须维护一个列表，记录哪些内存块是可用的，在分配时从列表中找到一块足够大的空间划分给对象，并更新列表。
   - *注：分配内存时还需考虑并发问题（CAS重试或TLAB）。*
3. **内存空间初始化：** 虚拟机将分配到的内存空间（不包括对象头）都初始化为零值。这保证了对象的实例字段在Java代码中可以不赋初始值就直接使用。
4. **设置对象头：** 对对象进行必要的设置，例如这个对象是哪个类的实例、如何找到类的元数据、对象的哈希码（实际上会延迟到调用 `hashCode()` 方法时才计算）、对象的GC分代年龄等信息，这些信息存放在对象的对象头（Object Header）中。
5. **执行 <init> 方法：** 执行 `new` 指令之后会接着执行 `<init>` 方法，按照程序员的意愿对对象进行初始化。至此，一个真正可用的对象才算被完全构造出来。

------

**3. 系统频繁发生Full GC，你会从哪些方面去排查？**

1. **查看监控与日志：**
   - 查看GC日志（`-XX:+PrintGCDetails`），确认Full GC的频率和耗时。
   - 查看系统错误日志，检查是否有 `OutOfMemoryError`。
2. **确认Full GC触发原因：**
   - **System.gc() 调用：** 检查是否显式调用了 `System.gc()`，可以通过 `-XX:+DisableExplicitGC` 屏蔽。
   - **老年代空间不足：**
     - **内存泄漏：** 
     - **大对象直接进入老年代：** 检查是否创建了过大的数组或对象，超过了 `-XX：PretenureSizeThreshold` 的阈值。
     - **分配速率过快：** 导致 Minor GC 后，存活对象太多无法放入Survivor，直接进入老年代。
   - **元空间/永久代不足：** 加载了过多的类（如热部署、反射、动态代理使用不当），超过了 `-XX：MaxMetaspaceSize`。
   - **晋升失败/并发模式失败（CMS）：** 年轻代晋升的对象大小超过了老年代的剩余空间（或者CMS的`-XX：CMSInitiatingOccupancyFraction` 设置过低/过高）。
3. **工具辅助分析：**
   - 使用 `top -H -p [pid]` 查看CPU和内存占用情况。
   - 使用 `jstat -gcutil [pid] [interval]` 实时观察各个内存区域的使用情况和GC次数。
   - 使用 `jmap -heap [pid]` 查看堆内存分配情况。
   - 进行堆转储分析，查找内存泄漏的根源。

------

**4. 什么是Java内存模型（JMM）？它的主要目的是什么？**

- **定义：** Java内存模型（Java Memory Model）是一种规范，它屏蔽了各种硬件和操作系统的内存访问差异，定义了在多线程环境下，一个线程对共享变量的写入何时对另一个线程可见。
- **主要目的：** 保证并发编程中的**原子性**、**可见性**和**有序性**。
  - **原子性：** 一个或多个操作在CPU执行过程中不被中断的特性。JMM通过 `lock`、`unlock` 等指令（以及高层次的 `synchronized`、`java.util.concurrent` 包）来保证。
  - **可见性：** 当一个线程修改了共享变量的值，其他线程能够立即得知这个修改。JMM通过在变量修改后将新值同步回主内存，在变量读取前从主内存刷新变量值来实现，`volatile` 关键字是这一特性的最强实现。
  - **有序性：** 对于在本线程内观察，操作是有序的；如果在一个线程中观察另一个线程，所有操作都是无序的。JMM允许编译器和处理器进行指令重排序，但规定了 `as-if-serial` 语义和 `happens-before` 原则，以保证程序的正确同步。

------

**5. Java的类加载机制。什么是双亲委派模型？**

- **类加载机制：** 虚拟机把描述类的数据从Class文件加载到内存，并对数据进行校验、转换解析和初始化，最终形成可以被虚拟机直接使用的Java类型。
- **生命周期：** 加载 -> 验证 -> 准备 -> 解析 -> 初始化 -> 使用 -> 卸载。
- **双亲委派模型：**
  - **定义：** 除了顶层的启动类加载器外，其余的类加载器都应当有自己的父类加载器。父子关系通常是组合关系（而不是继承）。
  - **工作过程：** 如果一个类加载器收到了类加载的请求，它首先不会自己去尝试加载这个类，而是把这个请求委派给父类加载器去完成，每一个层次的类加载器都是如此。因此所有的加载请求最终都应该传送到最顶层的启动类加载器中。只有当父加载器反馈自己无法完成这个加载请求（它的搜索范围中没有找到所需的类）时，子加载器才会尝试自己去完成加载。
  - **好处：** 保证了Java程序的稳定运行。例如，`java.lang.Object` 类存放在 `rt.jar` 中，无论哪一个类加载器要加载这个类，最终都是委派给启动类加载器进行加载，保证了任意一个类加载器最终得到的都是同一个 `Object` 类，避免了用户自定义的类加载器加载一个虚假的 `Object` 类破坏系统安全。

------

**6. 判断一个对象是否可以被回收？介绍一下常用的算法。**

判断对象是否存活（是否可回收）主要有两种算法：

1. **引用计数法：**
   - **原理：** 给对象中添加一个引用计数器，每当有一个地方引用它时，计数器值就加1；当引用失效时，计数器值就减1；任何时刻计数器为0的对象就是不可能再被使用的。
   - **缺点：** 很难解决对象之间相互循环引用的问题。
2. **可达性分析算法：**
   - **原理：** 通过一系列称为 **GC Roots** 的根对象作为起始节点集，从这些节点开始，根据引用关系向下搜索，搜索过程所走过的路径称为引用链。如果某个对象到 GC Roots 间没有任何引用链相连（即不可达），则证明此对象是不可能再被使用的。
   - **现状：** 目前主流的商用程序语言（Java、C#）的内存管理子系统，都是通过可达性分析算法来判定对象是否存活的。

------

**7. 哪些对象可以作为GC Roots？**

在Java中，固定可作为GC Roots的对象包括以下几种：

1. **虚拟机栈（栈帧中的本地变量表）中引用的对象：** 各个线程被调用的方法堆栈中的参数、局部变量、临时变量等。
2. **方法区中类的静态属性引用的对象：** Java类的引用类型静态变量。
3. **方法区中常量引用的对象：** 如字符串常量池里的引用。
4. **本地方法栈中JNI（即一般说的Native方法）引用的对象。**
5. **Java虚拟机内部的引用：** 基本数据类型对应的Class对象，常驻的异常对象（如 `NullPointerException`），系统类加载器。
6. **所有被同步锁（synchronized 关键字）持有的对象。**
7. 反映Java虚拟机内部情况的JMXBean、JVMTI中注册的回调、本地代码缓存等。

------

**8. 常见的垃圾回收算法**

1. **标记-清除算法：**
   - **过程：** 先标记出所有需要回收的对象，标记完成后统一回收所有被标记的对象。
   - **缺点：** 执行效率不稳定，会产生大量不连续的内存碎片。
2. **标记-复制算法：**
   - **过程：** 将可用内存按容量划分为大小相等的两块，每次只使用其中的一块。当这一块的内存用完了，就将还存活着的对象复制到另外一块上面，然后再把已使用过的内存空间一次清理掉。
   - **优点：** 实现简单，运行高效，没有内存碎片。
   - **缺点：** 可用内存缩小为原来的一半，空间浪费较多。
3. **标记-整理算法：**
   - **过程：** 标记过程仍然与“标记-清除”算法一样，但后续步骤不是直接对可回收对象进行清理，而是让所有存活的对象都向内存空间一端移动，然后直接清理掉边界以外的内存。
   - **优点：** 没有内存碎片。
   - **缺点：** 移动大量存活对象并更新所有引用，是一种复杂的操作，会加重系统停顿（Stop The World）。

------

**9. 分代收集理论。年轻代和老年代分别采用什么回收算法？为什么？**

- **理论：** 基于两个弱分代假说：1）绝大多数对象都是朝生夕死的；2）熬过越多次垃圾收集过程的对象就越难以消亡。
- **年轻代（Young Generation）：** 采用**标记-复制算法**。
  - **原因：** 因为年轻代中的对象大部分都是“朝生夕死”的，在垃圾回收时只有少量对象存活。所以使用复制算法，只需要付出少量存活对象的复制成本，就可以完成收集。通常会将新生代分为一个较大的Eden区和两个较小的Survivor区（如8：1：1）。
- **老年代（Old Generation）：** 通常采用**标记-清除算法**或**标记-整理算法**。
  - **原因：** 老年代中的对象存活率高，没有额外空间进行分配担保。如果使用复制算法，需要复制大量存活对象，效率太低。因此，一般使用标记-清除（如CMS）或标记-整理（如Serial Old）算法来处理。

------

**10. 常见的垃圾收集器有哪些？请简要介绍一下CMS和G1收集器的特点及区别。**

- **常见收集器：** Serial（串行）、ParNew（并行）、Parallel Scavenge（吞吐量优先）、Serial Old、Parallel Old、CMS（并发）、G1（分区+并发）。
- **CMS收集器（Concurrent Mark Sweep）：**
  - **特点：** 以获取最短回收停顿时间为目标的收集器，基于“标记-清除”算法实现。
  - **过程：** 初始标记（STW）-> 并发标记 -> 重新标记（STW）-> 并发清除。
  - **优点：** 并发收集、低停顿。
  - **缺点：** 对CPU资源敏感、无法处理浮动垃圾、产生大量内存碎片。
- **G1收集器（Garbage First）：**
  - **特点：** 面向服务端应用的垃圾收集器，将Java堆划分为多个大小相等的独立区域（Region）。它保留了分代思想，但不再是物理隔离，而是逻辑分代。基于“标记-整理”算法实现（局部看是复制，整体看是整理）。
  - **过程：** 初始标记（STW）-> 并发标记 -> 最终标记（STW）-> 筛选回收（STW，但可并行且可预测停顿）。
  - **优点：** 可预测的停顿时间模型，能指定在 M 毫秒的时间片段内，消耗在垃圾收集上的时间不超过 N 毫秒。不会产生内存碎片。
- **区别：**
  - **回收范围：** CMS针对老年代（配合ParNew），G1针对整个堆（Region）。
  - **内存布局：** CMS物理分代，G1逻辑分代+Region。
  - **算法：** CMS标记-清除（碎片），G1标记-整理（无碎片）。
  - **停顿时间：** G1可预测停顿时间，CMS对停顿时间没有绝对的保证。

------

**11. 什么是Minor GC、Major GC 和 Full GC？它们分别发生在什么时候？**

- **Minor GC / Young GC：**发生在年轻代的垃圾回收。
  - **触发条件：** 当年轻代 Eden 区空间不足时触发Minor GC。
  - Minor GC 非常频繁，但每次耗时较短，对应用性能影响小。
- **Major GC / Old GC：**指发生在老年代的GC。
  - **触发条件：** 通常是在 CMS 并发收集时，老年代空间不足或并发收集失败时触发。
- **Full GC：**指发生在整个 Java 堆和方法区（元空间）的 GC。频率低但 STW 时间长，对应用性能影响大。
  - **触发条件：**
    1. 调用 `System.gc()` 时，系统建议执行Full GC，但不一定立即执行。
    2. 老年代空间不足。
    3. 方法区/元空间空间不足。
    4. Minor GC 后老年代的空间分配担保失败。
    5. 使用 CMS GC 时并发失败，会退化为 Serial Old 进行Full GC。

------

**12. JVM 调优的参数都有哪些？**

JVM参数通常分为三类：标准参数（-开头，所有JVM都支持）、非标准参数（-X开头，特定HotSpot支持）和高级参数（-XX开头）。

**常见调优参数示例：**

1. **堆内存设置：**
   - `-Xms`：初始堆大小（如 `-Xms4g`）
   - `-Xmx`：最大堆大小（如 `-Xmx4g`，通常与Xms设置相同避免扩容开销）
   - `-Xmn`：年轻代大小
   - `-XX：NewRatio`：老年代与年轻代的比例（如 `-XX：NewRatio=2` 表示老年代:年轻代=2:1）
   - `-XX：SurvivorRatio`：Eden区与一个Survivor区的比例（如 `-XX：SurvivorRatio=8` 表示 Eden:Survivor=8:1）
2. **元空间设置：**
   - `-XX：MetaspaceSize` 和 `-XX：MaxMetaspaceSize`（JDK 8+）
3. **垃圾收集器选择：**
   - `-XX：+UseSerialGC`：Serial + Serial Old
   - `-XX：+UseParNewGC`：ParNew + Serial Old（不推荐单独使用）
   - `-XX：+UseConcMarkSweepGC`：ParNew + CMS + Serial Old（备用）
   - `-XX：+UseG1GC`：启用G1收集器
4. **GC日志与诊断：**
   - `-XX：+PrintGCDetails` 和 `-XX：+PrintGCDateStamps`：打印GC日志
   - `-Xloggc：/path/to/gc.log`：指定GC日志输出路径
   - `-XX：+HeapDumpOnOutOfMemoryError`：OOM时导出堆转储文件
   - `-XX：HeapDumpPath=path`：指定堆转储文件路径
5. **GC触发阈值：**
   - `-XX：CMSInitiatingOccupancyFraction=75`：CMS老年代占比达到75%时开始并发收集
   - `-XX：G1HeapRegionSize`：设置G1的Region大小
   - `-XX：MaxGCPauseMillis`：设置G1的目标停顿时间（如 `-XX：MaxGCPauseMillis=200`）



13、JVM的组成部分

根据《Java 虚拟机规范》，JVM 的架构组成主要分为 **5 个部分**：

1. **类加载子系统 (Class Loader Subsystem)**
2. **运行时数据区 (Runtime Data Area)**
3. **执行引擎 (Execution Engine)**
4. **本地接口 (Native Interface / JNI)**
5. **本地方法库 (Native Method Libraries)**



**1. 类加载子系统 (Class Loader Subsystem)**

**作用**：负责将 `.class` 文件（字节码文件）加载到内存中，并将其转换为方法区中的运行时数据结构。

类加载过程：

- **加载 (Loading)**：通过类的全限定名从不同的渠道获取定义此类的二进制字节流，将字节流所代表的静态存储结构转化为方法区中的运行时数据结构，在方法区中生成一个 `InstanceKlass`对象，保存类的所有信息，最后在堆中生成一个代表该类的 `java.lang.Class` 对象，作为访问方法区中数据的入口。

  可以使用 Java 代码扩展获取类全限定名的不同渠道。比如从本地 (ZIP、JAR)、网络和动态代理生成等途径。

- 链接 (Linking)：

  - **验证 (Verification)**：确保字节码文件符合规范，安全。
  - **准备 (Preparation)**：为静态变量分配内存空间并赋初值。
  - **解析 (Resolution)**：将符号引用替换为直接引用，指向内存中分配的空间。

- **初始化 (Initialization)**：执行类构造器 `<clinit>()` 方法，给变量赋值并执行静态代码块。

- 类加载器分类：

  - **启动类加载器 (Bootstrap ClassLoader)**：加载核心类库 (`rt.jar`)。
  - **扩展类加载器 (Extension ClassLoader)**：加载扩展类库 (`ext` 目录)。
  - **应用程序类加载器 (Application ClassLoader)**：加载用户类路径 (`classpath`) 上的类。



**2. 运行时数据区 (Runtime Data Area)**

**作用**：JVM 在运行程序时管理的内存区域。这是 JVM 内存模型的核心，**分为线程私有和线程共享两部分**。

A. 线程私有 (Thread-Private)

- 程序计数器 (Program Counter Register)：一块较小的内存空间，记录当前线程所执行的字节码行号。

  是唯一一个在 JVM 规范中没有任何 `OutOfMemoryError` 情况的区域。

- Java 虚拟机栈 (Java Virtual Machine Stack)：用于存储每个方法执行时创建的**栈帧 (Stack Frame)**，栈帧包括局部变量表、操作数栈、动态链接、方法出口等信息。

  可能抛出 `StackOverflowError` (栈溢出) 或 `OutOfMemoryError` (栈扩展失败)。

- 本地方法栈 (Native Method Stack)：与虚拟机栈类似，但服务于 **Native 方法** (通常由 C/C++ 编写)。



B. 线程共享 (Thread-Shared)

- 堆 (Heap)：JVM 管理的内存中**最大**的一块区域。
  - 存放**对象实例**和数组。
  - 是**垃圾回收 (GC)** 的主要区域。
  - 可划分为：新生代 (Eden, Survivor) 和 老年代。
- 方法区 (Method Area)：
  - 用于存储已被虚拟机加载的**类信息**、**常量**、**静态变量**、即时编译器编译后的代码缓存等。
  - **注意**：在 Java 8 及之后，方法区的实现由 **元空间 (Metaspace)** 取代了永久代 (PermGen)，元空间直接使用本地内存。



**3. 执行引擎 (Execution Engine)**

**作用**：负责执行字节码指令。

- 解释器 (Interpreter)：逐行读取字节码并立即执行。启动速度快，但执行效率相对较低。
- 即时编译器 (JIT Compiler, Just-In-Time)：将热点代码（经常执行的代码）编译成**本地机器码**。执行效率高，但编译需要时间。JVM 通常采用 **解释执行 + JIT 编译** 混合模式。
- 垃圾回收器 (Garbage Collector)：负责自动管理堆内存，回收不再使用的对象。



**4. 本地接口 (Java Native Interface / JNI)**

**作用**：供 JVM 调用本地 C/C++ 方法



**5. 本地方法库 (Native Method Libraries)**

**作用**：是 JVM 调用本地 C/C++ 方法的方法库，为 JVM 提供底层操作系统的支持（如 I/O 操作、线程调度等）。



14、类加载过程、类加载器



## Spring 高频八股

1、什么是 **IoC** 和 **AOP**？AOP的实现原理是什么
2、@Transactional 注解在什么情况下会失效
3、Spring Boot的自动配置原理是什么？是如何按需加载的
4、Spring **Bean** 的作用域有哪些？请分别说明它们的**生命周期**和适用场景
5、IoC容器的启动过程是怎样的？Bean的生命周期有哪些关键步骤
6、Spring怎么解决**循环依赖**问题的，什么是三级缓存？
7、Spring **事务**的传播行为有哪些
8、AOP有哪些通知类型？它们的执行顺序是怎样的
9、BeanFactory和ApplicationContext有什么区别
10、Spring 事务底层是如何实现的？事务信息是如何与当前线程绑定的
11、Spring提供了哪些解决方案在后端解决跨域问题
12、@Autowired和@Resource注解有什么区别
13、自定义一个Spring Boot Starter需要哪些步骤

14、Spring的常见注解

15、SpringBoot的常见注解

16、通俗易懂的语言描述一下AOP和IOC，哪些注解用到了AOP？

17、动态代理的方式有哪几种？有什么区别？

18、SpringBoot启动类上有哪些注解

19、怎么理解 SpringBoot ？



**⭐️1、IoC和AOP？AOP的实现原理是什么**

**IoC（Inversion of Control 控制反转）**是一种思想，描述的是 Java 开发中对象的创建和管理问题。IoC 将对象的创建和管理权力交给 Spring 容器，而不通过 new 关键字创建对象。后续需要哪个对象直接去 IoC 容器里面取即可。DI 是 IoC 最常见以及最合理的实现方式。

依赖注入 DI 是一种设计模式，用来实现控制反转 IoC。简单来说就是对象不自己创建依赖，而是由外部传进来。这样做最大的好处是**解耦**。包括构造器注入、Setter方法注入和接口注入(用的较少)。

**AOP（Aspect Oriented Programming 面向切面编程）**能将与业务无关，但被业务模块共同调用的逻辑封装起来（例如日志记录、事务管理、权限控制等），以减少重复代码，降低模块间的耦合度，有利于代码的可拓展性和可维护性。

**AOP的实现原理**：AOP 的常见实现方式有动态代理（运行时增强）和静态代理（编译时增强）。

Spring AOP 基于动态代理，如果要代理的对象实现了某个接口，那么会使用 **JDK Proxy **去创建代理对象，如果要代理的对象没有实现接口，会使用 **Cglib** 生成一个被代理对象的子类来作为代理。也可以使用静态代理 **AspectJ** 

**Spring AOP 的代理机制：**当一个 Bean 被 Spring 容器管理且包含 AOP 注解（如 @Transactional、@Cacheable、@Async、@Scheduled）时，Spring 会为它创建一个 代理对象（Proxy）。**所有来自外部的调用（如 Controller 调用 Service）都会经过这个代理对象。**代理对象会在调用目标方法前/后执行切面逻辑（如开启事务、缓存检查、异步执行、定时任务等）。

**⭐️2、@Transactional注解在什么情况下会失效**

1. **修饰非public方法时**
2. **修饰的 public 方法所在类没有被 Spring 容器管理时。**
3. **类内部自调用方法时**
4. **异常类型不匹配时**：默认只回滚 RuntimeException 和 Error，检查异常不回滚，导致事务失效
5. **传播行为设置不当时**：如Propagation.NOT_SUPPORTED、Propagation.NEVER 就不支持事务
6. **数据库引擎不支持事务时**：如MyISAM引擎

**3、Spring Boot的自动配置原理是什么？是如何按需加载的**

SpringBoot 采用自动配置原理，根据应用程序中引入的依赖和配置，SpringBoot 自动配置整个应用程序的环境。
SpringBoot 的自动配置是基于条件的按需配置，通过注解驱动 + SPI 机制，根据项目依赖、环境配置和自定义规则，自动向IoC容器注入对应Bean，替代传统 Spring 的 XML 手动配置。
自动装配原理：

![1773370900023](C:\Users\85448\AppData\Roaming\Typora\typora-user-images\1773370900023.png)

SpringBoot 启动类下 `@SpringBootApplication` 注解中的 `@EnableAutoConfiguration` 注解是实现自动装配的核心注解

![1773370983206](C:\Users\85448\AppData\Roaming\Typora\typora-user-images\1773370983206.png)

该注解中的 `@AutoConfigurationPackage` 注解会将主应用程序类(即启动类)所在包及其子包下的所有组件注册到 IoC 容器中，`@Import` 注解会导入 `AutoConfigurationImportSelector` 类 ，该类实现了 `ImportSelector`接口，也就实现了该接口中的 `selectImports()` 方法，可以动态选择需要导入的自动配置类。

具体来说：在应用程序启动时，`AutoConfigurationImportSelector`类会扫描类路径，加载META-INF/spring.factories(SpringBoot2.7.0版本之前)META-INF/spring/...AutoConfiguration.imports(2.7.0版本之后)文件中所有实现了 `AutoConfiguration` 接口的自动配置类，然后对每一个发现的自动配置类使用条件判断，通过条件注解 `@Conditional`（如 `@ConditionalOnClass`、`@ConditionalOnMissingBean`）筛选出符合当前环境的配置类，如果满足导入条件，则将该配置类注册到 IoC 容器中。

遵循 “自定义优先” 原则，开发者可通过手动配置 Bean 或禁用(exclude)自动配置类，覆盖默认行为，最终实现 “按需配置、简化开发” 的目标。

**按需加载**：使用各种条件注解，如：

- `@ConditionalOnClass`：类路径存在指定类才加载
- `@ConditionalOnMissingBean`：容器中没有指定Bean才加载
- `@ConditionalOnProperty`：配置文件有指定属性才加载

**⭐️4、Spring Bean的作用域**

- **singleton** : 表示 IoC 容器中只有唯一的 bean 实例，Spring 中 bean 默认都是单例的。

- **prototype** : 每次获取 Bean 时都会创建一个新的 Bean 实例。也就是说连续 `getBean()` 两次，得到的是两个不同的 Bean 实例。

  还有四种仅在 Web 应用下可用的作用域：

- **request**：每一次 **HTTP 请求**都会产生一个 bean（请求 bean），该 bean 仅在当前 HTTP request 内有效。

- **session**：每一次**来自新 session 的 HTTP 请求**都会产生一个 bean（会话 bean），该 bean 仅在当前 HTTP session 内有效。

- **application**：每个 Web 应用在启动时创建一个 Bean(应用 Bean)，该 bean 仅在当前应用**启动时间内**有效。

- **websocket**：每一次 WebSocket 会话产生一个 bean。

**如何配置 bean 的作用域？**

在 xml 文件中配置 scope 属性：

```xml
<bean id="..." class="..." scope="singleton"></bean>
```

在 `@Scope` 注解的 value 属性中指定作用域：

```java
@Bean
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public Person personPrototype() {
    return new Person();
}
```

**5、IoC容器的启动过程和Bean的生命周期**

**IoC容器启动过程**：

总: IOC容器的初始化，核心工作是在 AbstractApplicationContext.refresh 方法中完成的

分：在refresh方法中主要做了这么几件事

​         1. 准备BeanFactory，在这一块需要给BeanFacory设置很多属性，比如类加载器、Environment等

​         2.  执行BeanFactory后置处理器，这一阶段会扫描要放入到容器中的Bean信息，得到对应的BeanDefinition（注意，这里只扫描，不创建）

​         3.  注册BeanPostProcesor，我们自定义的BeanPostProcessor就是在这一个阶段被加载的, 将来Bean对象实例化好后需要用到

​         4.  启动tomcat

​         5.  实例化容器中实例化非懒加载的单例Bean, 这里需要说的是，多例Bean和懒加载的Bean不会在这个阶段实例化，将来用到的时候再创建

​          6.  当容器初始化完毕后，再做一些扫尾工作，比如清除缓存等  

总：简单总结一下，在IOC容器初始化的的过程中，首先得准备并执行BeanFactory后置处理器，其次得注册Bean后置处理器,并启动tomcat，最后需要借助于BeanFactory完成Bean的实例化

1. 资源定位：读取配置文件/注解
2. 加载解析：将配置信息解析成BeanDefinition
3. 注册：将BeanDefinition注册到容器
4. 实例化：创建Bean实例（懒加载除外）

**Bean的生命周期**：

1. **创建 Bean 的实例**：Bean 容器首先会找到配置文件中的 Bean 定义，然后使用 Java 反射来创建 Bean 实例。
2. **Bean 属性赋值/填充**：为 Bean 设置相关属性和依赖，例如`@Autowired` 等注解注入的对象、`@Value` 注入的值、`setter`方法或构造函数注入依赖和值、`@Resource`注入的各种资源。
3. Bean 初始化： 
   - 如果 Bean 实现了 `BeanNameAware` 接口，调用 `setBeanName()`方法，传入 Bean 的名字。
   - 如果 Bean 实现了 `BeanClassLoaderAware` 接口，调用 `setBeanClassLoader()`方法，传入 `ClassLoader`对象的实例。
   - 如果 Bean 实现了 `BeanFactoryAware` 接口，调用 `setBeanFactory()`方法，传入 `BeanFactory`对象的实例。
   - 与上面的类似，如果实现了其他 `*.Aware`接口，就调用相应的方法。
   - 如果有和加载这个 Bean 的 Spring 容器相关的 `BeanPostProcessor` 对象，执行`postProcessBeforeInitialization()` 方法
   - 如果 Bean 实现了`InitializingBean`接口，执行`afterPropertiesSet()`方法。
   - 如果 Bean 在配置文件中的定义包含 `init-method` 属性，执行指定的方法。
   - 如果有和加载这个 Bean 的 Spring 容器相关的 `BeanPostProcessor` 对象，执行`postProcessAfterInitialization()` 方法。
4. 销毁 Bean：销毁并不是说要立马把 Bean 给销毁掉，而是把 Bean 的销毁方法先记录下来，将来需要销毁 Bean 或者销毁容器的时候，就调用这些方法去释放 Bean 所持有的资源。 
   - 如果 Bean 实现了 `DisposableBean` 接口，执行 `destroy()` 方法。
   - 如果 Bean 在配置文件中的定义包含 `destroy-method` 属性，执行指定的 Bean 销毁方法。或者，也可以直接通过`@PreDestroy` 注解标记 Bean 销毁之前执行的方法。
5. 实例化：通过反射创建对象
6. 属性赋值：填充依赖
7. 初始化前：执行各种Aware接口
8. 初始化：调用init方法
9. 初始化后：AOP代理
10. 使用：放入容器
11. 销毁：容器关闭时执行destroy

总: Bean的生命周期总的来说有4个阶段，分别有创建对象，初始化对象，使用对象以及销毁对象，而且这些工作大部分是交给Bean工厂的doCreateBean方法完成的
分：
         首先，在创建对象阶段，先调用构造方法实例化对象，对象有了后会填充该对象的内容，其实就是处理依赖注入
         其次，对象创建完毕后，需要做一些初始化的操作，在这里涉及到几个扩展点。
	1. 执行Aware感知接口的回调方法
	2.执行Bean后置处理器的postProcessBeforeInitialization方法
	3.执行InitializingBean接口的回调，在这一步如果Bean中有标注了@PostConstruct注解的方法，会先执行它
	4.执行Bean后置处理器的postProcessAfterInitialization
	把这些扩展点都执行完，Bean的初始化就完成了
         接下来，在使用阶段就是程序员从容器中获取该Bean使用即可
         最后，在容器销毁之前，会先销毁对象，此时会执行DisposableBean接口的回调，这一步如果Bean中有标注了@PreDestroy接口的函数，会先执行它	
总：简单总结一下，Bean的生命周期共包含四个阶段，其中初始化对象和销毁对象我们程序员可以通过一些扩展点执行自己的代码

**6、Spring怎么解决循环依赖？什么是三级缓存**

Spring 循环依赖是指 Bean 对象循环引用，如两个或多个 Bean 之间相互持有对方的引用。

```java
@Component
public class CircularDependencyA {
    @Autowired
    private CircularDependencyB circB;
}

@Component
public class CircularDependencyB {
    @Autowired
    private CircularDependencyA circA;
}
```

单个对象的自我依赖也会出现循环依赖，但这种概率极低，属于是代码编写错误。

```java
@Component
public class CircularDependencyA {
    @Autowired
    private CircularDependencyA circA;
}
```

Spring 框架通过使用三级缓存来解决这个问题，确保即使在循环依赖的情况下也能正确创建 Bean。

Spring 的三级缓存其实就是三个 Map：

```java
// 一级缓存
/** Cache of singleton objects: bean name to bean instance. */
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

// 二级缓存
/** Cache of early singleton objects: bean name to bean instance. */
private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

// 三级缓存
/** Cache of singleton factories: bean name to ObjectFactory. */
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
```

简单来说，Spring 的三级缓存包括：

1. **一级缓存（singletonObjects）**：存放已经完成实例化、属性填充、初始化的最终 Bean，是一个单例池。一般情况下都是从这里获取 Bean，但并不是所有的 Bean 都在单例池里面，例如原型 Bean 就不在里面。
2. **二级缓存（earlySingletonObjects）**：存放尚未完成属性填充的过渡 Bean，也就是三级缓存中`ObjectFactory`产生的对象，与三级缓存配合使用。同时可以防止 AOP 情况下每次调用`ObjectFactory#getObject()`都产生新的代理对象。
3. **三级缓存（singletonFactories）**：存放Bean 的`ObjectFactory`，`ObjectFactory`的`getObject()`方法可以获得实例化的对象或者代理对象（如果 Bean 被 AOP 切面代理），最终调用的是`getEarlyBeanReference()`方法。三级缓存只对单例 Bean 生效。

接下来说一下 Spring 创建 Bean 的流程：

1. 先去 **一级缓存 singletonObjects** 中获取，存在就返回；
2. 如果不存在或者对象正在创建中，就去 **二级缓存 earlySingletonObjects** 中获取；
3. 如果还没有获取到，就去 **三级缓存 singletonFactories** 中获取，通过执行 `ObjectFacotry` 的 `getObject()` 可以获取该对象，获取成功之后，从三级缓存移除，并将该对象加入到二级缓存中。

Spring 在创建 Bean 的时候，如果允许循环依赖，就会将刚刚实例化完成但是属性还没有初始化完的 Bean 对象给提前暴露出去，通过 `addSingletonFactory` 方法向三级缓存中添加一个 `ObjectFactory` 对象。

在 Spring 创建 Bean 时，如果一、二级缓存都取不到对象时，会去三级缓存中通过 `ObjectFactory` 的 `getObject` 方法获取对象。

```java
class A {
    // 使用了 B
    private B b;
}
class B {
    // 使用了 A
    private A a;
}
```

以上面的循环依赖代码为例，整个解决循环依赖的流程如下：

- 当 Spring 创建 A 之后，发现 A 依赖了 B ，又去创建 B，B 依赖了 A ，又去创建 A；
- 在 B 创建 A 的时候就发生了循环依赖，此时 A 仅完成了实例化，没有完成初始化（即还没有完成属性 B 的填充），因此在 **一二级缓存** 中肯定没有 A；但 A 完成实例化后会在三级缓存中添加一个 `ObjectFactory` 对象
- 此时 B 可以去三级缓存中调用 `getObject()` 方法获取 A 的 **前期暴露对象** ，即完成实例化的对象；
- 然后将 A 的 `ObjectFactory` 从三级缓存中移除，并将其放入到二级缓存中，同时将其注入到依赖，完成 B 的依赖注入。将来如果还有其他的类循环依赖了 A，就可以直接在二级缓存中找到它了
- B 完成依赖注入后，初始化就完成了，可以将 B 的对象放入一级缓存，并删除三级缓存中的`ObjectFactory`
- 此时 A 也可以完成 B 的注入，同时将 A 的对象放入一级缓存，循环依赖得以解决。

**只用两级缓存够吗？** 

没有 AOP 的情况下可以只使用一、二级缓存来解决循环依赖问题。涉及到 AOP 时，三级缓存就确保了即使在 Bean 的创建过程中有多次对早期引用的请求，也只返回一个代理对象，避免了同一个 Bean 有多个代理对象问题

**最后总结一下 Spring 如何解决三级缓存**：

如果发生循环依赖的话，就去 **三级缓存 singletonFactories** 中拿到存储的 `ObjectFactory` 并调用它的 `getObject()` 方法来获取这个循环依赖对象的前期暴露对象（虽然还没初始化完成，但是可以拿到该对象在堆中的存储地址），并将这个前期暴露对象放到二级缓存中，这样就可以完成依赖注入，解决循环依赖。

缺点就是**增加了内存开销**（需要维护三级缓存，也就是三个 Map），**降低了性能**（需要进行多次检查和转换）。并且**非单例的 bean 和`@Async`注解的 bean 无法支持循环依赖**。

**面试回答：**

Bean的循环依赖指的是 A 依赖 B，B 又依赖 A 这样的依赖闭环问题，在 Spring 中，通过三个对象缓存区来解决循环依赖问题，分别是：

- `singletonObjects`：用来存放已经完成实例化、属性填充、初始化的最终 Bean，是一个单例池。一般情况下都是从这里获取 Bean，但并不是所有的 Bean 都在单例池里面，例如原型 Bean 就不在里面。
- `earlySingletonObjecs`：用来存放尚未完成属性填充的过渡 Bean，也就是三级缓存中`ObjectFactory`产生的对象，与三级缓存配合使用。同时可以防止 AOP 情况下每次调用`ObjectFactory#getObject()`都产生新的代理对象。
- `singletonFactories`：用来存放 Bean 的 `ObjectFactory`。

假设现在 A 依赖 B，B 依赖 A，则整个 Bean 的创建过程是这样的

- 当 Spring 创建 A 之后，发现 A 依赖了 B ，又去创建 B，而 B 依赖了 A ，又去创建 A；
- 在 B 创建 A 的时候就发生了循环依赖，此时 A 仅完成了实例化，没有完成初始化（即还没有完成属性 B 的填充），因此在**一二级缓存**中找不到 A；但 A 完成实例化后会在三级缓存中添加一个 `ObjectFactory` 对象
- 此时 B 可以去**三级缓存**中调用 A 的 `ObjectFactory` 的 `getObject()` 方法获取 A 的 **前期暴露对象**；
- 然后将 A 的 `ObjectFactory` 从三级缓存中移除，并将其 **前期暴露对象** 放入到二级缓存中，同时将其注入到 B 中，完成 B 的依赖注入。将来如果还有其他类循环依赖 A，就可以直接在二级缓存中找到 A 了。
- B 完成依赖注入后，初始化就完成了，可以将 B 的对象放入一级缓存，并删除三级缓存中的`ObjectFactory`
- 此时 A 也可以完成 B 的注入，同时将 A 的对象放入一级缓存，循环依赖得以解决。

**@Lazy 能解决循环依赖吗？**

`@Lazy` 用来标识类是否需要懒加载/延迟加载，可以作用在类上、方法上、构造器上、方法参数上、成员变量中。

Spring Boot 2.2 新增了**全局懒加载属性**，开启后全局 bean 被设置为懒加载，需要时再去创建。

配置文件配置全局懒加载：

```properties
#默认false
spring.main.lazy-initialization=true
```

编码的方式设置全局懒加载：

```java
SpringApplication springApplication = new SpringApplication(Start.class);
springApplication.setLazyInitialization(false);
springApplication.run(args);
```

如非必要尽量不要用全局懒加载。全局懒加载会让 Bean 第一次使用的时候变慢，并且它会延迟应用程序问题的发现，即只有当 Bean 被初始化时，才可能发现程序的问题。

如果一个 Bean 没有被标记为懒加载，那么它会在 Spring IoC 容器启动的过程中被创建和初始化。

否则在第一次被请求时才创建，这可以帮助减少应用启动时的初始化时间，也可以用来解决循环依赖问题。

**`@Lazy` 如何解决循环依赖问题？**

假设 A 和 B 发生了循环依赖，在 A 的构造器上添加 `@Lazy` 之后，可以延迟 B 的实例化，加载的流程如下：

- 首先 Spring 创建 A 的 Bean，创建时需要注入 B；
- 由于在 A 上标注了 `@Lazy` 注解，因此 Spring 会创建一个 B 的代理对象，将这个代理对象注入到 A 中， A 顺利完成初始化。
- 后续直到 A 的实例对象调用 B 的方法时才开始执行 B 的实例化和初始化，此时由于 A 已经创建完毕了，就可以在 B 中直接注入 A，顺利完成 B 的初始化。

从上面的加载流程可以看出， **`@Lazy` 解决循环依赖的关键在于代理对象的使用**。

- **没有 @Lazy 的情况下**：在 Spring 容器初始化 `A` 时会立即尝试创建 `B`，而在创建 `B` 的过程中又会尝试创建 `A`，最终导致循环依赖。需要通过三级缓存解决。
- **使用 @Lazy 的情况下**：Spring 初始化 `A` 时不会创建 `B`，而是注入一个 `B` 的代理对象，此时`A` 的初始化可以顺利完成，等到 `A` 的对象实例实际调用 `B` 的方法时，代理对象才会触发 `B` 的真正初始化，此时由于 A 已经初始化完成，所以 B 可以直接注入 A，初始化可以顺利完成。

`@Lazy` 能够在一定程度上打破循环依赖链，允许 Spring 容器顺利地完成 Bean 的创建和注入。但这并不是一个根本性的解决方案，尤其是在构造函数注入、复杂的多级依赖场景中，`@Lazy` 无法有效地解决问题。

因此，最佳实践仍然是尽量避免设计上的循环依赖。

**注意**：只能解决setter注入的循环依赖，构造器注入无法解决

**⭐️7、Spring事务的传播行为**

7种传播行为：

1. **REQUIRED**（默认）：支持当前事务，没有则新建
2. **REQUIRES_NEW**：新建事务，挂起当前事务
3. **NESTED**：嵌套事务
4. **MANDATORY**：支持当前事务，没有则抛异常
5. **SUPPORTS**：支持当前事务，没有则以非事务运行
6. **NOT_SUPPORTED**：以非事务运行，挂起当前事务
7. **NEVER**：以非事务运行，有事务则抛异常

**⭐️8、AOP的通知类型和通知执行顺序**

**通知类型**：

1. **@Before**：前置通知（方法执行前）
2. **@After**：后置通知（方法执行后，无论是否异常），After在AfterReturning和AfterThrowing之后执行。
3. **@AfterReturning**：返回通知（正常返回后）
4. **@AfterThrowing**：异常通知（抛出异常后）
5. **@Around**：环绕通知（最强大，可控制方法执行）

**执行顺序**（正常情况）：

```
@Around前半部分 → @Before → 目标方法 → @AfterReturning → @After → @Around后半部分
```

**异常情况**：

```
@Around前半部分 → @Before → 目标方法（异常） → @AfterThrowing → @After
```

**9、BeanFactory和ApplicationContext的区别**

1. **继承关系**：ApplicationContext继承自BeanFactory
2. **功能**：
   - BeanFactory：基础的IoC容器，延迟加载
   - ApplicationContext：更高级的容器，支持国际化、事件发布、AOP等，立即加载
3. **使用场景**：
   - BeanFactory：嵌入式设备、资源受限场景
   - ApplicationContext：大多数企业应用

**10、Spring事务底层实现**



**11、Spring解决跨域的方法**



**12、@Autowired 和 @Resource 的区别**

| 特性             | @Autowired                       | @Resource                          |
| ---------------- | -------------------------------- | ---------------------------------- |
| 来源             | Spring                           | JDK（JSR-250）                     |
| 装配方式         | 先按类型（Type），再按名称       | 默认按名称（Name），找不到再按类型 |
| 注入范围         | 支持构造器、Setter方法、字段注入 | 支持方法和字段                     |
| 按名称注入方式   | 需要配合@Qualifier注解           | 直接在name属性指定名称             |
| 是否支持@Primary | 支持@Primary注解指定优先Bean     | 不支持                             |
| 允许注入null？   | 通过required=false允许注入null   | 始终允许注入null                   |

**@Autowired**

```java
@Autowired
private UserService userService;  // 按类型匹配

@Autowired
@Qualifier("userService")
private UserService userService;  // 指定 Bean 名称

@Autowired(required = false)
private OptionalService service;  // 允许为 null
```

**@Resource**

```java
@Resource
private UserService userService;  // 默认按字段名查找 Bean

@Resource(name = "userService")
private UserService service;      // 显式指定 Bean 名称

@Resource(type = UserService.class)
private UserService service;      // 按类型注入（较少用）
```

假设有一个接口 `StudentService` 和两个实现类：

```java
public interface StudentService {
    void study();
}

@Component("studentOne") // Bean 名称为 studentOne
public class StudentOneServiceImpl implements StudentService {
    // ...
}

@Component("studentTwo") // Bean 名称为 studentTwo
public class StudentTwoServiceImpl implements StudentService {
    // ...
}
```

**只使用 @Autowired（会报错）**

```java
@Autowired // 试图注入 StudentService 类型，但找到两个，Spring 不知道用哪个
private StudentService studentService;
// 结果：启动报错 NoUniqueBeanDefinitionException
```

**需要@Autowired + @Qualifier（精确指定）**

```java
@Autowired
@Qualifier("studentOne") // 指定要注入 Bean id 为 "studentOne" 的那个
private StudentService studentService;
// 结果：成功注入 StudentOneServiceImpl 的实例
```

`@Qualifier`本身**不能独立工作**，必须配合 `@Autowired` 使用。它相当于一个**筛选器**，在 `@Autowired` 根据类型找到多个候选 Bean 后，根据 `@Qualifier` 指定的名称来确定最终要注入哪一个。

**13、自定义Spring Boot Starter步骤**



**⭐️14、Spring常见注解**

1个组件类+5个mvc+4个配置类+1个事务

- **@Component**：表示一个组件类，会被 Spring 的 IoC 容器扫描并注册为 Bean。
- **@Repository/Mapper**：表示 **数据访问层组件（DAO）**。
- **@Service**：表示 **业务逻辑层组件**。
- **@Controller**：表示 **控制层组件**，主要用于 **Spring MVC** 处理 HTTP 请求。
- **@Autowired**：用于 **自动依赖注入**。Spring 会根据 **类型** 自动查找 Bean 并注入。
- **@Qualifier**：当存在多个同类型 Bean 时，指定注入哪个 Bean。
- **@Configuration**：表示 **配置类**，用于替代 XML 配置文件。通常配合 `@Bean` 使用。
- **@Bean**：用于在配置类中 **手动声明 Bean**。
- **@Scope**：用于指定 Bean 的作用域。
- **@Import**：用于导入配置类
- **@Transactional：**用于开启 **事务管理**。



**⭐️15、Spring Boot常见注解**

3启动类+2配置属性+3mvc+4条件

- **@SpringBootApplication**：Spring Boot **启动类核心注解**。它是三个注解的组合：`@SpringBootConfiguration`、`@EnableAutoConfiguration`、`@ComponentScan`

  作用：开启自动配置和组件扫描，并标识主程序入口

- **@EnableAutoConfiguration**：开启 **自动配置机制**。

- **@ComponentScan：**指定 **Spring 扫描 Bean 的包路径**，默认为启动类所在包及其子包。

- **@ConfigurationProperties**：用于 **读取配置文件中指定 prefix 前缀的属性**，并配置属性绑定

- **@EnableConfigurationProperties**：**开启对 @ConfigurationProperties 注解的支持**

- **@RestController：**用于声明 **RESTful** 风格的 web服务。它是两个注解组合：`@Controller`、`@ResponseBody`，声明指定类为一个 Controller 并返回 JSON 格式的数据。

- **@RequestMapping：**用于 **映射 HTTP 请求路径**，适合指定公共路径前缀，如 @RequestMapping("/user")

- **@GetMapping、@PostMapping等：**是 `@RequestMapping` 的简化版本，`@GetMapping` 等同于 `@RequestMapping(method = RequestMethod.GET)`，其指定的路径将拼接上`@RequestMapping` 的路径。

- **@ConditionalOnClass**：条件注解，自动配置时，指定类存在时对应配置类才生效，才加载对应的 Bean

- **@ConditionalOnMissingBean**：条件注解，自动配置时，不存在指定 Bean 时生效

- **@ConditionalOnProperty**：条件注解，自动配置时，存在指定属性时生效

- **@ConditionalOnWebApplication**：条件注解，自动配置时，存在指定 Web 应用时生效



**16、哪些注解用了AOP**

**用到AOP的注解**：

- **@Transactional**：事务管理
- **@Cacheable**：缓存
- **@Async**：异步执行
- **@PreAuthorize**：权限控制
- **@Retryable**：重试机制

**17、动态代理的方式及区别**

两种方式：

**JDK动态代理**：

- 基于接口
- 通过反射机制
- 要求目标类必须实现接口
- 性能相对较好（新版本）

**CGLIB动态代理**：

- 基于继承
- 通过字节码技术生成子类
- 目标类和方法不能是final
- 可代理没有接口的类

**区别**：

- **实现方式**：JDK基于接口，CGLIB基于继承
- **性能**：JDK 1.8+后JDK性能优于CGLIB
- **使用场景**：有接口用JDK，无接口用CGLIB

**18、SpringBoot启动类上的注解**

主要是`@SpringBootApplication`，它组合了三个注解：

1. **@SpringBootConfiguration**：标记为配置类
2. **@EnableAutoConfiguration**：开启自动配置
3. **@ComponentScan**：组件扫描

**完整写法**：

```java
@SpringBootApplication(scanBasePackages = "com.example")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**等价于**：

```java
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.example")
public class Application {
    // 启动方法
}
```

19、怎么理解 SpringBoot ？

简要回答：SpringBoot是Spring框架的简化版，通过自动配置减少XML配置内容，起步依赖整合常用的技术栈如SpringMVC、MyBatis等，内置Tomcat实现jar包的独立运行，解决Spring配置繁琐，依赖管理复杂的问题，使开发者专注业务而非框架配置。

详细回答：

SpringBoot是基于Spring的快速开发脚手架。它提供一种快速启动的方式，自动配置和约定优于配置的原则极大地简化了Spring应用的搭建、开发和部署过程。

SpringBoot使用内嵌服务器的方式，将Tomcat、Jetty等服务器嵌入到应用中，可以将应用程序打包成一个可执行的JAR文件，无需部署到外部容器，简化项目的部署和运行。

SpringBoot采用自动配置的机制，根据应用程序中引入的依赖和配置，SpringBoot自动配置整个应用程序的环境。

 SpringBoot的自动配置是基于条件的按需配置，本质是通过注解驱动+SPI机制，根据项目依赖、环境配置、自定义规则，自动向IoC容器注入对应Bean，替代传统Spring的XML手动配置。

 **SpringBoot自动装配原理：**@EnableAutoConfiguration是实现自动装配的核心注解，该注解中 @AutoConfigurationPackage 注解会将主应用程序类(即启动类)所在包及其子包下的所有组件注册到IoC容器中，@Import注解会导入AutoConfigurationImportSelector类 ，该类实现了ImportSelector接口，也就实现了该接口中的selectImports()方法，可以动态选择需要导入的自动配置类。具体来说：在应用程序启动时，AutoConfigurationImportSelector类会扫描类路径，加载META-INF/spring.factories(SpringBoot2.7.0版本之前)META-INF/spring/...AutoConfiguration.imports(2.7.0版本之后)文件中所有实现了AutoConfiguration接口的自动配置类，然后对每一个发现的自动配置类使用条件判断，通过条件注解@Conditional（如@ConditionalOnClass、@ConditionalOnMissingBean）筛选出符合当前环境的配置类，如果满足导入条件，则将该配置类注册到IoC容器中。遵循 “自定义优先” 原则，开发者可通过手动配置 Bean 或禁用(exclude)自动配置类，覆盖默认行为，最终实现 “按需配置、简化开发” 的目标。

 

SpringBoot提供了快速的项目启动器，不同的Starter将常用的技术栈的依赖整合，比如spring-boot-starter-web包含了SpringMVC、Jackson等Web开发常用的依赖，开发者只需引入一个依赖，无需手动管理版本，避免冲突。

SpringBoot遵循约定优于配置的原则，预设默认的配置和约定，开发者按照这些约定进行开发，可以大大减少配置文件的编写。

 SpringBoot提供特定的项目结构，将主应用程序类置于根包，将控制器类、服务类、数据访问类等分别放在相应子包中，使开发者更易理解项目结构与组织，新成员加入项目也能快速定位各功能代码的位置，提升协作效率。

 SpringBoot提供了大量默认配置，比如连接数据库、设置Web服务器、处理日志等，开发者无需配置日志级别、输出格式与位置。

 SpringBoot的自动化配置也是约定大于配置的体现，通过分析项目的依赖和环境，自动配置应用程序的行为。



# JUC八股

## 线程

### ⭐️线程进程协程

#### 何为进程？

- 进程是程序的一次执行过程，是系统运行程序的基本单位，程序是静止的而进程是动态的。系统运行一个程序就是一个进程从创建，运行到消亡的过程。
- 在 Java 中，当我们启动 main 函数时其实就是启动了一个 JVM 的进程，而 main 函数所在的线程就是这个进程中的一个线程，也称主线程。
- 在 Windows 中通过查看任务管理器就可以看到当前正在运行的进程（`.exe` 文件的运行）。

#### 何为线程？

- 线程是比进程更小的执行单位，一个进程在其执行的过程中可以产生多个线程。同类的多个线程共享进程的堆和方法区资源，但每个线程有自己的程序计数器、Java虚拟机栈和本地方法栈，所以系统在各个线程之间做切换工作时，负担要比进程小得多，也正因为如此，线程也被称为轻量级进程。
- Java 程序天生就是多线程程序，一个 Java 程序的运行是 main 线程和多个其他线程同时运行。

#### 何为协程？

- 协程是一种轻量级线程，其调度完全由用户控制，拥有自己的寄存器上下文和栈。
- 协程调度切换时，将寄存器上下文和栈保存，在切回来的时候，恢复先前保存的寄存器上下文和栈。切换快，内存占用低，支持百万级的超高并发。



**进程与协程的比较**

- 一个线程可以有多个协程，一个进程也可以单独拥有多个协程；
- 进程线程都是同步机制，而协程则是异步机制；



### 线程与协程的区别？

**1. 调度方式（核心区别）**

- **线程：抢占式调度**。线程由操作系统的内核负责调度。**线程上下文切换的开销较大**。
- **协程：协作式调度**。协程由用户控制调度。**协程切换的开销非常小**（通常只需要保存几个寄存器和栈指针，类似于函数调用的开销）。

**2. 栈内存占用**

- **线程**通常拥有**固定的栈空间**（例如默认 1MB 或 8MB）。这意味着如果你创建 10,000 个线程，仅栈内存就需要占用 10GB 以上，因此线程数量受限于物理内存。
- **协程**通常拥有**极小的的栈空间**，内存占用极低，一台普通的服务器可以轻松创建**几十万甚至上百万个协程**。

**3. 并发与并行**

- **线程**在多核CPU上可以实现真正的**并行**。由于是操作系统调度，线程天然可以利用多核资源。
- **协程**协程本身是**并发**的，但不一定是并行的。在单线程中一次只能执行一个协程，无法利用多核。

**4. 锁与同步**

- **线程**：多线程必须使用互斥锁等机制来确保线程安全。
- **协程通常不需要加锁**



### ⭐️简要描述线程与进程的关系、区别及优缺点？

下图是 Java 内存区域，通过下图我们从 JVM 的角度来说一下线程和进程之间的关系。

![Java 运行时数据区域(JDK1.8 之后)](https://oss.javaguide.cn/github/javaguide/java/jvm/java-runtime-data-areas-jdk1.8.png)

- 一个进程中可以有多个线程，多个线程共享进程的堆和方法区，但是每个线程有自己的程序计数器、Java虚拟机栈和本地方法栈。进程共享内存、磁盘文件、消息队列等数据。
- 进程是程序的一次执行过程，是系统运行程序的基本单位，系统运行一个程序就是一个进程从创建，运行到消亡的过程。而线程是进程划分成的更小的运行单位。
- 线程和进程的最大区别在于**各进程一般是独立的**，**而各线程则不一定**，因为同一进程中的线程可能会相互影响
- **线程执行开销小，但不利于资源的管理和维护**；而**进程则相反**。

**思考：**为什么程序计数器、Java虚拟机栈和本地方法栈是线程私有的？为什么堆和方法区是线程共享的？

#### 程序计数器为什么是私有的?

程序计数器主要有下面两个作用：

- JVM 通过改变程序计数器来依次读取指令，从而实现代码的流程控制，如分支、循环、异常处理等逻辑。
- 在多线程情况下，程序计数器用于记录当前线程执行的位置，从而当线程被切换回来的时候能够知道该线程上次执行到哪儿了。

需要注意的是，如果执行的是 native 方法，那么程序计数器记录的是 undefined 地址，只有执行的是 Java 代码时程序计数器记录的才是下一条指令的地址。

**所以，程序计数器线程私有主要是为了线程切换后能恢复到正确的执行位置，避免被其它线程修改。**

#### 虚拟机栈和本地方法栈为什么是私有的?

虚拟机栈： 每个 Java 方法在执行之前会创建一个栈帧用于存储局部变量表、操作数栈、常量池引用等信息。从方法调用直至执行完成的过程，就对应着一个栈帧在 Java 虚拟机栈中入栈和出栈的过程。

本地方法栈： 和虚拟机栈所发挥的作用非常相似，区别是虚拟机栈为虚拟机执行 Java 方法服务，而本地方法栈为虚拟机使用到的 Native 方法服务。 在 HotSpot 中两个栈合二为一。

**所以，Java虚拟机栈和本地方法栈线程私有主要是为了保证线程中的局部变量不被其它线程访问到。**

#### 一句话简单了解堆和方法区

堆是进程中最大的一块内存，主要用于存放新创建的对象 (几乎所有对象都在这里分配内存)，方法区主要用于存放已被加载的类信息、常量、静态变量等数据，**因此堆和方法区需要被线程共享，以访问共享资源。**

### 如何创建线程？

一般来说，创建线程有很多种方式，例如继承`Thread`类、实现`Runnable`接口、实现`Callable`接口、使用线程池、使用`CompletableFuture`类等等。

不过，这些方式其实并没有真正创建出线程。准确点来说，这些都属于是在 Java 代码中使用多线程的方法。

严格来说，Java 只有一种方式可以创建线程，那就是通过`new Thread().start()`方法创建。不管是上述哪种方式，最终还是依赖于`new Thread().start()`。

### ⭐️说说线程的生命周期和状态?

Java 线程在生命周期中的指定时刻只可能处于下面 6 种状态中的一种状态：

- NEW: 初始状态，线程被创建出来但没有被调用 `start()` 。
- RUNNABLE: 运行状态，线程被调用了 `start()`等待运行的状态。
- BLOCKED：阻塞状态，需要等待锁释放。
- WAITING：等待状态，表示该线程需要等待其他线程做出一些特定动作（通知或中断）。
- TIME_WAITING：超时等待状态，可以在指定的时间后结束等待状态，而不是像 WAITING 那样一直等待。
- TERMINATED：终止状态，表示该线程已经运行完毕。

线程在生命周期中并不是固定处于某一个状态，而是随着代码的执行在不同状态之间切换。

![Java 线程状态变迁图](https://oss.javaguide.cn/github/javaguide/java/concurrent/640.png)

由上图可以看出：线程创建之后它将处于 **NEW（新建）** 状态，调用` start()` 方法后处于 **READY（可运行）** 状态。可运行状态的线程获得了 CPU 时间片（timeslice）后处于**RUNNING（运行）** 状态。

- 当线程执行 **wait()方法**后，进入 **WAITING（等待）** 状态，需要依靠其他线程的通知才能返回到运行状态。
- **TIMED_WAITING（超时等待）**状态相当于在等待状态的基础上增加了**超时限制**，当超时时间结束后将会返回到 RUNNABLE 状态。
- 当线程进入 synchronized 方法/块 或者 调用 wait 后被 notify 重新进入 synchronized 方法/块，但是锁被其它线程占有，**当前线程获取锁失败**，此时线程就会进入 **BLOCKED（阻塞）** 状态。
- 线程在**执行完 run() 方法**后将会进入到 **TERMINATED（终止）** 状态

### 什么是线程上下文切换?

**线程上下文**指**线程在执行过程中的运行条件和状态**，**比如线程的程序计数器，栈信息等。**

当出现如下情况的时候，线程会从占用 CPU 状态中退出。

- 主动让出 CPU，比如调用了 sleep(), wait() 等。
- 时间片用完，防止一个线程或进程长时间占用 CPU 导致其他线程或进程饿死。
- 调用了阻塞类型的系统中断，比如请求 IO，线程被阻塞。
- 被终止或结束运行

前三种情况都会发生线程切换，线程切换意味着需要**保存当前线程的上下文**，留待线程下次占用 CPU 的时候恢复现场，并**加载下一个将要占用 CPU 线程的上下文**，这个过程就是**线程上下文切换**。

上下文切换是现代操作系统的基本功能，因其每次需要保存信息和恢复信息，这将会占用 CPU、内存等系统资源进行处理，也就意味着效率会有一定损耗，如果频繁切换线程上下文就会造成整体效率低下。

### Thread#sleep() 方法和 Object#wait() 方法对比

共同点：两者都可以暂停线程的执行。

区别：

- sleep() 方法没有释放锁，而 wait() 方法释放了锁 。
- wait() 通常用于线程间交互/通信，sleep()通常用于暂停执行。
- wait() 方法被调用后，线程不会自动苏醒，需要别的线程调用同一个对象上的 notify()或者 notifyAll() 方法。而sleep()方法执行完成后，线程会自动苏醒。也可以使用 wait(long timeout) ，超时后线程会自动苏醒。
- sleep() 是 Thread 类的静态本地方法，wait() 则是 Object 类的本地方法。

### 为什么 wait() 方法不定义在 Thread 中？

`wait() `方法是让获得当前对象锁的线程进入等待，并释放占有的对象锁。每个对象（Object）都拥有对象锁，既然要释放当前线程占有的对象锁并让其进入 WAITING 状态，自然是要操作对应的对象（Object）而非当前的线程（Thread）。

类似的问题：为什么 sleep() 方法定义在 Thread 中？因为 sleep() 是让当前线程暂停执行，不涉及到对象类，也不需要获得对象锁。

### 可以直接调用 Thread 类的 run 方法吗？

这是另一个非常经典的 Java 多线程面试问题，而且在面试中会经常被问到。很简单，但是很多人都会答不上来！

- new一个 t1 线程，该线程就进入了新建状态，调用 `t1.start()` 方法，会启动 t1 线程并使其进入就绪状态，当分配到时间片后就可以开始运行并执行 t1 线程中的 `run()` 方法，这是真正的多线程工作。
- 但直接执行 `t1.run()`方法不会启动 t1 线程，而是在当前线程中直接执行 t1 的 run() 方法中的代码，也就是把 run() 方法当成一个普通方法在当前线程中执行，这并不是多线程工作。

总结：调用 start() 方法可以启动线程并使其进入就绪状态，当就绪线程获得 CPU 时间片后就会自动执行 run() 方法。而直接执行 run() 方法的话不会以多线程的方式执行。



## 多线程

### 并发与并行的区别

并发：两个及两个以上的作业在**同一时间段**内执行。

并行：两个及两个以上的作业在**同一时刻**执行。

最关键的点是：是否是**同时**执行。

### 同步和异步的区别

同步：发出一个调用之后，在没有得到结果之前， 该调用不可以返回，需要一直等待。

异步：在发出调用之后，不需要等待结果返回，该调用可直接返回。

### ⭐️为什么要使用多线程?

先从总体上来说：

- 从计算机底层来说： 线程可以比作是轻量级的进程，是程序执行的最小单位，线程间进行切换和调度的成本远小于进程。另外，多核 CPU 时代意味着多个线程可以同时运行，减少了线程上下文切换的开销。
- 从当代互联网发展趋势来说：现在的系统动不动就要求百万级甚至千万级的并发量，而多线程并发编程正是开发高并发系统的基础，利用好多线程机制可以大大提高系统整体的并发能力以及性能。

再深入到计算机底层来探讨：

- 单核时代：在单核时代多线程主要是为了**提高单进程利用 CPU 和 IO 系统的效率**。 假设只运行了一个 Java 进程，当请求 IO 的时候，如果进程中只有一个线程，此线程被 IO 阻塞则整个进程被阻塞，CPU 和 IO 设备只有一个在运行，那么可以简单地说系统整体效率只有 50%。当使用多线程的时候，一个线程被 IO 阻塞，其他线程还可以继续使用 CPU。从而提高了 Java 进程利用系统资源的整体效率。
- 多核时代：多核时代多线程主要是为了**提高进程利用多核 CPU 的能力**。举个例子：假如我们要计算一个复杂的任务，我们只用一个线程的话，不论系统有几个 CPU 核心，都只会有一个 CPU 核心被利用到。而创建多个线程，这些线程可以被映射到底层多个 CPU 核心上执行，在任务中的多个线程没有资源竞争的情况下，任务执行的效率会有显著性的提高，执行时间可优化为`单核时执行时间/CPU 核心数`

### ⭐️单核 CPU 支持 Java 多线程吗？

单核 CPU 支持 Java 多线程，操作系统通过时间片轮转的方式，将 CPU 的时间分配给不同的线程。尽管单核 CPU 一次只能执行一个任务，但通过快速在多个线程之间切换，可以让用户感觉多个任务是同时进行的。

这里顺带提一下 **Java 使用的线程调度方式**。**操作系统主要通过两种线程调度方式来管理多线程的执行**：

- **抢占式调度（Preemptive Scheduling）**：操作系统决定何时暂停当前正在运行的线程，并切换到另一个线程执行。这种切换通常是由**系统时钟中断（时间片轮转）**或**其他高优先级事件**（如 I/O 操作完成）触发的。这种方式**存在上下文切换开销**，但**公平性和 CPU 资源利用率较好，不易阻塞**。
- **协同式调度（Cooperative Scheduling）**：线程执行完毕后，主动通知系统切换到另一个线程。这种方式可以**减少上下文切换带来的性能开销**，但**公平性较差，容易阻塞**。

**Java 使用的线程调度是抢占式的**。也就是说，JVM 本身不负责线程的调度，而是将线程的调度委托给操作系统。

**操作系统通常会基于线程优先级和时间片来调度线程的执行，高优先级的线程通常获得 CPU 时间片的机会更多。**

### ⭐️单核 CPU 上运行多个线程效率一定会高吗？

单核 CPU 同时运行多个线程的效率是否会高，取决于**线程的类型**和**任务的性质**。一般来说，**有两种类型的线程**： 

- CPU 密集型：主要进行计算和逻辑处理，需要占用大量的 CPU 资源。
- IO 密集型：主要进行输入输出操作，如读写文件、网络通信等，需要等待 IO 设备的响应，不占用太多的 CPU 资源。

在单核 CPU 上，同一时刻只能有一个线程在运行，其他线程则需要等待 CPU 的时间片分配。如果线程是 CPU 密集型的，那么运行多个线程会导致频繁的线程切换，增加了系统的开销，降低了效率。如果线程是 IO 密集型的，那么运行多个线程可以在等待 IO 时的空闲时间利用 CPU，提高了效率。

因此，**对于单核 CPU 来说，如果任务是 CPU 密集型的，那么开多个线程会影响效率；如果任务是 IO 密集型的，那么开多个线程会提高效率。**当然，这里的“很多”也要适度，不能超过系统能够承受的上限。

### 使用多线程可能带来什么问题?

多线程并发编程的目的是为了**提高程序的执行效率**进而**提高程序的运行速度**，但是多线程并发编程并不总是能够提高程序运行速度，而且可能会遇到**内存泄漏**、**死锁**、**线程不安全**等问题

### 如何理解线程安全和不安全？

线程安全和不安全是在多线程环境下，对于共享数据的访问是否能够保证其正确性和一致性的描述。

- 线程安全指的是在多线程环境下，对于共享数据，不管有多少个线程同时访问，都能保证这份数据的正确性和一致性。
- 线程不安全则表示在多线程环境下，对于共享数据，多个线程同时访问时可能导致数据混乱、错误或者丢失



## ⭐️死锁

### 什么是线程死锁？

线程死锁描述的是这样一种情况：多个线程同时被阻塞，它们中的一个或者全部线程都在等待某个资源被释放。由于线程被无限期地阻塞，因此程序不可能正常终止。

如下图所示，线程 A 持有资源 2，线程 B 持有资源 1，他们同时都想申请对方的资源，所以这两个线程就会互相等待而进入死锁状态。

![线程死锁示意图 ](https://oss.javaguide.cn/github/javaguide/java/2019-4%E6%AD%BB%E9%94%811.png)

产生死锁的四个必要条件，需要同时满足：

- 互斥条件：资源在任意一个时刻只能被一个线程占用。
- 请求与保持条件：一个线程因请求资源而阻塞时，对已获得的资源保持不放。
- 不剥夺条件：线程已获得的资源在未使用完之前不能被其他线程强行剥夺，只有自己使用完毕后才释放资源。
- 循环等待条件：若干线程之间形成一种头尾相接的循环等待资源关系。

破坏上述条件中的任意一个均可以打破死锁。

### 如何检测死锁？

- 使用`jmap`、`jstack`等命令查看 JVM 线程栈和堆内存的情况。如果有死锁，`jstack` 的输出中通常会有 `Found one Java-level deadlock:`的字样，后面会跟着死锁相关的线程信息。实际项目中还可以搭配使用`top`、`df`、`free`等命令查看操作系统的基本情况，出现死锁可能会导致 CPU、内存等资源消耗过高。
- 采用 `VisualVM`、`JConsole`等工具进行排查。

这里以 JConsole 工具为例进行演示。

- 首先要找到 JDK 的 bin 目录，找到 jconsole 并双击打开。
- 打开 jconsole 后，连接对应的程序，然后进入线程界面选择检测死锁即可！

### 如何预防和避免线程死锁?

#### 如何预防死锁？

破坏任意一个死锁的产生的必要条件即可预防死锁：

- 破坏互斥条件：尽量使用共享资源而不是互斥资源。例如，用只读文件代替需要互斥写的文件。但并不是所有的资源都能变成共享的（如打印机），因此破坏互斥条件在实际中可行性较低。
- 破坏请求与保持条件：线程一次性申请所需的所有资源。
- 破坏不剥夺条件：占用部分资源的线程进一步申请其他资源时，如果申请不到，可以主动释放它占有的资源。
- 破坏循环等待条件：按序申请资源，即**每个线程都按照相同的顺序申请资源**，释放资源则反序释放。

#### 如何避免死锁？

避免死锁就是在资源分配时，借助于算法（比如银行家算法）对资源分配进行计算评估，使其进入安全状态。

安全状态 指的是系统能够按照某种线程推进顺序（P1、P2、P3……Pn）来为每个线程都分配所需资源，使每个线程都可顺利完成。称 <P1、P2、P3.....Pn> 序列为安全序列。

### 死锁演示代码

两个线程循环等待的死锁演示：

```java
public class DeadlockDemo {
    
    // 定义两个资源（锁对象）
    private static final Object resource1 = new Object();
    private static final Object resource2 = new Object();
    
    public static void main(String[] args) {
        // 线程1：先拿resource1，再拿resource2
        Thread thread1 = new Thread(() -> {
            synchronized (resource1) {
                System.out.println("线程1：拿到了资源1，准备拿资源2...");
                
                // 让线程稍微睡眠一下，增加死锁发生的概率
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                synchronized (resource2) {
                    System.out.println("线程1：拿到了资源2！");
                }
            }
        });
        
        // 线程2：先拿resource2，再拿resource1
        Thread thread2 = new Thread(() -> {
            synchronized (resource2) {
                System.out.println("线程2：拿到了资源2，准备拿资源1...");
                
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                synchronized (resource1) {
                    System.out.println("线程2：拿到了资源1！");
                }
            }
        });
        
        // 启动两个线程
        thread1.start();
        thread2.start();
    }
}

//运行结果：
线程1：拿到了资源1，准备拿资源2...
线程2：拿到了资源2，准备拿资源1...
//两个线程循环等待对方的资源，卡住不动，发送了死锁。
```

避免上述死锁的最简单方法是**破坏循环等待条件，统一资源获取顺序**，即让线程2也先获取资源1，再获取资源2。

单线程不可重入锁的死锁演示：

```java
public class NonReentrantDeadlockDemo {
    
    private static NonReentrantLock lock = new NonReentrantLock();
    
    public static void main(String[] args) {
        
        // 创建一个线程执行任务
        Thread thread = new Thread(() -> {
            try {
                // 第一次获取锁 - 成功
                System.out.println("线程：第一次尝试获取锁...");
                lock.lock();
                System.out.println("线程：第一次获取锁成功！");
                
                // 在同一个线程中再次调用需要锁的方法
                nestedMethod();  // 这里会尝试再次获取同一个锁
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // 由于死锁，这行代码永远不会执行到
                lock.unlock();
                System.out.println("线程：释放锁");
            }
        });
        
        thread.start();
    }
    
    // 一个需要获取同一个锁的方法
    private static void nestedMethod() throws InterruptedException {
        System.out.println("进入嵌套方法，尝试再次获取同一个锁...");
        
        // 第二次尝试获取同一个锁 - 这里会导致死锁！
        lock.lock();  // 因为锁已被当前线程持有，且不可重入，所以会一直等待
        
        System.out.println("第二次获取锁成功！");  // 这行永远不会执行
        
        // 释放第二次获取的锁
        lock.unlock();
    }
}

//运行结果：
线程：第一次尝试获取锁...
线程：第一次获取锁成功！
进入嵌套方法，尝试再次获取同一个锁...
//进入嵌套方法后，由于锁不可重入，就卡住不动发生了死锁
```

## ⭐️JMM(Java 内存模型)

对于 Java 来说，你可以把 **JMM（Java 内存模型）** 看作是 Java 定义的并发编程相关的一组规范。除了抽象了线程和主内存之间的关系之外，还规定了从 Java 源代码到 CPU 可执行指令的转化过程要遵守哪些并发相关的原则和规范。其主要目的是为了简化多线程编程，增强程序的可移植性。**JMM 主要定义了对于一个共享变量，当一个线程执行写操作后，该变量对其他线程的可见性。**要想透彻理解 JMM，我们需要从 CPU 缓存模型和指令重排序说起。

### CPU 缓存模型

**为什么要弄一个 CPU 高速缓存？** 

类比开发网站后台系统使用的缓存（比如 Redis）是为了**解决程序处理速度**和**访问常规关系型数据库速度不对等的问题**。 CPU 缓存则是为了**解决 CPU 处理速度**和**内存处理速度不对等的问题**。

我们甚至可以把 内存看作外存的高速缓存，程序运行的时候我们把外存的数据复制到内存，由于内存的处理速度远远高于外存，这样提高了处理速度。

总结：CPU Cache 缓存的是内存数据，用于**解决CPU 处理速度**和**内存处理速度不匹配的问题**，内存缓存的是硬盘数据，用于解决硬盘访问速度过慢的问题。

为了更好地理解，一个简单的 CPU Cache 示意图如下所示。

![CPU 缓存模型示意图](https://oss.javaguide.cn/github/javaguide/java/concurrent/cpu-cache.png)

现代的 CPU Cache 通常分为三层，分别叫 L1, L2, L3 Cache。

**CPU Cache 的工作方式：** 先复制一份数据到 CPU Cache 中，当 CPU 需要用到该数据的时候就可以直接从 CPU Cache 中读取，当运算完成后，再将运算得到的数据写回 Main Memory 中。

但是这样存在内存缓存数据不一致的问题 ！比如两个线程执行 i++ 操作，如果同时从 CPU Cache 中读取 i=1，都做完 i++ 运算后都将 i=2 写回 Main Memory，而正确结果应该是 i=3。

CPU 为了解决内存缓存不一致性问题可以通过制定缓存一致协议（比如 MESI 协议）或者其他手段来解决。 这个缓存一致性协议指的是**在 CPU 高速缓存与主存交互的时候需要遵守的原则和规范**。不同的 CPU 中，使用的缓存一致性协议通常也会有所不同。

操作系统**通过内存模型（Memory Model） 定义一系列规范来解决内存缓存不一致性问题**。无论是 Windows 系统，还是 Linux 系统，它们都有特定的内存模型。



### 指令重排序

说完了 CPU 缓存模型，再来看另外一个比较重要的概念：**指令重排序** 。

为了提升执行速度/性能，计算机在执行程序代码的时候，会对指令进行重排序。

什么是指令重排序？ 简单来说就是系统在执行代码的时候并不一定是按照代码的编写顺序依次执行。

常见的指令重排序有下面 2 种情况：

- 编译器优化重排：编译器（包括 JVM、JIT等）在不改变单线程程序语义的前提下，重新安排语句的执行顺序。
- 指令并行重排：现代处理器采用了指令级并行技术(Instruction-Level Parallelism，ILP)来将多条指令重叠执行。如果不存在数据依赖性，处理器可以改变语句对应的机器指令的执行顺序。

另外，内存系统也会有“重排序”，但又不是真正意义上的重排序。在 JMM 里表现为主存和本地内存的内容可能不一致，进而导致程序在多线程下执行可能出现问题。

Java 源代码会经历 编译器优化重排 —> 指令并行重排 —> 内存系统重排 的过程，最终才变成操作系统可执行的指令序列。

指令重排序可以保证串行语义一致，但是没有义务保证多线程间的语义也一致 ，所以在多线程下，指令重排序可能会导致一些问题。

对于编译器优化重排和处理器的指令重排序（指令并行重排和内存系统重排都属于是处理器级别的指令重排序）有不同的处理方式。

- 对于编译器，通过禁止特定类型的编译器重排序的方式来禁止重排序。
- 对于处理器，通过插入内存屏障（Memory Barrier，或有时叫做内存栅栏，Memory Fence）的方式来禁止特定类型的处理器重排序。

内存屏障（Memory Barrier，或有时叫做内存栅栏，Memory Fence）是一种 CPU 指令，用来禁止处理器指令发生重排序，从而保障指令执行的有序性。另外，为了达到屏障的效果，它会**在处理器写入值后**，**强制将写缓冲区中的数据刷新到主内存**；**在处理器读取值之前，使处理器本地缓存中的相关数据失效，强制从主内存中加载最新值，从而保障变量的可见性。**

### JMM(Java Memory Model)

#### 什么是 JMM？为什么需要 JMM？

##### 什么是JMM？

可以把 **JMM（Java 内存模型）** 看作是 Java 定义的并发编程相关的一组规范。除了抽象了线程和主内存之间的关系之外，还规定了从 Java 源代码到 CPU 可执行指令的转化过程要遵守哪些并发相关的原则和规范。其主要目的是为了简化多线程编程，增强程序的可移植性。**JMM 主要定义了对于一个共享变量，当一个线程执行写操作后，该变量对其他线程的可见性。**

##### 为什么需要JMM？

并发编程下，像 CPU 多级缓存和指令重排序这类设计可能会导致程序运行出现一些问题，为此，JMM 抽象了 happens-before 原则来解决指令重排序问题。

JMM 说白了就是定义了一些规范来解决 CPU 多级缓存和指令重排序设计下出现的一些问题，开发者可以利用这些规范更方便地开发多线程程序。对于 Java 开发者来说，不需要了解底层原理，直接使用并发相关的一些关键字和类（比如 volatile、synchronized、各种 Lock）即可开发出并发、安全的程序。

#### JMM 如何抽象线程和主内存之间的关系？

Java 内存模型（JMM） 抽象了线程和主内存之间的关系，比如说线程之间的共享变量必须存储在主内存中。

在 JDK2 之前，Java 的内存模型实现总是从 主存 （即共享内存）读取变量。而在当前的 Java 内存模型下，线程可以把变量保存到 本地内存 （比如机器的寄存器）中，而不需要在主存中进行读写。这就可能造成一个线程在主存中修改了一个变量的值，而另外一个线程还在继续使用它在寄存器中的旧值，造成数据的不一致。

什么是主内存？什么是本地内存？

- 主内存：所有线程创建的**实例对象都存放在主内存中**，不管该实例对象是成员变量，还是局部变量，**类信息、常量、静态变量都是放在主内存中**。为了获取更好的运行速度，虚拟机及硬件系统可能会让工作内存优先存储于寄存器和高速缓存中。
- 本地内存：**每个线程都有一个私有的本地内存**，本地内存存储了该线程已读 / 写的共享变量的副本。每个线程只能操作自己本地内存中的变量，无法直接访问其他线程的本地内存。如果线程间需要通信，必须通过主内存来进行。本地内存是 JMM 抽象出来的一个概念，并不真实存在，它涵盖了缓存、写缓冲区、寄存器以及其他的硬件和编译器优化。

Java 内存模型的抽象示意图如下：

![JMM(Java 内存模型)](https://oss.javaguide.cn/github/javaguide/java/concurrent/jmm.png)

从上图来看，线程 1 与线程 2 之间如果要进行通信的话，必须要经历下面 2 个步骤：

- 线程 1 把本地内存中修改过的共享变量副本的值同步到主内存中去。
- 线程 2 到主存中读取对应的修改后的共享变量的值。

**也就是说，JMM 为共享变量提供了可见性的保障。**

不过，多线程下对主内存中的同一个共享变量进行操作有可能诱发线程安全问题。举个例子：

- 线程 1 和线程 2 分别对同一个共享变量进行操作，线程 1 执行修改，线程 2 执行读取。
- 线程 2 读取到的是线程 1 修改之前的值还是修改后的值并不确定，因为线程 1 和线程 2 都是先将共享变量从主内存拷贝到对应线程的工作内存中。

关于主内存与工作内存直接的具体交互协议，即一个变量如何从主内存拷贝到工作内存，如何从工作内存同步到主内存之间的实现细节，Java 内存模型定义了以下八种同步操作（了解即可）：

- **lock（锁定）**: 作用于主内存中的变量，将他**标记为一个线程独享变量**。
- **unlock（解锁）**: 作用于主内存中的变量，**解除变量的锁定状态**，被解除锁定的变量才能被其他线程锁定。
- **read（读取）**：作用于主内存的变量，把一个变量的值从主内存**传输到线程的工作内存中**。
- **load（载入）**：把 read 操作从主内存中得到的变量值**放入工作内存的变量的副本中**。
- **use（使用）**：把工作内存中的一个变量的值传给执行引擎，**每当虚拟机遇到一个使用变量的指令时都会执行该操作**。
- **assign（赋值）**：作用于工作内存的变量，它把一个从执行引擎接收到的值赋给工作内存的变量，**每当虚拟机遇到一个给变量赋值的字节码指令时都会执行该操作**。
- **store（存储）**：作用于工作内存的变量，它把工作内存中一个变量的值**传送到主内存中**。
- **write（写入）**：作用于主内存的变量，它把 store 操作从工作内存中得到的变量的值**放入主内存的变量中**。

除了这 8 种同步操作之外，还规定了下面这些同步规则来保证这些同步操作的正确执行（了解即可）：

- 不允许一个线程无原因地（没有发生过任何 assign 操作）把数据从线程的工作内存同步回主内存中。
- 一个新的变量只能在主内存中 “诞生”，不允许在工作内存中直接使用一个未被初始化（load 或 assign）的变量，换句话说就是**对一个变量实施 use 和 store 操作之前，必须先执行过了 assign 和 load 操作**。
- 一个变量在同一个时刻只允许一条线程对其进行 lock 操作，但 lock 操作可以被同一条线程重复执行多次，多次执行 lock 后，只有执行相同次数的 unlock 操作，变量才会被解锁。
- 如果对一个变量执行 lock 操作，将会清空工作内存中此变量的值，在执行引擎使用这个变量前，需要重新执行 load 或 assign 操作初始化变量的值。
- 如果一个变量事先没有被 lock 操作锁定，则不允许对它执行 unlock 操作，也不允许去 unlock 一个被其他线程锁定住的变量。
- ……

#### Java 内存区域和 JMM 有何区别？

这是一个比较常见的问题，很多初学者非常容易搞混。 Java 内存区域和Java内存模型是完全不一样的两个东西：

- Java 内存区域和 Java 虚拟机的运行时区域相关，定义了 JVM 在运行时如何分区存储程序数据，比如说堆主要用于存放对象实例。
- Java 内存模型和 Java 的并发编程相关，抽象了线程和主内存之间的关系，比如说线程之间的共享变量必须存储在主内存中，规定了从 Java 源代码到 CPU 可执行指令的这个转化过程要遵守哪些和并发相关的原则和规范，其主要目的是为了简化多线程编程，增强程序的可移植性。

#### happens-before 原则是什么？

JSR 133 引入了 happens-before 这个概念来描述两个操作之间的内存可见性。

为什么需要 happens-before 原则？ **happens-before 原则的诞生是为了程序员和编译器、处理器之间的平衡。**程序员追求的是易于理解和编程的强内存模型，遵守既定规则编码即可；而编译器和处理器追求的是较少约束的弱内存模型，让它们尽己所能地去优化性能，让性能最大化。**happens-before 原则的设计思想其实非常简单：**

- 为了对编译器和处理器的约束尽可能少，只要不改变程序执行结果，编译器和处理器怎么进行重排序优化都行
- 对于会改变程序执行结果的重排序，JMM 要求编译器和处理器必须禁止这种重排序。

**了解了 happens-before 原则的设计思想，再来看看 JSR-133 对 happens-before 原则的定义：**

- 如果操作1 happens-before 操作2，那么操作1的执行结果对操作2可见，并且操作1的执行顺序在操作2之前
- 两个操作之间存在 happens-before 关系，并不意味着必须要按照 happens-before 关系指定的顺序来执行。如果重排序之后的执行结果与按 happens-before 关系来执行的结果一致，那么 JMM 也允许这样的重排序。

比如下面这段代码：

```java
int userNum = getUserNum();   			// 1
int teacherNum = getTeacherNum();   	// 2
int totalNum = userNum + teacherNum;  	// 3
```

1 happens-before 2，2 happens-before 3，1 happens-before 3。虽然 1 happens-before 2，但对 1 和 2 进行重排序也不会影响代码的执行结果，所以 JMM 是允许编译器和处理器执行这种重排序的。但 1 和 2 必须是在 3 执行之前，也就是说 1,2 happens-before 3 。

**happens-before** 原则表达的意义其实并不是一个操作发生在另外一个操作的前面，它更想表达的意义是前一个操作的结果对于后一个操作是可见的，无论这两个操作是否在同一个线程里。

举个例子：操作 1 happens-before 操作 2，即使操作 1 和操作 2 不在同一个线程内，JMM 也会保证操作 1 的结果对操作 2 是可见的。

#### happens-before 常见规则有哪些？谈谈你的理解

happens-before 的规则就 8 条，说多不多，重点了解下面列举的 5 条即可。全记是不可能的，很快就忘记了，意义不大，随时查阅即可。并发编程语境下讨论 happens-before 时，通常指的是多个线程之间的关系。

- **程序顺序**规则：一个线程内，按照代码顺序，书写在前面的操作 happens-before 于书写在后面的操作；

- **解锁**规则：解锁 happens-before 于加锁；即线程 1 的解锁操作 happens-before 线程 2 的加锁操作；

- volatile 变量规则：对一个 volatile 变量的写操作 happens-before 于后面对这个 volatile 变量的读操作。

  即对 volatile 变量的写操作的结果对于发生于其后的**任何操作**都是可见的。

- 传递规则：如果 A happens-before B，且 B happens-before C，那么 A happens-before C；

- 线程启动规则：Thread 对象的 start()方法 happens-before 于此线程的每一个动作。

如果两个操作不满足上述任意一个 happens-before 规则，那么这两个操作就没有顺序的保障，JVM 可以对这两个操作进行重排序。

#### happens-before 和 JMM 是什么关系？

happens-before 与 JMM 的关系如下图所示：

- JMM 向程序员提供了 happens-before 规则 。这是一种 “ 强内存模型 ” 的假象：程序员不需要关心底层复杂的指令重排序细节，只需要按照 happens-before 规则编写代码，就能保证多线程下的内存可见性。
- JVM 在执行时，会将 happens-before 规则映射到具体的实现上。为了在保证正确性的前提下不丧失性能，JMM 只会 “ 禁止影响执行结果的指令重排序 ”。对于不影响单线程执行结果的重排序，JMM 是允许的。
- 最底层是编译器和处理器真实的 “ 指令重排序规则 ”。

总结来说，JMM 就像是一个中间层：它向上通过 happens-before 为程序员提供简单的编程模型；向下通过禁止特定重排序，充分利用底层硬件性能。这种设计既保证了多线程的安全性，又最大限度释放了硬件的性能

### 并发编程的三个重要特性

#### 原子性

一次操作或多次操作，要么全部执行并且不会受到任何干扰而中断，要么都不执行。

在 Java 中，可以借助 `synchronized`、各种` Lock` 以及**各种原子类**实现原子性。

`synchronized` 和各种` Lock` 可以保证任一时刻只有一个线程访问该代码块，因此可以保障原子性。各种原子类是利用 **CAS (compare and swap) 操作**（可能也会用到 volatile或者final关键字）来保证原子性。

#### 可见性

如果一个线程对**共享变量**进行了修改，那么其它的线程都应该立即看到修改后的最新值。

在 Java 中，可以借助`synchronized`、各种 `Lock`和 `volatile` 实现可见性。

如果将变量声明为 `volatile` ，就指示 JVM这个变量是共享且不稳定的，每次使用它都要到主存中去进行读取。

#### 有序性

由于指令重排序问题，代码的执行顺序未必就是编写代码的顺序。

指令重排序可以保证串行语义一致，即在串行执行时只有在不改变最终结果时才会进行指令重排序优化，但是没有义务保证多线程间的语义也一致 ，所以在多线程下，指令重排序可能会导致一些问题。

在 Java 中，`volatile` 关键字可以禁止指令进行重排序优化。

### JMM 总结

- Java 是最早尝试提供内存模型的语言，其主要目的是为了**简化多线程编程**，**增强程序的可移植性**。
- CPU 可以通过制定缓存一致协议（比如 MESI 协议）来**解决内存缓存不一致性问题**。
- 为了提升执行速度/性能，计算机在执行程序代码的时候，会对指令进行重排序。 即系统在执行代码的时候并不一定是按照代码的编写顺序依次执行。**指令重排序可以保证串行语义一致**，但是**没有义务保证多线程间的语义一致** ，所以在多线程下，指令重排序可能会导致一些问题。
- 可以把 JMM 看作是 Java 定义的**与并发编程相关的一组规范**，除了**抽象了线程和主内存之间的关系**之外，其还**规定了从 Java 源代码到 CPU 可执行指令的这个转化过程要遵守哪些和并发相关的原则和规范**，如 `happens-before` 规则，其主要目的是为了简化多线程编程，增强程序的可移植性。
- JSR 133 引入了 happens-before 这个概念来描述两个操作之间的内存可见性。

## ⭐️volatile 关键字

### 如何保证变量的可见性？

在 Java 中，`volatile` 关键字可以保证变量的可见性，如果我们将变量声明为 `volatile` ，这就指示 JVM，这个变量是**共享且不稳定的**，每次使用它都到主存中进行读取。

volatile 关键字其实并非是 Java 语言特有的，在 C 语言里也有，它**最原始的意义就是禁用 CPU 缓存**。

`volatile` 关键字能保证数据的可见性，但不能保证数据的原子性。`synchronized` 关键字两者都能保证。

### 如何禁用指令重排序？

在 Java 中，`volatile` 关键字除了可以保证变量的**可见性**，还可以**防止 JVM 的指令重排序**。 

如果将变量声明为 `volatile` ，那么对其进行读写操作时，会通过插入特定的内存屏障来禁止指令重排序。

**面试中**面试官经常会说：单例模式了解吗？给我手写一下！给我解释一下双重检验锁方式实现单例模式的原理呗！

双重校验锁实现对象单例（线程安全）：

```java
public class Singleton{
    private volatile static Singleton instance;		//静态实例变量私有化
    private Singleton(){							//构造方法私有化
    }
    public static Singleton getInstance(){			//提供公共的获取实例方法
        if(instance == null){
            synchronized(Singleton.class){
                if(instance == null){
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
//第一重if(instance == null)校验是为了在实例被创建后直接返回实例对象，而不需要进入synchronized代码块
//第二重if(instance == null)校验是为了防止在并发场景下多个线程同时通过第一重校验，从而创建多个实例对象
```

`instance` 采用 `volatile` 关键字修饰来禁止指令重排序，这是因为 `instance = new Singleton();` 这段代码其实是分三步执行：

- 1、为`instance`对象分配内存空间
- 2、初始化`instance`对象（执行构造函数）
- 3、将`instance`指向分配的内存地址（此时instance 就 != null了，且初始化完毕）

但是由于 JVM 具有指令重排序的特性，执行顺序有可能变成 1->3->2，即

- 1、为`instance`对象分配内存空间
- 2、将`instance`指向分配的内存地址（此时instance 就 != null了，但还没有初始化完成）
- 3、初始化`instance`对象（执行构造函数）

这个指令重排在**单线程下不会出现问题**，但是在**多线程**下会**导致一个线程获得另一个线程还没有初始化的实例**。

例如，指令重排序后，线程 1 还没完成第3步初始化，此时线程 2 调用 `getInstance()` 方法并发现` instance` 不为 null，因为已经将其指向了分配的内存地址，因此线程 2 会返回一个还没有初始化的 `instance`

使用 `volatile` 修饰 instance 后，`volatile` 会插入内存屏障(Memory Barrier)来禁止特定类型的指令重排序：

比如在将 `instance `指向分配的内存地址前，会插入写屏障(Store Barrier)，以确保在这个写操作之前的所有普通写操作（包括对象初始化）都完成，并且这些写入对其它线程可见。此外还有读屏障(Load Barrier)，以确保在 `volatile` 变量读操作之后的所有读操作，都能看到 `volatile` 写操作之前的所有写入。

### volatile可以保证原子性吗？

`volatile` 关键字能保证变量的可见性，但不能保证对变量的操作是原子性的。

通过下面的代码即可证明：

```java
public class VolatileAtomicityDemo {
    public volatile static int inc = 0;

    public void increase() {
        inc++;
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(5);	//线程池
        VolatileAtomicityDemo volatileAtomicityDemo = new VolatileAtomicityDemo();
        for (int i = 0; i < 5; i++) {
            threadPool.execute(() -> {
                for (int j = 0; j < 500; j++) {		//2500次inc++操作分配给5个线程执行
                    volatileAtomicityDemo.increase();
                }
            });
        }
        // 等待1.5秒，保证上面程序执行完成
        Thread.sleep(1500);
        System.out.println(inc);
        threadPool.shutdown();
    }
}
```

正常情况下，运行上面的代码理应输出 2500，但真正运行后会发现**每次输出结果都小于 2500**。

为什么会出现这种情况呢？不是说volatile 可以保证变量的可见性吗，也就是说每个线程对 inc 变量自增完之后其他线程应该可以立即看到修改后的值。5 个线程分别进行了 500 次操作，那么最终 inc 的值应该是 5*500=2500。

很多人会误认为自增操作 inc++ 是原子性的，实际上，inc++ 是一个复合操作，包括三步：

- 读取 inc 的值。
- 对 inc 加 1。
- 将 inc 的值写回内存。

volatile 无法保证这三个操作具有原子性，有可能出现下面这种情况：两个线程同时读取到相同的 inc 值，都对其进行+1的操作，然后都将结果写回内存，这就导致有两个线程都对 inc 进行了一次自增操作，但 inc 只增加了 1。

其实想要保证上面的代码运行正确也非常简单，利用 `synchronized`、`Lock`或者`AtomicInteger`都可以。

使用`synchronized`改进：

```java
public synchronized void increase(){
    inc++;
}
```

使用 `AtomicInteger` 改进：

```java
public AtomicInteger inc = new AtomicInteger();
public void increase(){
    inc.getAndIncrement();	//CAS保证原子性
}
```

使用`ReentrantLock`改进

```java
ReentrantLock lock = new ReentrantLock();
public void increase(){
    lock.lock();
    try{
        inc++;
    }finally{
        lock.unlock();
    }
}
```

## ⭐️乐观锁和悲观锁

### 什么是悲观锁？

悲观锁总是假设最坏的情况，认为每次访问**共享资源**都会出现线程安全问题，所以在每次获取资源的时候都会上锁，这样其他线程想拿到这个资源就会被阻塞，直到锁被上一个持有者释放。

也就是说，**共享资源每次只给一个线程使用**，其它线程阻塞，用完后再把资源转让给其它线程。

像 Java 中`synchronized`和`ReentrantLock`等**独占锁**就是悲观锁思想的实现。

```java
public void performSynchronisedTask() {
    synchronized (this) {
        // 需要同步的操作
    }
}

private Lock lock = new ReentrantLock();
lock.lock();
try {
   // 需要同步的操作
} finally {
    lock.unlock();
}
```

高并发场景下，激烈的锁竞争会造成线程阻塞，大量阻塞线程会导致系统的上下文切换，增加系统的性能开销。并且悲观锁还可能会存在死锁问题，影响代码的正常运行。

### 什么是乐观锁？

乐观锁总是假设最好的情况，认为每次访问**共享资源**都不会出现线程安全问题，因此不加锁，只是在提交对共享资源的修改时去检查其是否被其它线程修改了（可以使用版本号或CAS算法）。

在 Java 中，`java.util.concurrent.atomic`包下面的原子类（比如`AtomicInteger`、`LongAdder`）都是使用了乐观锁的 CAS 实现。

```java
// LongAdder 在高并发场景下会比 AtomicInteger 和 AtomicLong 的性能更好
// 代价就是会消耗更多的内存空间（空间换时间）
LongAdder sum = new LongAdder();
sum.increment();	//使用 CAS 保证原子性
```

高并发场景下，乐观锁相比悲观锁来说，不存在锁竞争造成线程阻塞，也不会有死锁的问题，在性能上往往更好。但是如果冲突频繁发生（比如写操作占比非常多的情况时），会频繁失败和重试，同样会影响性能。

不过，大量失败重试的问题也是可以解决的，比如 `LongAdder`就以空间换时间的方式解决了这个问题。

理论上来说：

- 悲观锁通常多用于写操作比较多的情况（多写场景，竞争激烈），这样可以避免频繁失败和重试影响性能。悲观锁的开销是固定的，不过如果乐观锁解决了频繁失败和重试这个问题的话（比如`LongAdder`），也是可以考虑使用乐观锁的，要视实际情况而定。
- 乐观锁通常多用于写操作比较少的情况（多读场景，竞争较少），这样可以避免频繁加锁影响性能。不过，乐观锁主要针对的对象是单个共享变量（参考`java.util.concurrent.atomic`包下面的原子变量类）。

### 如何实现乐观锁？

乐观锁一般使用**版本号**或 **CAS** 实现，CAS 相对来说更多一些。

#### 版本号

一般是在数据表中加上一个版本号 version 字段，表示数据被修改的次数，当数据被修改时，version 值会加1。

当线程 A 要更新数据时，在读取数据的同时也会读取 version 值，在提交更新时，判断当前 version 值是否与刚才读取到的 version 值相等，如果相等才更新，否则重试直到更新成功。

#### CAS

CAS 的全称是 **Compare And Swap**（比较与交换） ，用于实现乐观锁，被广泛应用于各大框架中。

CAS 的思想很简单，就是**用一个预期值和要更新的变量值进行比较**，两者相等才会更新，CAS 是一个原子操作。

CAS 涉及到三个操作数：

- V：要更新的变量值(Var)
- E：预期值(Expected)
- N：拟写入的新值(New)

当且仅当 V 等于 E 时，CAS 才会用 N 来更新 V 的值。否则说明已经有其它线程更新了 V，则当前线程放弃更新。

当多个线程同时使用 CAS 操作一个变量时，只有一个会胜出，并成功更新，其余均会失败，但失败的线程并不会被挂起，仅是被告知失败，并且允许再次尝试，当然也允许失败的线程放弃操作。

Java 语言并没有直接实现 CAS，CAS 是通过 C++ 内联汇编的形式实现的（JNI 调用）。因此 CAS 的具体实现和操作系统以及 CPU 都有关系。

`sun.misc`包下的`Unsafe`类提供了`compareAndSwapObject`、`compareAndSwapInt`、`compareAndSwapLong`方法来实现的对`Object`、`int`、`long`类型的 CAS 操作

### Java 中 CAS 是如何实现的？

在 Java 中，实现 CAS 操作的一个关键类是`Unsafe`。

`Unsafe`类位于`sun.misc`包下，是一个提供低级别、不安全操作的类。由于其强大的功能和潜在的危险性，它通常用于 JVM 内部或一些需要极高性能和底层访问的库中，而不推荐普通开发者在应用程序中使用。

关于 Unsafe类的详细介绍，可以阅读这篇文章：📌Java 魔法类 Unsafe 详解。

`sun.misc`包下的`Unsafe`类提供了`compareAndSwapObject`、`compareAndSwapInt`、`compareAndSwapLong`方法来实现的对`Object`、`int`、`long`类型的 CAS 操作：

```java
/**
 * 以原子方式更新对象字段的值。
 *
 * @param o        要操作的对象
 * @param offset   对象字段的内存偏移量，比如AtomicInteger对象在内存中对应的int值
 * @param expected 期望的旧值
 * @param x        要设置的新值
 * @return 如果值被成功更新，则返回 true；否则返回 false
 */
boolean compareAndSwapObject(Object o, long offset, Object expected, Object x);

/**
 * 以原子方式更新 int 类型的对象字段的值。
 */
boolean compareAndSwapInt(Object o, long offset, int expected, int x);

/**
 * 以原子方式更新 long 类型的对象字段的值。
 */
boolean compareAndSwapLong(Object o, long offset, long expected, long x);
```

`Unsafe`类中的 CAS 方法是`native`方法。`native`关键字表明这些方法是用本地代码（通常是 C 或 C++）实现的，而不是用 Java 实现的。这些方法直接调用底层的硬件指令来实现原子操作。也就是说，Java 语言并没有直接用 Java 实现 CAS，而是通过 C++ 内联汇编的形式实现的（通过 JNI 调用）。因此，CAS 的具体实现与操作系统以及 CPU 密切相关。

`java.util.concurrent.atomic` 包提供了一些用于原子操作的类。这些类利用底层的原子指令，确保在多线程环境下的操作是线程安全的。关于这些 Atomic 原子类的介绍和使用，可以阅读这篇文章：[Atomic 原子类总结](https://javaguide.cn/java/concurrent/atomic-classes.html)。

AtomicInteger是 Java 的原子类之一，主要用于对 int 类型的变量进行原子操作，它利用Unsafe类提供的低级别原子操作方法实现**无锁的线程安全性**。

下面通过解读`AtomicInteger`的核心源码（JDK8）来说明 Java 如何使用`Unsafe`类的方法来实现原子操作：

```java
// 获取 Unsafe 实例
private static final Unsafe unsafe = Unsafe.getUnsafe();
private static final long valueOffset;//表示AtomicInteger对象在内存偏移量valueOffset处的int值

static {
    try {
        // 获取“value”字段在AtomicInteger类中的内存偏移量
        valueOffset = unsafe.objectFieldOffset
            (AtomicInteger.class.getDeclaredField("value"));
    } catch (Exception ex) { throw new Error(ex); }
}
// 确保“value”字段的可见性
private volatile int value;

// 如果当前值等于预期值，则原子地将值设置为newValue
// 使用 Unsafe#compareAndSwapInt 方法进行CAS操作
public final boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}

// 原子地将当前值加 delta 并返回旧值
public final int getAndAdd(int delta) {
    return unsafe.getAndAddInt(this, valueOffset, delta);
}

// 原子地将当前值加 1 并返回加之前的值（旧值）
// 使用 Unsafe#getAndAddInt 方法进行CAS操作。
public final int getAndIncrement() {
    return unsafe.getAndAddInt(this, valueOffset, 1);
}

// 原子地将当前值减 1 并返回减之前的值（旧值）
public final int getAndDecrement() {
    return unsafe.getAndAddInt(this, valueOffset, -1);
}
```

`Unsafe#getAndAddInt`源码：

```java
// 原子地获取并增加整数值
public final int getAndAddInt(Object o, long offset, int delta) {
    int v;
    do {
        // 以 volatile 方式获取对象 o 在内存偏移量 offset 处的整数值
        v = getIntVolatile(o, offset);
    } while (!compareAndSwapInt(o, offset, v, v + delta));
    // 返回旧值
    return v;
}
```

可以看到，`getAndAddInt` 使用了 `do-while` 循环：在`compareAndSwapInt`操作失败时，会不断重试直到成功。也就是说，`getAndAddInt`方法会通过 `compareAndSwapInt` 方法来尝试更新 `value` 的值，如果更新失败（即当前值在此期间被其他线程修改），它会重新获取当前值并再次尝试更新，直到操作成功。

由于 CAS 操作可能会因为并发冲突而失败，因此通常会与`while`循环搭配使用，在失败后不断重试，直到操作成功。这就是 **自旋锁机制** 。

### CAS 存在哪些问题？

#### ABA 问题

如果一个变量 V 初次读取的时候是 A 值，并且在准备赋值的时候检查到它仍然是 A 值，那我们就能说明它的值没有被其他线程修改过了吗？很明显是不能的，因为在这段时间它可能被改为其他值，然后又改回 A，那 CAS 操作就会误认为它从来没有被修改过。这个问题被称为 CAS 操作的 "ABA"问题。

ABA 问题的解决思路是在变量前面追加上版本号或者时间戳。JDK5 以后的 `AtomicStampedReference` 类就是用来解决 ABA 问题的，其中的 `compareAndSet()` 方法就是首先检查当前引用是否等于预期引用，并且当前标志是否等于预期标志，如果全部相等，则以原子方式将该引用和该标志的值设置为给定的更新值。

#### 循环时间长开销大

CAS 经常会用到自旋操作来进行重试，如果长时间不成功，会给 CPU 带来非常大的执行开销。

如果 JVM 能够支持处理器提供的pause指令，那么自旋操作的效率将有所提升。pause指令有两个重要作用：

- 延迟流水线执行指令：pause指令可以延迟指令的执行，从而减少 CPU 的资源消耗。具体的延迟时间取决于处理器的实现版本，在某些处理器上延迟时间可能为零。
- 避免内存顺序冲突：在退出循环时，pause指令可以避免由于内存顺序冲突而导致的 CPU 流水线被清空，从而提高 CPU 的执行效率。

#### 只能保证一个共享变量的原子操作

CAS 操作仅能对单个共享变量有效。当需要操作多个共享变量时，CAS 就显得无能为力。

不过从 JDK5 开始，Java 提供了`AtomicReference`类，这使得我们能够保证**引用对象**之间的原子性。因此可以将多个变量封装在一个对象中，然后使用`AtomicReference`来执行 CAS 操作。

除了 `AtomicReference `这种方式之外，还可以利用加锁来保证。

### 总结

- 悲观锁基于悲观的假设，认为共享资源在每次访问时都会发生冲突，因此在每次操作时都会加锁。这种锁机制会导致其他线程阻塞，直到锁被释放。Java 中的 `synchronized` 和 `ReentrantLock` 是悲观锁的典型实现方式。虽然悲观锁能有效避免数据竞争，但是在高并发场景下会导致线程阻塞、上下文切换频繁，从而影响系统性能，并且还可能引发死锁问题。
- 乐观锁基于乐观的假设，认为共享资源在每次访问时都不会发生冲突，因此无须加锁，只需在提交对共享数据的修改时验证数据是否被其他线程修改即可。Java 中的 `AtomicInteger` 和 `LongAdder` 等原子类通过 CAS（Compare-And-Swap）算法实现了乐观锁。乐观锁避免了线程阻塞和死锁问题，在读多写少的场景中性能优越。但在写操作频繁的情况下，可能会导致大量的失败重试，影响性能。
- 乐观锁主要通过版本号机制或 CAS 算法实现。版本号机制通过比较版本号确保数据一致性，而 CAS 通过 `Unsafe` 类中的 `native` 方法实现，这些方法调用底层的硬件指令来完成原子操作。由于 CAS 的实现依赖于 C++ 内联汇编和 JNI 调用，因此 CAS 的具体实现与操作系统以及 CPU 密切相关。CAS 虽然具有高效的无锁特性，但也需要注意 ABA 、循环时间长开销大等问题。

悲观锁和乐观锁各有优缺点，适用于不同的应用场景。在实际开发中，选择合适的锁机制能够有效提升系统的并发性能和稳定性。



## synchronized 关键字

### synchronized 是什么？有什么用？

synchronized 是 Java 中的一个关键字，翻译成中文是同步的意思，主要解决的是多个线程之间访问资源的同步性，可以保证被它修饰的方法或者代码块在任意时刻只能有一个线程执行。

在 Java 早期版本中，synchronized 属于重量级锁，效率低下。在 Java 6 之后， synchronized 引入了大量的优化如**自旋锁**、**适应性自旋锁**、**锁消除**、**锁粗化**、**偏向锁**、**轻量级锁**等技术来减少锁操作的开销，这些优化让 synchronized 锁的效率提升了很多。因此， synchronized 还是可以在实际项目中使用的，像 JDK 源码、很多开源框架都大量使用了 synchronized 。

**关于偏向锁多补充一点：**由于偏向锁增加了 JVM 的复杂性，同时也并没有为所有应用都带来性能提升。因此在 JDK15 中，偏向锁被默认关闭（仍然可以使用 -XX:+UseBiasedLocking 启用偏向锁），在 JDK18 中，偏向锁已经被彻底废弃（无法通过命令行打开）。

### 如何使用 synchronized？

synchronized 关键字的使用方式主要有下面 3 种：

- 修饰实例方法
- 修饰静态方法
- 修饰代码块

1、修饰实例方法 （锁调用该实例方法的对象实例）

给调用该实例方法的对象实例加锁，进入该同步代码前要获得当前对象实例锁 。

```java
synchronized void method() {
    //业务代码
}
```

2、修饰静态方法 （锁该静态方法所属的类）

```java
synchronized static void method() {
    //业务代码
}
```

3、修饰代码块 （根据普通代码块和静态代码块分别锁指定对象/类）

对括号里指定的对象/类加锁：

`synchronized(object)` 表示进入同步代码块前要获得指定对象的锁。

`synchronized(类.class)` 表示进入同步代码块前要获得指定类的锁。

```java
synchronized(this) {
    //业务代码
}
```

总结：

- `synchronized `关键字加到 static 静态方法和 synchronized(class) 代码块上都是是给 Class 类上锁；
- synchronized 关键字加到实例方法上是给对象实例上锁；
- 尽量不要使用 synchronized(String a) 因为 JVM 中，字符串常量池具有缓存功能。

### 构造方法可以用 synchronized 修饰么？

构造方法不能使用 synchronized 关键字修饰。不过可以在构造方法内部使用 synchronized 修饰代码块。

另外，**构造方法本身是线程安全的**，但如果在构造方法中涉及到共享资源的操作，就需要采取适当的同步措施来**保证整个构造过程的线程安全**。

### ⭐️synchronized 底层原理

synchronized 的底层原理属于 JVM 层面的东西。

- `synchronized` 修饰同步代码块使用的是 `monitorenter` 和 `monitorexit` 指令，其中 `monitorenter` 指令指向同步代码块的开始位置，`monitorexit` 指令指向同步代码块的结束位置。
  - 在执行`monitorenter`指令时会尝试获取对象的锁，如果锁的计数器为 0 则表示可以获取锁，获取后将锁计数器加 1。如果获取对象锁失败则当前线程进入阻塞等待，直到锁被另外一个线程释放为止。
  - 持有对象锁的线程才可以执行 `monitorexit `指令来释放锁。执行` monitorexit `指令后，将锁计数器 -1，当计数器为 0 时表明锁被释放，其他线程可以尝试获取锁，否则表示当前是重入锁，会继续尝试释放锁。
- `synchronized` 修饰方法并没有 `monitorenter` 指令和 `monitorexit` 指令，取而代之的是 `ACC_SYNCHRONIZED` 标识 (Access Flag)，该标识表明该方法是一个同步方法。
  - 如果是实例方法，JVM 会尝试获取实例对象的锁。
  - 如果是静态方法，JVM 会尝试获取当前类的锁。
- 两者的本质都是对对象监视器 monitor 的获取。

`synchronized` 锁的是对象（或者类对象），锁的信息主要存储在 **对象头** 中。对象头中包含一块 **Mark Word** 数据，其中包含 **锁状态标志：**无锁状态、偏向锁、轻量级锁、重量级锁

当锁升级为重量级锁时，对象头会指向堆内存中的一个 `ObjectMonitor` 对象，核心结构如下：

- **_owner：** 指向持有锁的线程。
- **_WaitSet：** 存放调用 `wait()` 方法后被阻塞的线程。
- **_EntryList：** 存放等待获取锁的线程（阻塞队列）。
- **_Count：** 记录等待线程的数量。
- **_recursions：** 锁的重入次数（支持可重入）。

**偏向锁 (Biased Locking)：**只有一个线程获取锁，不存在竞争。原理：通过在 Mark Word 中记录当前线程的 ID，后续当该线程再次进入同步块时，只需判断 ID 是否为自己，如果是直接执行。

如果有另一个线程尝试获取锁，偏向锁会撤销，升级为轻量级锁。

**轻量级锁 (Lightweight Locking)：**存在轻微竞争，但线程交替执行，没有激烈的资源争抢。
原理：获取锁失败时通过 **CAS** 配合**自旋**重新尝试获取锁。

如果自旋超过一定次数（默认 10 次）仍未获取到锁，升级为重量级锁。

**重量级锁 (Heavyweight Locking)：** 激烈竞争，自旋失败。未获取到锁的线程进入 `_EntryList` 阻塞等待。

### JDK6 之后的 synchronized 底层做了哪些优化？锁升级原理了解吗？

在 Java 6 之后， synchronized 引入了大量的优化如**自旋锁、适应性自旋锁、锁消除、锁粗化、偏向锁、轻量级锁**等技术来减少锁操作的开销，这些优化让 synchronized 锁的效率提升了很多（JDK18中偏向锁已经被彻底废弃)。

锁主要存在四种状态，依次是**无锁状态**、**偏向锁状态**、**轻量级锁状态**、**重量级锁状态**。锁状态会随着锁竞争的激烈而逐渐升级。但注意锁可以升级不可以降级，这种策略是为了提高获得锁和释放锁的效率。

synchronized 锁升级是一个比较复杂的过程，**面试也很少问到**，如果想要详细了解的话可以看看这篇文章：浅析 synchronized 锁升级的原理与实现。

### JDK18中，synchronized 的偏向锁为什么被废弃了？

在 JDK15 中，偏向锁被默认关闭（仍然可以使用 -XX:+UseBiasedLocking 启用偏向锁），在 JDK18 中，偏向锁已经被彻底废弃（无法通过命令行打开）。

在官方声明中，主要原因有两个方面：

- 性能收益不明显：

  偏向锁是 HotSpot 虚拟机的一项优化技术，可以提升单线程对同步代码块的访问性能。

  受益于偏向锁的应用程序通常使用了早期的 Java 集合 API，例如 HashTable、Vector，在这些集合类中通过 synchronized 来控制同步，这样在单线程频繁访问时，通过偏向锁会减少同步开销。

  随着 JDK 的发展，出现了 ConcurrentHashMap 高性能的集合类，在集合类内部进行了许多性能优化，此时偏向锁带来的性能收益就不明显了。

  **偏向锁仅仅在单线程访问同步代码块的场景中可以获得性能收益。**

  **一旦出现多线程竞争，就需要撤销偏向锁** ，这个操作的性能开销是比较昂贵的。偏向锁的撤销需要等待进入到全局安全点（safe point），该状态下所有线程都是暂停的，此时去检查线程状态并进行偏向锁的撤销。

- JVM 内部代码维护成本太高：偏向锁将许多复杂代码引入到同步子系统，并且对其他的 HotSpot 组件也具有侵入性。这种复杂性为理解代码、系统重构带来了困难，因此， OpenJDK 官方希望禁用、废弃并删除偏向锁

### ⭐️synchronized 和 volatile 有什么区别？

synchronized 关键字和 volatile 关键字是两个互补的存在，而不是对立的存在！

- volatile 关键字是线程同步的轻量级实现，性能要比 synchronized 好 。
- volatile 关键字只能用于修饰变量而 synchronized 关键字可以修饰方法(静态方法和实例方法)以及代码块 。
- volatile 关键字只能保证变量的可见性，但不能保证变量的原子性。synchronized 关键字两者都能保证。
- volatile关键字主要用于解决共享资源在多个线程之间的**可见性**，而 synchronized 关键字解决的是多个线程之间访问共享资源的**同步性**。

## ReentrantLock

### ReentrantLock 是什么？

ReentrantLock 实现了 Lock 接口，是一个可重入且独占式的锁，和 synchronized 关键字类似。

不过，ReentrantLock 更灵活、更强大，增加了**轮询、超时、中断、公平锁和非公平锁**等高级功能。

```java
public class ReentrantLock implements Lock, java.io.Serializable {}

```

ReentrantLock 里面有一个内部类 Sync，Sync 继承自 AQS（AbstractQueuedSynchronizer），添加锁和释放锁的大部分操作实际上都是在 Sync 中实现的。Sync 有公平锁 FairSync 和非公平锁 NonfairSync 两个子类。

![img](https://oss.javaguide.cn/github/javaguide/java/concurrent/reentrantlock-class-diagram.png)

`ReentrantLock` **默认使用非公平锁**，也可以通过构造器来显式的指定使用公平锁。

```java
// 传入一个 boolean 值，true 时为公平锁，false 时为非公平锁
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

从上面的内容可以看出， `ReentrantLock` 的底层就是由 AQS 来实现的。

关于 AQS 的相关内容推荐阅读AQS详解这篇文章。

### 公平锁和非公平锁有什么区别？

- 公平锁 : 锁被释放之后，先申请的线程先得到锁。性能较差一些，因为公平锁为了保证时间上的绝对顺序，上下文切换更频繁。
- 非公平锁：锁被释放之后，后申请的线程可能会先获取到锁，**是随机或者按照其他优先级排序的**。性能更好，但可能会导致某些线程永远无法获取到锁。

### ⭐️synchronized 和 ReentrantLock 有什么区别？

**两者都是可重入锁**

可重入锁也叫递归锁，指的是线程可以再次获取自己的内部锁。比如一个线程获得了某个对象的锁，此时这个对象锁还没有释放，当该线程想要再次获取这个对象的锁的时候还是可以获取的，如果是不可重入锁就会造成死锁。

JDK 提供的所有现成的 Lock 实现类，包括 synchronized 关键字锁都是可重入的。

双重校验锁的单例模式实现中也可以看出 synchronized 是可重入锁。

**synchronized 依赖于 JVM 而 ReentrantLock 依赖于 API**

`synchronized` 是依赖于 JVM 实现的，JDK6 为 synchronized 进行了很多优化，但这些优化都是在 JVM 层面实现的，并没有直接暴露给我们。

ReentrantLock 是 JDK 实现的，也就是 API 层面，需要 lock() 和 unlock() 方法配合 try/finally 语句块来完成，可以通过查看源码来看它是如何实现的。

**ReentrantLock 比 synchronized 增加了一些高级功能**

- **等待可中断 :** ReentrantLock 提供了一种**能够中断等待锁的线程**的机制，通过 lock.lockInterruptibly() 实现。当前线程在等待获取锁的过程中，可以被其它线程通过 interrupt() 中断，当前线程会抛出 `InterruptedException` 异常，可以捕捉该异常进行相应处理。
- **可实现公平锁 :** ReentrantLock可以指定是公平锁还是非公平锁。而**synchronized只能是非公平锁**。所谓的公平锁就是先等待的线程先获得锁。**ReentrantLock默认情况是非公平的**，可以通过 ReentrantLock类的ReentrantLock(boolean fair)构造方法来指定是否是公平的。
- **通知机制更强大：**ReentrantLock 通过绑定多个 Condition 对象，可以实现分组唤醒和选择性通知。这解决了 synchronized 只能随机唤醒或全部唤醒的效率问题，为复杂的线程协作场景提供了强大的支持。
- **支持超时 ：**ReentrantLock 提供了 tryLock(timeout) 的方法，可以指定等待获取锁的最长等待时间，如果超过了等待时间，就会获取锁失败，不会一直等待。

即 synchronized 是不可中断的、非公平的、没有condition通知机制、不支持超时获取锁。

**关于 Condition接口的补充：**Condition是 JDK5 之后才有的，它具有很好的灵活性，比如可以实现多路通知功能，也就是在一个Lock对象中可以创建多个Condition实例（即对象监视器），线程对象可以注册在指定的Condition中，从而可以有选择性的进行线程通知，在调度线程上更加灵活。 

在使用notify()/notifyAll()方法进行通知时，被通知的线程是由 JVM 选择的，用ReentrantLock类结合Condition实例可以实现“选择性通知” ，这个功能非常重要，而且是 Condition 接口默认提供的。而synchronized关键字就相当于整个 Lock 对象中只有一个Condition实例，所有的线程都注册在它一个身上，如果执行notifyAll()方法的话就会通知所有处于等待状态的线程，这样会造成很大的效率问题。而Condition实例的signalAll()方法，只会唤醒注册在该Condition实例中的所有等待线程。

**关于 等待可中断 的补充：**lockInterruptibly() 会让获取锁的线程在阻塞等待的过程中可以响应中断，如果其他线程中断当前线程 interrupt() ，就会抛出 InterruptedException 异常，可以捕获该异常，做一些处理操作。

**关于 支持超时 的补充：**tryLock(timeout) 方法尝试在指定的超时时间内获取锁。如果成功获取锁，则返回 true；如果在锁可用之前超时，则返回 false。此功能在以下几种场景中非常有用：

- 防止死锁： 在复杂的锁场景中，tryLock(timeout) 通过允许线程在合理的时间内放弃并重试来防止死锁。
- 提高响应速度： 防止线程无限期阻塞。
- 处理时间敏感的操作： 对于具有严格时间限制的操作，tryLock(timeout) 允许线程在无法及时获取锁时继续执行其它操作。

### 可中断锁和不可中断锁有什么区别？

它们的区别在于：**线程在获取锁的过程中被阻塞时，是否能够因为中断而提前放弃等待。**

- 不可中断锁：线程在等待锁期间即使收到中断信号，也不会退出阻塞状态，而是一直等待直到获得锁。中断状态会被保留，但不会影响锁的获取过程。 
  - synchronized 属于典型的不可中断锁。
  - ReentrantLock#lock() 也是不可中断的。
- 可中断锁：线程在等待锁的过程中如果收到中断信号，会立即停止等待并抛出 InterruptedException，从而有机会进行取消或错误处理。 
  - ReentrantLock#lockInterruptibly() 实现了可中断锁。
  - ReentrantLock#tryLock(long time, TimeUnit unit) （带超时的尝试获取）也是可中断的。



## ThreadLocal

### ThreadLocal 有什么用？

通常情况下，我们创建的变量可以被任何一个线程访问和修改。这在多线程环境中可能导致数据竞争和线程安全问题。那么，如果想**让每个线程都有自己的专属本地变量**，该如何实现呢？

JDK 中提供的 ThreadLocal 类正是为了解决这个问题。ThreadLocal 类**允许每个线程绑定自己的值**，可以将其形象地比喻为一个“存放数据的盒子”，每个线程都有自己独立的盒子，用于存储私有数据，确保不同线程之间的数据互不干扰。

当创建一个 ThreadLocal 变量时，每个访问该变量的线程都会拥有一个独立的副本。这也是 ThreadLocal 名称的由来。**线程可以通过 get() 方法获取自己线程的本地副本**，**或通过 set() 方法修改该副本的值**，从而避免了线程安全问题。

```java
public class ThreadLocalExample {
    private static ThreadLocal<Integer> threadLocal = ThreadLocal.withInitial(() -> 0);

    public static void main(String[] args) {
        Runnable task = () -> {
            int value = threadLocal.get();
            value += 1;
            threadLocal.set(value);
            System.out.println(Thread.currentThread().getName() + " Value: " + threadLocal.get());
        };

        Thread thread1 = new Thread(task, "Thread-1");
        Thread thread2 = new Thread(task, "Thread-2");

        thread1.start(); // 输出: Thread-1 Value: 1
        thread2.start(); // 输出: Thread-2 Value: 1
    }
}
```

### ⭐️ThreadLocal 原理了解吗？

从 Thread类源代码入手。

```java
public class Thread implements Runnable {
    //......
    //与此线程有关的ThreadLocal值。由ThreadLocal类维护
    ThreadLocal.ThreadLocalMap threadLocals = null;

    //与此线程有关的InheritableThreadLocal值。由InheritableThreadLocal类维护
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
    //......
}
```

从上面`Thread`类 源代码可以看出`Thread` 类中有一个 `threadLocals` 和 一个 `inheritableThreadLocals` 变量，它们都是 `ThreadLocalMap` 类型的变量，我们可以把 `ThreadLocalMap` 理解成为`ThreadLocal` 类实现的定制化的 `HashMap`。默认情况下这两个变量都是 null，只有当前线程调用 `ThreadLocal` 类的 `set`或`get`方法时才创建它们，实际上调用这两个方法的时候，我们调用的是`ThreadLocalMap`类对应的 `get()`、`set()`方法。

`ThreadLocal`类的`set()`方法：

```java
public void set(T value) {
    //获取当前请求的线程
    Thread t = Thread.currentThread();
    //取出 Thread 类内部的 threadLocals 变量(哈希表结构)
    ThreadLocalMap map = getMap(t);
    if (map != null)
        // 将需要存储的值放入到这个哈希表中
        map.set(this, value);
    else
        createMap(t, value);
}
ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}
```

通过上述内容可以得出结论：最终的变量是放在了当前线程的 ThreadLocalMap 中，并不是存在 ThreadLocal 上，ThreadLocal 可以理解为对ThreadLocalMap的封装，传递了变量值。 

ThrealLocal 类中可以通过Thread.currentThread()获取到当前线程对象，然后通过getMap(Thread t)访问到该线程对象的ThreadLocalMap对象。

每个Thread对象中都具备一个ThreadLocalMap对象，而ThreadLocalMap可以存储以ThreadLocal对象为 key ，Object 对象为 value 的键值对。

```java
ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
    //......
}
```

比如我们在同一个线程中声明了两个 `ThreadLocal` 对象的话， `Thread`内部就是使用仅有的那个`ThreadLocalMap` 对象存放数据的，`ThreadLocalMap`的 key 为 `ThreadLocal`对象，value 为 `ThreadLocal` 对象调用`set`方法设置的值。

`ThreadLocal` 数据结构如下图所示：

![ThreadLocal 数据结构](https://oss.javaguide.cn/github/javaguide/java/concurrent/threadlocal-data-structure.png)

`ThreadLocalMap`是`ThreadLocal`的**静态内部类**。

![ThreadLocal内部类](https://oss.javaguide.cn/github/javaguide/java/concurrent/thread-local-inner-class.png)

### ⭐️ThreadLocal 的内存泄露是怎么导致的？

ThreadLocal 内存泄漏的根本原因在于其内部实现机制。

通过上面的内容我们已经知道：每个线程维护一个名为 ThreadLocalMap 的 map。 当使用 ThreadLocal 存储值时，实际上是将值存储在当前线程的 ThreadLocalMap 中，其中 ThreadLocal 实例作为 key，存储的值作为 value 。

`ThreadLocal` 的 `set()` 方法源码如下：

```java
public void set(T value) {
    Thread t = Thread.currentThread(); // 获取当前线程
    ThreadLocalMap map = getMap(t);   // 获取当前线程的 ThreadLocalMap
    if (map != null) {
        map.set(this, value);         // 设置值
    } else {
        createMap(t, value);          // 创建新的 ThreadLocalMap
    }
}
```

`ThreadLocalMap` 的 `set()` 和 `createMap()` 方法中，并没有直接存储 `ThreadLocal` 对象本身，而是使用 `ThreadLocal` 对象的哈希值计算数组索引，并将`ThreadLocal对象`最终存储在类型为`static class Entry extends WeakReference<ThreadLocal<?>>`的数组中。

`ThreadLocalMap` 的 `Entry` 定义如下：

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

`ThreadLocalMap` 的 `key` 和 `value` 引用机制：

- **key 是弱引用**：`ThreadLocalMap` 中的 key 是 `ThreadLocal` ，使用的是弱引用 (`WeakReference<ThreadLocal<?>>`)。 这意味着当 `ThreadLocal` 实例不再被任何强引用指向时，垃圾回收器会在下次 GC 时回收该实例，导致 `ThreadLocalMap` 中对应的 key 变为 `null`。
- **value 是强引用**：即使 `key` 被 GC，`value` 仍然被 `ThreadLocalMap.Entry` 强引用，无法被 GC 回收。

当 `ThreadLocal` 对象实例失去强引用后，其对应的 value 仍然存在于 `ThreadLocalMap` 中，因为 `Entry` 对象强引用了它。如果线程持续存活（例如线程池中的线程），`ThreadLocalMap` 也会一直存在，导致 key 为 `null` 的 entry 无法被垃圾回收，如果`value`存储的是内存很大的对象，那么多个线程都出现这种情况就会造成内存泄漏。

也就是说，内存泄漏的发生需要同时满足两个条件：

1. `ThreadLocal` 对象不再被强引用；
2. 线程持续存活，导致 `ThreadLocalMap` 长期存在。

虽然 `ThreadLocalMap` 在 `get()`, `set()` 和 `remove()` 操作时会尝试清理 key 为 null 的 entry，但这种清理机制是被动的，并不完全可靠。

**如何避免内存泄漏的发生？**

1. 在使用完 `ThreadLocal` 后，务必调用该`ThreadLocal`对象的 `remove()` 方法。 **这是最安全和最推荐的做法**。 `remove()` 方法会从 `ThreadLocalMap` 中显式地移除对应 `key` 的 `entry`，彻底解决内存泄漏的风险。 
2. 在线程池等线程复用的场景下，使用 `try-finally` 块可以确保即使发生异常，`remove()` 方法也一定会被执行。

### ⭐️如何跨线程传递 ThreadLocal 的值？

由于 `ThreadLocal` 的变量值存放在当前 `Thread` 里，而父子线程属于不同的 `Thread` 。因此在异步场景下，父子线程的 `ThreadLocal` 值无法进行传递。

如果想要在异步场景下传递 `ThreadLocal` 值，有两种解决方案：

- `InheritableThreadLocal` ：`InheritableThreadLocal` 是 JDK2 提供的工具，继承自 `ThreadLocal` 。使用 `InheritableThreadLocal` 时，**会在创建子线程时，令子线程继承父线程中的 `ThreadLocal` 值**，但是**无法支持线程池场景下的多线程间的 `ThreadLocal` 值传递**。
- `TransmittableThreadLocal` ： `TransmittableThreadLocal` （简称 TTL） 是阿里巴巴开源的工具类，继承并加强了`InheritableThreadLocal`类，可以在线程池的场景下支持 `ThreadLocal` 值传递。项目地址：<https://github.com/alibaba/transmittable-thread-local>。



#### InheritableThreadLocal 原理

`InheritableThreadLocal` 实现了创建异步线程时，子线程可以继承父线程 `ThreadLocal` 值的功能。

该类是 JDK 团队提供的，通过改造 JDK 源码包中的 `Thread` 类来实现创建线程时，`ThreadLocal` 值的传递。

**InheritableThreadLocal 的值存储在哪里？**

在 `Thread` 类中添加了一个新的 `ThreadLocalMap` ，命名为 `inheritableThreadLocals` ，该变量用于存储需要跨线程传递的 `ThreadLocal` 值。如下：

```java
class Thread implements Runnable {
    ThreadLocal.ThreadLocalMap threadLocals = null;
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
}
```

**如何完成 ThreadLocal 值的传递？**

**通过改造 `Thread` 类的构造方法来实现**，在创建 `Thread` 线程时，如果判断父线程的`inheritableThreadLocals`不为null，则复制父线程的 `ThreadLocalMap`。相关代码如下：

```java
// Thread 的构造方法会调用 init() 方法
private void init(/* ... */) {
	// 1、获取父线程
    Thread parent = currentThread();
    // 2、将父线程的 inheritableThreadLocals 赋值给子线程
    if (inheritThreadLocals && parent.inheritableThreadLocals != null)
        this.inheritableThreadLocals =
        	ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
}
```

`InheritableThreadLocal`的局限性：

- 新创建的子线程才能继承，因此线程池中无效（因为线程都已存在，不会重新创建）
- 子线程修改值不会影响父线程
- 传递的是对象引用，可能引发线程安全问题

#### TransmittableThreadLocal 原理

JDK 默认没有支持线程池场景下 `ThreadLocal` 值传递的功能，因此阿里巴巴开源了一套工具 `TransmittableThreadLocal` 来实现该功能。**这是解决线程池传递问题的标准方案。**

阿里巴巴无法改动 JDK 的源码，因此他内部通过 **装饰器模式** 在原有的功能上做增强，以此来实现线程池场景下的 `ThreadLocal` 值传递。

TTL 改造的地方有两处：

- 实现自定义的 `Thread` ，在 `run()` 方法内部做 `ThreadLocal` 变量的赋值操作。
- 基于 **线程池** 进行装饰，在 `execute()` 方法中，不提交 JDK 内部的 `Thread` ，而是提交自定义的 `Thread` 。

如果想要查看相关源码，可以引入 Maven 依赖进行下载。

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.12.0</version>
</dependency>
```

#### 应用场景

1. **压测流量标记**： 在压测场景中，使用 `ThreadLocal` 存储压测标记，用于区分压测流量和真实流量。如果标记丢失，可能导致压测流量被错误地当成线上流量处理。
2. **上下文传递**：在分布式系统中，传递链路追踪信息（如 Trace ID）或用户上下文信息





# 计网

# [TCP/IP 四层模型](#⭐️tcp-ip-四层模型是什么-每一层的作用是什么)

**TCP/IP 四层模型** 是 OSI 七层模型的精简版本，由以下 4 层组成：

![TCP/IP 各层协议概览](https://oss.javaguide.cn/github/javaguide/cs-basics/network/network-protocol-overview.png)

**应用层：** 规定数据的格式，比如网页是 HTML，邮件是 SMTP，同时为应用程序提供各种网络服务接口

- HTTP、HTTPS（Hypertext Transfer Protocol，超文本传输协议）
- SSH（Secure Shell Protocol，安全网络传输协议），用于远程安全登录
- DNS（Domain Name System，域名管理系统）
- FTP、SFTP 文件传输协议
- SMTP、POP3、IMAP 邮件传输协议

**传输层**： 通过端口寻址实现端到端的通信，并通过 TCP 实现可靠传输，UDP 实现实时高效传输

- TCP 协议：报文段结构、可靠数据传输、流量控制、拥塞控制
- UDP 协议：报文段结构、RDT（可靠数据传输协议）

**网络层：**IP 寻址，路由选择：决定数据包如何从源主机到达目标主机，分片与重组：数据包太大就切开

- IP（Internet Protocol，网际协议）
- ARP（Address Resolution Protocol，地址解析协议），将 IP 地址解析成 MAC 地址。
- ICMP ping使用的协议

**网络接口层：**处理 MAC 寻址，负责在网线/无线中传输比特流，管 0 和 1怎么变成电信号、光信号等。

- MAC 协议
- 以太网技术



# OSI 七层模型

| 层级 | 名称           | 协议数据单元  | 主要协议/技术                     | 核心作用                       |
| ---- | -------------- | ------------- | --------------------------------- | ------------------------------ |
| 7    | **应用层**     | Data（数据）  | HTTP、HTTPS、FTP、SMTP、DNS、DHCP | 给应用程序提供统一接口         |
| 6    | **表示层**     | Data（数据）  | JPEG、GIF、ASCII、SSL/TLS、MIME   | 数据格式转换、加密、压缩       |
| 5    | **会话层**     | Data（数据）  | RPC、NetBIOS、PPTP、SIP           | 建立、管理、终止会话           |
| 4    | **传输层**     | Segment（段） | TCP、UDP、SCTP                    | 端到端的数据传输               |
| 3    | **网络层**     | Packet（包）  | IP、ICMP、OSPF、BGP、ARP          | 逻辑寻址和路由选择             |
| 2    | **数据链路层** | Frame（帧）   | Ethernet、PPP、HDLC、VLAN、交换机 | MAC 物理寻址、帧传输、错误检测 |
| 1    | **物理层**     | Bit（比特）   | 网线、光纤、RJ45、802.3、Hub      | 在网线/无线中传输比特流        |

网络层的主机不再是和另一台主机进行交互了，而是在和中间系统进行交互。也就是说，应用层和传输层都是端到端的协议，而网络层及以下都是中间件的协议了。

**网络层的核心功能——转发与路由！**网络层负责把数据从一台机器送到另一台机器。

- 转发：将数据分组从路由器的输入端口转移到合适的输出端口。
- 路由：是最核心的功能。当数据从电脑发送到远处的服务器，中间要经过很多路由器，网络层负责选择一条**最优路径**。
- 寻址：使用 IP 地址（IPv4 或 IPv6）来唯一标识网络中的设备。

[网络分层的原因](https://javaguide.cn/cs-basics/network/osi-and-tcp-ip-model.html#%E7%BD%91%E7%BB%9C%E5%88%86%E5%B1%82%E7%9A%84%E5%8E%9F%E5%9B%A0)

**各层之间相互独立**：不需要关心其他层是如何实现的，只需要知道自己如何调用下层提供好的功能就可以了

**提高了整体灵活性**：高内聚、低耦合

每一层只需要专注做一类事情即可。



# 场景题

## 访问网页的全过程

1. **URL 解析：**在浏览器中输入指定网页的 URL，浏览器解析出协议（如 HTTP/HTTPS）、域名（如 `www.google.com`）、端口（HTTP 默认80，HTTPS 默认443）、路径（如 `/search`）等信息。

2. **DNS 解析：**先通过浏览器缓存、系统缓存等查找服务器的 IP 地址。如果缓存未命中，浏览器向本地 DNS 服务器发起请求，最终可能通过根 DNS 服务器、顶级域 DNS 服务器、权威 DNS 服务器逐级查询，直到获取到目标域名的 IP 地址。

3. **获取 MAC 地址：**当浏览器得到 IP 地址后，数据传输还需要知道目的主机的 MAC 地址，因为应用层下发数据给传输层，TCP 协议会指定源端口号和目的端口号，然后下发给网络层。网络层会将本机地址作为源地址，获取的 IP 地址作为目的地址。然后将下发给数据链路层，数据链路层的发送需要加入通信双方的 MAC 地址。

   通过 ARP 协议来获取目标 IP 地址主机的 MAC 地址。

4. **建立 TCP 连接：**浏览器根据 **端口号、IP 地址和 MAC 地址**，向目标服务器发起 TCP 连接建立请求，并通过三次握手建立 TCP 连接。如果是 HTTPS 协议，在通信前还需要进行 TLS 的四次握手。

5. **浏览器发送 HTTP 请求：**建立 TCP 连接后，浏览器向服务器发送 HTTP 请求报文，以请求获取网页的内容。

   HTTP 报文包含**请求行** (请求方法(GET/POST)、请求路径、HTTP 版本等)、**请求头** (Cookie、浏览器标识等)、**请求体** (GET 请求没有请求体，POST 请求可能包含表单数据)。

6. **服务器处理 HTTP 请求并返回 HTTP 响应：**服务器返回的响应报文包含**状态行** (HTTP版本 和 **状态码**)、**响应头**(返回的资源类型，如 HTML/JSON)、**响应体**(HTML 代码、JSON 数据等)。

7. **浏览器接收 HTTP 响应：**浏览器收到 HTTP 响应报文后，解析响应体中的 HTML 代码和 JSON 数据，渲染网页的结构和样式。

8. **断开 TCP 连接：**数据传输完毕后，如果浏览器不需要和服务器进行通信，可以通过四次挥手断开 TCP 连接。

## 网页非常慢转圈圈的时候，从哪些角度定位问题？

**可以通过抓包排查，思路为：**

1.先确认浏览器是否可以访问其他网站，如果不可以，说明是客户端网络的问题，然后检查客户端网络配置（连接 Wi-Fi 正不正常，有没有插网线）。

2. 如果客户端网络没问题，就抓包确认 DNS 是否解析出了 IP 地址，如果没有解析出来，说明域名可能写错了，如果解析出了 IP 地址，就抓包确认有没有和服务器建立三次握手，如果能成功建立三次握手，并且发出了 HTTP 请求，但是就是没有显示页面，可以查看服务器返回的响应码：

- 如果是 404 错误码，检查输入的 url 是否正确；
- 如果是500，说明服务器此时有问题；
- 如果是200，F12 看看是不是前端代码有问题导致浏览器没有渲染出页面。

3.如果客户端和服务器都没问题，但是访问的速度很慢，很久才显示出来，可以看看客户端的网口流量是否太大了，导致 TCP 发生丢包之类的问题。

## 服务器正常启动，但是客户端请求不到，如何排查？

如果客户端请求的接口没有响应，排查的方式：

- 检查接口IP地址是否正确，ping一下接口地址。
- 检查被测接口端口号是否正确，可以在本机 Telnet 接口的 IP 和端口号，检查端口号能否连通
- 检查服务器的防火墙是否关闭，开放对应的 IP 和端口。
- 检查客户端是否设置了网络代理，网络代理可以造成请求失败。

如果客户端的请求有响应，但是返回了错误状态码，那么根据错误码做对应的排查：

- 400：客户端请求错误，比如请求参数格式错误
- 401：未授权，比如请求 header 里，缺乏必要的信息头。(token，authorization等）
- 403：禁止，可能用户的账号没有对应的 URL 权限，或者项目中所用的中间件不允许远程连接（Tomcat)
- 404：资源未找到，导致这种情况的原因很多，比如URL地址不正确
- 500：服务器内部错误，出现这种情况，说明服务器内部报错了，需要登录服务器检查错误日志，根据具体的信息进行排查
- 502/503/504（错误的网关、服务器无法获得、网关超时）：如果单次调用接口就报该错误，说明后端服务器配置有问题或者服务不可用，挂掉了；如果是并发压测时出现的，说明后端压力太大，出现异常，此问题一般是后端出现了响应时间过长或者是无响应造成的

## 服务器 ping 不通但是 HTTP 能请求成功是为什么?

ping 走的是 ICMP 协议，HTTP 走的是 TCP协议。

有可能服务器的防火墙禁止 ICMP 协议但没有禁止 TCP 协议，就会出现服务器 ping 不通但是 HTTP 能请求成功。



# HTTP vs HTTPS

## [HTTP](#http-协议)

**[HTTP 协议介绍](#http-协议介绍)**

HTTP 协议，全称超文本传输协议（Hypertext Transfer Protocol）。HTTP 协议用来规范超文本的传输。

- 超文本是文字、图片、视频等的混合体，最关键有超链接，能从一个超文本跳转到另外一个超文本。
- HTML 是最常见的超文本，它是纯代码文件，但用很多标签定义了图片、视频的链接，再经过浏览器的解释，呈现出一个有文字画面的网页。HTTP 协议用来规范客户端和服务器以及服务器和服务器之间的超文本传输。
- HTTP 是一个无状态协议，每次请求独立，服务器不保存客户端状态信息。

**[HTTP 协议通信过程](#http-协议通信过程)**

HTTP 是应用层协议，它以 TCP（传输层）作为底层协议，默认端口为 80。通信过程主要如下：

1. 服务器在 80 端口等待客户端的请求。
2. 浏览器发起到服务器的 TCP 连接（创建套接字 Socket）。
3. 服务器接收来自浏览器的 TCP 连接。
4. 浏览器（HTTP 客户端）与 Web 服务器（HTTP 服务器）交换 HTTP 消息。
5. 关闭 TCP 连接。

[HTTP 协议优点](#http-协议优点)

扩展性强、速度快、跨平台支持性好。

## [HTTPS](#https-协议)

[HTTPS 协议介绍](#https-协议介绍)

HTTPS 协议（Hyper Text Transfer Protocol Secure），是 HTTP 的加强安全版本。HTTPS 是基于 HTTP 的，也是用 TCP 作为底层协议，并额外使用 SSL/TLS 协议用作加密和安全认证。默认端口号是 443.

HTTPS 协议中，SSL 通道通常使用基于密钥的加密算法，密钥长度通常是 40 比特或 128 比特。

[HTTPS 协议优点](#https-协议优点)

保密性好、信任度高。

## [HTTPS 的核心—SSL/TLS 协议](#https-的核心—ssl-tls-协议)

HTTPS 之所以能达到较高的安全性要求，就是结合了 SSL/TLS 和 TCP 协议，对通信数据进行加密，解决了 HTTP 数据透明的问题。

**SSL（安全套接层）** 和 **TLS（传输层安全）** 是加密协议，用于在网络通信提供安全、加密的连接。最常见的是在 **客户端（如浏览器）** 和 **服务器（如网站）** 之间。

可以把它想象成一条**加密隧道**，在客户端和目标服务器之间建立。所有通过这条隧道传输的数据都会被加密，即使被第三方截获，也无法被读取或篡改。

**[SSL 和 TLS 的区别？](#ssl-和-tls-的区别)**

**SSL 和 TLS 没有太大的区别。**

SSL (Secure Sockets Layer)，TLS (Transport Layer Security)，**SSL 3.0 被命名为 TLS 1.0**

- **SSL** 是早期的版本（1.0, 2.0, 3.0），由于被发现存在安全漏洞，现已全部被弃用。
- **TLS** 是 SSL 更安全的继任者。
- 网站地址栏的 **“HTTPS”**（`https://`） 中的 **“S”** 就代表着 SSL/TLS 的保护。

[SSL/TLS 的工作原理](#ssl-tls-的工作原理)

[非对称加密](#非对称加密)

SSL/TLS 的核心要素是**非对称加密**。非对称加密采用两个密钥——一个公钥，一个私钥。在通信时，私钥仅由解密者保存，公钥由任何一个想与解密者通信的发送者（加密者）所知。可以设想一个场景，

> 在某个自助邮局，每个通信信道都是一个邮箱，每一个邮箱所有者都在旁边立了一个牌子，上面挂着一把钥匙：这是我的公钥，发送者请将信件放入我的邮箱，并用公钥锁好。
>
> 但是公钥只能加锁，并不能解锁。解锁只能由邮箱的所有者执行——因为只有他保存着私钥。
>
> 这样，通信信息就不会被其他人截获了，这依赖于私钥的保密性。

![img](https://javaguide.cn/assets/public-key-cryptography-BQGPLr2_.png)

非对称加密的公钥和私钥需要采用一种复杂的数学机制生成（密码学认为，为了较高的安全性，尽量不要自己创造加密方案）。公私钥对的生成算法依赖于单向陷门函数。

> 单向函数：已知单向函数 f，给定任意一个输入 x，易计算输出 y=f(x)；而给定一个输出 y，假设存在 f(x)=y，很难根据 f 来计算出 x。
>
> 单向陷门函数：一个较弱的单向函数。已知单向陷门函数 f，陷门 h，给定任意一个输入 x，易计算出输出 y=f(x;h)；而给定一个输出 y，假设存在 f(x;h)=y，很难根据 f 来计算出 x，但可以根据 f 和 h 来推导出 x。

在这里，函数 f 的计算方法相当于公钥，陷门 h 相当于私钥。公钥 f 是公开的，任何人对已有输入，都可以用 f 加密，而要想根据加密信息还原出原信息，必须要有私钥才行。

[对称加密](#对称加密)

使用 SSL/TLS 进行通信的双方需要使用非对称加密方案来通信，但是非对称加密设计了较为复杂的数学算法，在实际通信过程中，计算的代价较高，效率太低，因此，**SSL/TLS 实际对消息的加密使用的是对称加密**。

> 对称加密：通信双方共享唯一密钥 k，加解密算法已知，加密方利用密钥 k 加密，解密方利用密钥 k 解密，保密性依赖于密钥 k 的保密性。

![img](https://javaguide.cn/assets/symmetric-encryption-01EOzq7r.png)

**对称加密的密钥**生成代价比**公私钥对**的生成代价低得多，那为什么 SSL/TLS 还需要使用非对称加密呢？

- 因为对称加密的保密性完全依赖于密钥的保密性，在双方通信之前，需要商量一个用于对称加密的密钥。
- 而网络通信的信道是不安全的，传输的报文对任何人都可见，密钥的交换肯定不能直接在网络信道中传输。
- 因此需要使用非对称加密来对对称加密的密钥进行加密，保护该密钥不在网络信道中被窃听。
- 这样，通信双方只需要一次非对称加密就能交换对称加密的密钥，在之后的信息通信中，使用绝对安全的对称密钥对信息进行对称加密即可保证传输消息的保密性。

也就是需要**使用非对称加密**来**加密对称加密的密钥**，保证**对称密钥交换**的安全性。

[公钥传输的信赖性](#公钥传输的信赖性)

SSL/TLS 介绍到这里，了解信息安全的朋友又会想到一个安全隐患，设想一个下面的场景：

> 客户端 C 和服务器 S 想要使用 SSL/TLS 通信，由上述 SSL/TLS 通信原理，C 需要先知道 S 的公钥，而 S 公钥的唯一获取途径，就是把 S 公钥在网络信道中传输。
>
> 假设 S 公钥不加密，在信道中传输，如果攻击者 A 发送给 C 一个诱饵服务器 AS 的公钥，而 C 却以为是 S 的公钥，C 后续就会使用 AS 公钥对数据进行加密，并在公开信道传输。
>
> 那么 A 将捕获这些加密包，用 AS 的私钥解密，就截获了 C 本要给 S 发送的内容，而 S 捕获这些加密包后用 S 的私钥解密得到的却是乱码。
>
> 即使 S 公钥加密，也难以避免 C 被 AS 拐跑的问题

![img](https://javaguide.cn/assets/attack1-ftBWLwVN.png)

证书颁发机构（CA，Certificate Authority）用于解决公钥传输的信赖性问题。CA 默认是受信任的第三方，会给各个服务器颁发证书，证书存储在服务器上，并附有 CA 的**电子签名**。

当客户端向服务器发送 HTTPS 请求时，会先获取目标服务器的证书，并检验证书的合法性。如果证书非法，就会发生错误，如果合法，客户端就可以放心地信任证书上的公钥就是目标服务器的公钥。

[数字签名](#数字签名)

数字签名要解决的问题是防止证书被伪造，第三方信赖机构 CA 之所以能被信赖，就是靠**数字签名技术** 。

数字签名是 CA 在给服务器颁发证书时，使用散列+加密的组合技术，在证书上盖个章，以此来提供验伪的功能。具体行为如下：

> CA 知道服务器的公钥，对证书采用散列技术生成一个摘要，CA 使用 CA 私钥对该摘要进行加密，并附在证书下方，发送给服务器。
>
> 服务器将该证书发送给客户端，客户端需要验证该证书的合法性，就会找到第三方机构 CA，获知 CA 的公钥，并用 CA 公钥对证书的签名进行解密，获得了 CA 生成的摘要。
>
> 客户端对证书数据（包含服务器的公钥）做相同的散列处理，得到摘要，并将该摘要与之前从签名中解码出的摘要做对比，如果相同，则身份验证成功；否则验证失败。

![img](https://javaguide.cn/assets/digital-signature--6DaTXPY.png)

总的来说，带有证书的公钥传输机制如下：

1. 设有服务器 S，客户端 C，和第三方信赖机构 CA。
2. S 信任 CA，CA 知道 S 的公钥，CA 向 S 颁发证书。并附上使用 CA 私钥对消息摘要进行加密的签名。
3. S 获得 CA 颁发的证书，将该证书传递给 C。
4. C 获得 S 的证书，信任 CA 并知晓 CA 公钥，使用 CA 公钥对 S 证书上的签名解密得到摘要，同时对消息进行散列处理，得到摘要。比较摘要，验证 S 证书的真实性。
5. 如果 C 验证 S 证书是真实的，则信任 S 的公钥（在 S 证书中）。

![img](https://javaguide.cn/assets/public-key-transmission-CNWLGx6C.png)

对于数字签名，这里讲的比较简单。

## HTTPS 的工作流程

HTTPS 通过 SSL/TLS 协议在 HTTP 的基础上增加了安全层。

核心流程是：**握手阶段**用非对称加密交换密钥并验证服务器证书，**传输阶段**用对称加密保护数据。这样既保证了密钥交换的安全性，又保证了数据传输的高效性。

SSL/TLS 握手过程：

**TLS 第一次握手：客户端向服务器的 443 端口发起请求 (Client Hello)**

- 请求中包含一个随机数（`Client Random`）、客户端支持的 TLS 版本以及支持的加密套件列表，加密套件定义了将使用哪种加密算法和哈希算法，如 RSA加密算法。

**TLS 第二次握手：服务器响应 (Server Hello & Certificate)**

- 服务器收到请求后先确认支持的 TLS 协议版本，如果不支持则关闭加密通信。
- 如果支持 TLS 版本，服务器会选择一个双方都支持的加密套件，并生成另一个随机数（`Server Random`）。
- 然后服务器会将其**数字证书**发送给客户端，包含了**服务器的公钥**、域名信息、颁发机构（CA）等。

**TLS 第三次握手：客户端验证与密钥交换 (Client Key Exchange)**

- 客户端收到证书后，会检查证书是否过期、域名是否与正在访问的域名一致、是否由受信任的 CA 机构签发。

  客户端会用对应 CA 的公钥去解密证书的数字签名来验证证书的真伪。如果验证通过，就证明了服务器的身份是可信的而不是中间人伪造的。

- 证书验证通过后，客户端会再生成一个随机数，称之为预主密钥（`Pre-Master Secret`）。客户端用从服务器证书中获取的**公钥**对这个预主密钥进行**非对称加密**，然后将加密后的密文发送给服务器。

**4. TLS 第四次握手：服务器解密与生成会话密钥**

- 服务器收到加密的预主密钥后，使用自己的**私钥**进行**非对称解密**，获取到原始的预主密钥。至此客户端和服务器都拥有了三个相同的信息：`Client Random`、`Server Random` 和 `Pre-Master Secret`。

  双方会使用一个共同商定的算法，通过这三个随机数各自独立地计算出完全相同的**会话密钥（Session Key）**，这个密钥就是**对称密钥**。

**5. 握手完成，开始加密通信**

- 双方互相发送一个 Finished 消息，这个消息会用刚刚生成的**会话密钥**进行加密。如果对方能成功解密，就代表整个握手过程顺利完成，安全通道已经建立。在此之后的所有 HTTP 通信都会使用这个**对称的会话密钥**进行加密和解密。因为对称加密的效率远高于非对称加密，所以适合用于大量数据的传输。

## 为什么 TLS 用对称加密而不用非对称加密

TLS 没有全程使用非对称加密主要是出于**性能和效率**的考虑。简单来说，**非对称加密非常慢，而对称加密非常快**。

- **对称加密**：加解密使用同一个密钥，算法通常基于相对简单的位运算，计算量小，速度非常快。
- **非对称加密**：加解密使用公钥和私钥一对密钥。算法（如RSA）基于复杂的数学难题（比如大数质因数分解），计算量极大，CPU消耗非常高，速度通常比对称加密慢**几百到上千倍**。

如果 HTTPS 传输的所有数据，比如一个网页的图片、视频都用非对称加密，那么会导致服务器和客户端的 CPU 不堪重负，网络延迟会高到无法接受，导致用户体验极差。

TLS 根据非对称加密和对称加密各自的优势，设计了**混合加密机制**。

- **非对称加密的优点**：
  - **优点**：解决了**密钥分发**的难题。公钥可以任意分发，不用担心被窃取，只要私钥安全，通信就是安全的。
  - 因此，在 TLS 握手阶段，非对称加密的唯一使命就是用来**安全地协商出一个会话密钥**。它加密的只是一个极小量的数据（即预主密钥），这个过程虽然慢，但只在连接建立之初发生一次，完全可以接受。
- **对称加密的优点**：
  - **优点**：**速度快**，适合对大量数据进行加密。
  - **用途**：一旦双方通过非对称加密安全地得到了同一个**会话密钥**，后续所有的应用层数据（HTTP报文）就全部使用这个对称密钥进行加密传输。这样既保证了安全，又保证了极高的通信效率。

因此，**TLS 并不是不用非对称加密**，它用非对称加密来加密对称加密的密钥，解决**密钥交换问题**，而用对称加密来解决高效的**数据传输问题**。



## HTTP 和 HTTPS 的区别

区别主要有以下四点：

- HTTP 是超文本传输协议，信息是明文传输，存在安全风险的问题。HTTPS 在 TCP 和 HTTP 网络层之间加入了 SSL/TLS 安全协议，使得报文能够加密传输，解决了 HTTP 不安全的问题。
- HTTP 连接建立相对简单，TCP 三次握手之后便可进行 HTTP 的报文传输。而 HTTPS 在 TCP 三次握手之后，还需要完成 SSL/TLS 的握手过程才可以进入加密报文传输。
- HTTP 默认端口号是 80，HTTPS 默认端口号是 443。
- HTTPS 协议需要向 CA（证书权威机构）申请数字证书来保证服务器的身份是可信的。



## HTTP 常见状态码

HTTP 状态码用于描述 HTTP 请求的结果，比如 2xx 就代表请求被成功处理。

![常见 HTTP 状态码](https://oss.javaguide.cn/github/javaguide/cs-basics/network/http-status-code.png)

[1xx Informational（信息性状态码）](#_1xx-informational-信息性状态码)

1xx 平时大概率不会碰到，表示请求正在处理。

[2xx Success（成功状态码）](#_2xx-success-成功状态码)

- **200 OK**：请求被成功处理。例如，发送一个查询用户数据的 HTTP 请求到服务端，服务端正确返回了用户数据。这个是我们平时最常见的一个 HTTP 状态码。
- **202 Accepted**：服务端已经接收到了请求，但是还未处理。例如，发送一个需要服务端花费较长时间处理的请求（如报告生成、Excel 导出），服务端接收了请求但尚未处理完毕。
- **204 No Content**：服务端已经成功处理了请求，但是没有返回任何内容。例如，发送请求删除一个用户，服务器成功处理了删除操作但没有返回任何内容。

[3xx Redirection（重定向状态码）](#_3xx-redirection-重定向状态码)

- **301 Moved Permanently**：资源被永久重定向了。比如你的网站的网址更换了。
- **302 Found**：资源被临时重定向了。比如你的网站的某些资源被暂时转移到另外一个网址。

[4xx Client Error（客户端错误状态码）](#_4xx-client-error-客户端错误状态码)

- **400 Bad Request**：发送的 HTTP 请求存在问题。比如请求参数不合法、请求方法错误。
- **401 Unauthorized**：未认证却请求需要认证之后才能访问的资源。
- **403 Forbidden**：直接拒绝 HTTP 请求，不处理。一般用来针对非法请求。
- **404 Not Found**：请求的资源未在服务端找到。比如请求某个用户信息，服务端并没有找到指定的用户。

[5xx Server Error（服务端错误状态码）](#_5xx-server-error-服务端错误状态码)

- **500 Internal Server Error**：服务端出问题了（通常是服务端出 Bug 了）。比如服务端处理请求的时候突然抛出异常，但是异常并未在服务端被正确处理。
- **502 Bad Gateway**：网关 (中间层) 将请求转发到服务端，但是收到了服务端无效或错误的响应。



## HTTP 报文有哪些部分

建立 TCP 连接后，浏览器向服务器发送 HTTP 请求报文，以请求获取网页的内容。

HTTP **请求报文**包含 

- **请求行：**请求方法 (GET/POST)、请求路径、HTTP 版本等
- **请求头：** Cookie、浏览器标识等
- **请求体：** GET 请求没有请求体，POST 请求可能包含表单数据。

HTTP **响应报文**包含

- **状态行：** HTTP版本 和 **状态码**
- **响应头：**返回的资源类型，如 HTML / JSON
- **响应体：**返回的资源，如 HTML 代码、JSON 数据等



## HTTP 的请求类型有哪些

- GET：用于向服务器请求获取指定资源。
- POST：用于向服务器请求提交表单数据或进行资源的创建。
- PUT：用于向服务器请求更新指定资源。
- DELETE：用于向服务器请求删除指定资源。



## GET 和 POST 的区别

GET 和 POST 是 HTTP 协议中两种常用的请求方法，在不同的场景和目的下有不同的特点和用法。

- **语义（主要区别）**：GET 通常用于向服务器请求获取指定资源，而 POST 通常用于向服务器请求提交表单数据或进行资源的创建。

- **幂等**：GET 请求是幂等的，多次重复执行不会改变资源的状态，而 POST 请求是不幂等的，每次执行可能会产生不同的结果或影响资源的状态。

- **格式**：GET 请求的参数通常放在 URL 中，，而 POST 请求的参数通常放在请求体（body）中。

  GET 请求也可以用 body 传输数据，但可能会导致一些兼容性或者语义上的问题。

- **缓存**：由于 GET 请求是幂等的，可以被浏览器或其他中间节点（如代理、网关）缓存起来，以提高性能和效率。而 POST 请求则不适合被缓存，每次执行可能需要实时的响应。

- **安全性**：GET 请求和 POST 请求如果使用 HTTP 协议的话，都不安全，因为 HTTP 协议本身是明文传输的，必须使用 HTTPS 协议来加密传输数据。另外，GET 请求相比 POST 请求更容易泄露敏感数据，因为 GET 请求的参数通常放在 URL 中。

不过，也有一些项目所有的请求都用 POST，这个并不是固定的，项目组达成共识即可。



## HTTP 的长连接是什么？

HTTP 协议采用的是「请求-应答」的模式，客户端发起了请求服务端才会返回响应。

HTTP 是基于 TCP 协议实现的，客户端与服务端进行 HTTP 通信前需要先建立 TCP 连接，然后客户端发送 HTTP 请求，服务端收到后返回响应，随后断开 TCP 连接。

**HTTP 短连接：**每次请求都经历建立连接 -> 请求资源 -> 响应资源 -> 断开连接的过程

**HTTP 长连接：**HTTP 的 Keep-Alive 通过使用同一个 TCP 连接来发送和接收多个 HTTP 请求/应答，避免了连接建立和释放的开销，只要任意一端没有明确提出断开连接，则保持 TCP 连接状态。



## HTTP1.1 怎么对请求做拆包

在 HTTP/1.1 中，请求的拆包是通过 Content-Length 字段完成的。该字段指示了请求正文的长度，服务器可以根据该长度来正确接收和解析请求。**具体来说：**

客户端发送 HTTP 请求时，会在请求头中添加 Content-Length 字段，该字段的值表示请求正文的字节数。

服务器在接收到请求后会根据 Content-Length 字段的值来确定请求的长度，并从请求中读取相应数量的字节。

这种基于 Content-Length 字段的拆包机制可以确保服务器正确接收到完整的请求，避免了请求的丢失或截断问题



## HTTP 断点续传

断点续传是 HTTP/1.1 协议支持的特性。

实现断点续传功能需要客户端记录下当前的下载进度，并在需要续传的时候通知服务端本次需要下载的内容片段。

一个简单的断点续传流程如下：

1.客户端下载一个 1024k 的文件，服务端发送 Accept-Ranges:bytes 来告诉客户端，其支持带 Range 的请求

2.假如客户端下载到 512K 的时候网络突然断开了，网络恢复后客户端再次下载时候，需要在 HTTP 头中申明本次需要续传的片段：Range:bytes=512000- 这个头通知服务端从文件的 512K 位置开始传输文件直到文件内容结束

3.服务端收到断点续传请求，从文件的 512K 位置开始传输，并且在HTTP头中增加：Content-Range:bytes 512000-/1024000，Content-Length:512000。并且此时服务端返回的 HTTP 状态码应该是206 Partial Content。如果客户端传递过来的 Range 超过资源的大小，则响应416 Requested RangeNot Satisfiable

通过上面流程可以看出：断点续传中有 4 个 HTTP 头必不可少，分别是 Range 头、Content-Range 头、Accept-
Ranges 头、Content-Length 头。其中第一个Range头是客户端发过来的，后面3个头需要服务端发送给客户端。

下面是它们的说明：

**Accept-Ranges:bytes：**这个值声明了可被接受的每一个范围请求，大多数情况下是字节数 bytes
**Range:bytes=开始位置-结束位置：**Range 是浏览器告知服务器所需分部分内容范围的消息头。



## HTTP 建立 TCP 连接后，什么情况下会中断

- 当服务端或者客户端执行 close 系统调用的时候，会发送 FIN 报文进行四次挥手的断开连接过程
- 如果发送方发送数据之后接收方超过一段时间没有响应 ACK 报文，并且发送方重传数据达到最大次数的时
  候，就会断开TCP连接
- 当 HTTP 超过一定的时间没有进行请求和响应的时候就会断开 TCP 连接



## HTTP 是无状态的吗

HTTP 是无状态的，这意味着每个请求都是独立的，服务器不会在多个请求之间保留关于客户端的状态信息，在多个 HTTP 请求中，服务器不会记住之前的请求或会话状态。

虽然HTTP本身是无状态的，但可以通过一些机制来实现状态保持，最常见的方式是使用 Cookie 和 Session 来跟踪用户状态。通过在客户端存储会话信息或状态信息，服务器可以识别和跟踪特定用户的状态，以提供一定程度的状态保持功能。

可以说即使 HTTP 本身是无状态的，但通过 Cookie 的使用可以实现一定程度的状态保持功能。

## HTTPS 是如何防范中间人攻击的

主要通过加密和身份校验来防范中间人攻击

- 加密：HTTPS 握手期间会通过非对称加密的方式来协商出对称加密密钥。
- 身份校验：服务器会向证书颁发机构 CA 申请数字证书，证书中包含了服务器的公钥和其他相关信息。当客户端与服务器建立连接时，服务器会将证书发送给客户端。客户端会验证证书的合法性，包括检查证书的有效期、颁发机构 CA 的信任等。如果验证通过，客户端会使用证书中服务器的公钥来加密通信数据，并将加密后的数据发送给服务器，然后由服务端用私钥解密。

中间人攻击的关键在于攻击者冒充服务器与客户端建立连接，并同时与服务器建立连接。但由于攻击者无法获得服务器的私钥，因此无法正确解密客户端发送的加密数据。同时，客户端会在建立连接时验证服务器的证书，如果证书验证失败或存在问题，客户端会发出警告或中止连接。



## HTTP、SOCKET 和 TCP 的区别

HTTP 是应用层协议，定义了客户端和服务器之间交换的数据格式和规则；Socket是通信的一端，提供了
网络通信的接口；TCP是传输层协议，负责在网络中建立可靠的数据传输连接。

- HTTP 是一种用于传输超文本数据的应用层协议，用于在客户端和服务器之间传输和显示 Web 页面。
- Socket 是计算机网络中的一种抽象，用于描述通信链路的一端，提供了底层的通信接口，可实现不同计
  算机之间的数据交换。
- TCP 是一种面向连接的、可靠的传输层协议，负责在通信的两端之间建立可靠的数据传输连接。



## Cookie 和 Session 有什么区别?

Cookie 和 Session 都是 Web 开发中用于跟踪用户状态的技术，但在存储位置、数据容量、安全性以及生命周期等方面存在显著差异：

- **存储位置：**Cookie的数据存储在客户端（通常是浏览器）。当浏览器向服务器发送请求时，会自动附带Cookie中的数据。Session的数据存储在服务器端。服务器为每个用户分配一个唯一的SessionID，这个ID通常通过Cookie或URL重写的方式发送给客户端，客户端后续的请求会带上这个SessionID，服务器根据ID查找对应的Session数据。
- **数据容量：**单个Cookie的大小限制通常在4KB左右，而且大多数浏览器对每个域名的总Cookie数量也有限制。由于Session存储在服务器上，理论上不受数据大小的限制，主要受限于服务器的内存大小。
- **安全性：**Cookie相对不安全，因为数据存储在客户端，容易受到XSS（跨站脚本攻击）的威胁。不过，可以通过设置HttpOnly属性来防止JavaScript访问，减少XSS攻击的风险，但仍然可能受到CSRF（跨站请求伪造）的攻击。Session通常认为比Cookie更安全，因为敏感数据存储在服务器端。但仍然需要防范Session劫持（通过获取他人的SessionID）和会话固定攻击。
- **生命周期：**Cookie可以设置过期时间，过期后自动删除。也可以设置为会话Cookie，即浏览器关闭时自动删除。Session在默认情况下，当用户关闭浏览器时，Session结束。但服务器也可以设置Session的超时时间，超过这个时间未活动，Session也会失效。
- **性能：**使用Cookie时，因为数据随每个请求发送到服务器，可能会影响网络传输效率，尤其是在Cookie数据较大时。使用Session时，因为数据存储在服务器端，每次请求都需要查询服务器上的Session数据，这可能会增加服务器的负载，特别是在高并发场景下。



## Token，Session，Cookie的区别?

- Session存储于服务器，可以理解为一个状态列表，拥有一个唯一识别符号sessionld，通常存放于cookie中。服务器收到cookie后解析出sessionld，再去session列表中查找，才能找到相应session，依赖cookie。
- Cookie类似一个令牌，装有sessionld，存储在客户端，浏览器通常会自动添加。
- Token也类似一个令牌，无状态，用户信息都被加密到token中，服务器收到token后解密就可知道是哪个用户，需要开发者手动添加。



## 如果客户端禁用了Cookie，Session还能用吗?

默认情况下禁用 Cookie 后，Session 是无法正常使用的，因为大多数 Web 服务器都是依赖于 Cookie 来传递Session 的会话ID 的。

客户端浏览器禁用 Cookie 时，服务器将无法把会话 ID 发送给客户端，客户端也无法在后续请求中携带会话 ID 返回给服务器，从而导致服务器无法识别用户会话。

但有几种方法可以绕过这个问题，尽管它们可能会引入额外的复杂性和/或降低用户体验：

- **URL重写：**每当服务器响应需要保持状态的请求时，将 SessionID 附加到URL中作为参数。

  缺点是 URL 变得不整洁，且可能导致 SessionID 泄露。

- **隐藏表单字段：**在需要 Session 信息的 HTML 表单中包含一个隐藏字段，用来存储 SessionID。表单提交时，SessionID 随表单数据一起发送回服务器，服务器通过解析表单数据中的 SessionID 来获取用户的会话状态。

  这种方法仅适用于通过表单提交的交互模式，不适合链接点击或Ajax请求。



## 把数据存到 LocalStorage 和 Cookie 有什么区别

- 存储容量：Cookie的存储容量通常较小每个Cookie的大小限制在几KB左右。而LocalStorage的存储
  容量通常较大，一般限制在几MB左右。因此,如果需要存储大量数据，LocalStorage通常更适合；
- 数据发送：Cookie在每次HTTP请求中都会自动发送到服务器，这使得Cookie适合用于在客户端和服务
  器之间传递数据。而localStorage的数据不会自动发送到服务器，它仅在浏览器端存储数据，因此
  LocalStorage适合用于在同一域名下的不同页面之间共享数据；
- 生命周期：Cookie可以设置一个过期时间，使得数据在指定时间后自动过期。而LocalStorage的数据将
  永久存储在浏览器中，除非通过JavaScript代码手动删除；
- 安全性：Cookie的安全性较低，因为Cookie在每次HTTP请求中都会自动发送到服务器，存在被窃取或
  篡改的风险。而LocalStorage的数据仅在浏览器端存储，不会自动发送到服务器，相对而言更安全一些；

Cookie 适合用于在客户端和服务器之间传递数据、跨域访问和设置过期时间，而 LocalStorage 适合用于
在同一域名下的不同页面之间共享数据、存储大量数据和永久存储数据。



## Nginx有哪些负载均衡算法?

Nginx支持的负载均衡算法包括：

- 轮询：按照顺序依次将请求分配给后端服务器。这种算法最简单，但是也无法处理某个节点变慢或者客
  户端操作有连续性的情况。
- IP哈希：根据客户端IP地址的哈希值来确定分配请求的后端服务器。适用于需要保持同一客户端的请求
  始终发送到同一台后端服务器的场景，如会话保持。
- URL哈希：按访问的URL的哈希结果来分配请求，使每个URL定向到一台后端服务器，可以进一步提高
  后端缓存服务器的效率。
- 最短响应时间：按照后端服务器的响应时间来分配请求，响应时间短的优先分配。适用于后端服务器性
  能不均的场景，能够将请求发送到响应时间快的服务器，实现负载均衡。
- 加权轮询：按照权重分配请求给后端服务器，权重越高的服务器获得更多的请求。适用于后端服务器性
  能不同的场景，可以根据服务器权重分配请求，提高高性能服务器的利用率。

Nginx 位于七层网络结构中的应用层，Nginx 是七层负载均衡。



# DNS

**DNS（Domain Name System）域名系统**是互联网的一项核心服务，作用是解决**域名和 IP 地址的映射问题**。

即将人类可读的域名（如 www.baidu.com）和计算机用于通信的 IP 地址（如 93.184.216.34）进行转换映射。

**DNS 是应用层协议，基于 UDP 协议之上，默认端口为 53** 。

**为什么需要 DNS？**

- IP 地址难记：计算机通过 IP 地址互相通信，但对人来说难以记忆。
- 域名更友好：google.com 比 IP 地址更容易使用和传播。
- 解耦与灵活性：网站可以更换服务器 IP，而用户只需继续使用原域名，无需感知底层变化。



![TCP/IP 各层协议概览](https://oss.javaguide.cn/github/javaguide/cs-basics/network/network-protocol-overview.png)

## [DNS 服务器](#dns-服务器)

DNS 服务器自底向上可以依次分为以下几个层级(所有 DNS 服务器都属于以下四个类别之一)：

- 根 DNS 服务器。**根 DNS 服务器提供 TLD 服务器的 IP 地址**。目前世界上只有 13 组根服务器，我国境内目前仍没有根服务器。
- 顶级域 DNS 服务器 (**Top-Level Domain DNS Server**，TLD服务器)。顶级域是指域名的后缀，如`com`、`org`、`edu`等。国家也有自己的顶级域，如`uk`、`fr`和`ca`。**TLD 服务器提供了权威 DNS 服务器的 IP 地址**。
- 权威 DNS 服务器 (**Authoritative DNS Server**)。**真正存储域名与 IP 地址映射关系**的服务器。
- 递归 DNS 服务器（Recursive DNS Server）。起代理作用，将 DNS 请求转发到 DNS 层次结构中。

DNS 中的域名是用句点来分隔的，比如 www.google.com，代表了不同层次之间的界限。

在域名中，越靠右的位置表示其层级越高。实际上域名的最后还有一个点，代表根域名。

世界上并不只有 13 台根 DNS 服务器，而是 13 组根 DNS 服务器。最初确实只为 DNS 根服务器分配了 13 个 IP 地址，每个 IP 地址对应一个不同的根 DNS 服务器。由于互联网的快速发展和增长，为了提高 DNS 的可靠性、安全性和性能，目前这 13 个 IP 地址中，每一个都有多台根 DNS 服务器。

截止到 2023 年底，所有根服务器之和达到了 600 多台，未来还会继续增加。

## [DNS 工作流程](https://javaguide.cn/cs-basics/network/dns.html#dns-%E5%B7%A5%E4%BD%9C%E6%B5%81%E7%A8%8B)

DNS 工作流程示例：当在浏览器输入 `https://www.google.com` 并回车时，会发生以下过程：

1. **本地缓存检查**
   浏览器、操作系统会先查本地是否有该域名对应的 IP 缓存（比如最近访问过）。
2. **向递归 DNS 服务器查询**
   如果本地没有，请求会发送给 **递归 DNS 服务器**。
3. 递归 DNS 服务器逐级查询
   - **查询根 DNS 服务器**：返回顶级域（TLD）服务器的 IP 地址，即 `.com` 。
   - **查询 TLD 服务器**：返回权威 DNS 服务器的 IP 地址，即 `google.com` 。
   - **查询权威 DNS 服务器**：返回 `www.google.com` 对应的 IP 地址。
4. **返回结果并缓存**
   递归 DNS 服务器将 IP 地址返回给浏览器，同时可能缓存一段时间（由 TTL 值决定），下次查询可直接使用。

> 这个过程通常在几十毫秒内完成。

另外，DNS 的缓存位于本地 DNS 服务器。由于全世界的根服务器很少，只有 600 多台，分为 13 组，且顶级域的数量也在一个可数的范围内，因此本地 DNS 通常已经缓存了很多 TLD DNS 服务器，所以在实际查找过程中，无需访问根服务器，这样可以提高 DNS 查询的效率和速度，减少对根服务器和 TLD 服务器的负担。



## DNS的底层使用TCP还是UDP

DNS 基于 UDP 进行域名解析和数据传输。

UDP 能够提供低延迟、简单快速、轻量级的特性，更适合 DNS 这种需要快速响应的域名解析服务。

- 低延迟：UDP 是一种无连接的协议，不需要在数据传输前建立连接，可以减少传输时延，适合DNS这种需要快速响应的应用场景。
- 简单快速：UDP 相比于 TCP 更简单，没有 TCP 的连接管理和流量控制机制，传输效率更高，适合 DNS 这
  种需要快速传输数据的场景。
- 轻量级：UDP头部较小，占用较少的网络资源，对于小型请求和响应来说更加轻量级，适合DNS这种频繁且短小的数据交换。

尽管 UDP 存在丢包和数据包损坏的风险，但 DNS 通过查询超时重传、请求重试、缓存等机制保证了数据传输的可靠性和正确性。



# TCP

TCP（Transmission Control Protocol）传输控制协议，是一种**面向连接**的可靠传输层协议。**可靠**体现在：按序交付、差错检测、丢包重传、流量控制与拥塞控制等。

## TCP 的头部

![img](https://cdn.xiaolincoding.com//picgo/1718240465754-594d5aab-cb68-408a-b228-70fd33b094f4.png)

**序列号：**在建立连接时由计算机生成的随机数作为其初始值，通过 SYN 包传给接收端主机，每发送一次数据，就**累加**一次该**数据**的大小，**用来解决网络包乱序问题。**

**确认应答号：**指下一次**期望**收到的数据序列号，发送端收到这个确认应答以后可以认为在这个序号以前的数据都已经被正常接收，**用来解决丢包问题。**

**控制位：**

- ACK：该位为1时，确认应答号字段变为有效，TCP 规定除了最初建立连接时的 SYN 包之外该位必须设置为1
- RST：该位为1时，表示 TCP 连接中出现异常，必须强制断开连接。
- SYN：该位为1时，表示希望建立连接，并在序列号字段设置初始值。
- FIN：该位为1时，表示希望断开连接。。



为了在不可靠的网络上建立一条逻辑可靠的端到端连接，TCP 在传输数据前必须先完成连接的建立过程，即**三次握手（Three-way Handshake）**。

## [⭐️三次握手](#建立连接-tcp-三次握手)

1. **第一次握手 (SYN)**：客户端向服务器**发送**一个 SYN（Synchronize Sequence Numbers）报文段，其中包含一个由客户端随机生成的**初始序列号**（Initial Sequence Number, ISN），例如 seq=x。

   发送报文段后，客户端进入 **SYN_SENT** 状态，等待服务器的确认。

2. 第二次握手 **(SYN+ACK)**：服务器收到 SYN 报文段后，如果同意建立连接，会向客户端**回复**一个确认报文段。该报文段包含两个关键信息： 

   - **SYN**：服务器也需要同步自己的初始序列号，因此报文段中也包含一个由服务器随机生成的**初始序列号**，例如 seq=y。
   - **ACK** (Acknowledgement)：用于确认服务器收到了客户端的请求，其**确认号**被设置为客户端初始序列号加一，即 ack=x+1。

   发送报文段后，服务器进入 **SYN_RCVD** （也称 SYN_RECEIVED）状态。

3. **第三次握手 (ACK)**：客户端收到服务器的 **SYN+ACK** 报文段后，会向服务端发送一个**最终的确认报文段**。该报文段包含确认号 ack=y+1。发送后，客户端进入 **ESTABLISHED** 状态。服务器收到这个 ACK 报文段后，也进入 **ESTABLISHED** 状态。

至此，双方都确认了连接的建立，TCP 连接成功创建，可以开始进行双向数据传输。

三次握手发送的报文段分别为：`SYN=1, seq=x`、`SYN=1, ACK=1, seq=y, ack=x+1`、`ACK=1, seq=x+1, ack=y+1`

TCP 三次握手的核心目的是为了在客户端和服务器之间建立一个**可靠的**、**全双工的**通信信道。

## [半连接队列和全连接队列](#什么是半连接队列和全连接队列)

在 TCP 三次握手过程中，服务器内核通常会**用两个队列来管理连接请求**：

1. 半连接队列（也称 SYN Queue）： 
   - 保存三次握手未完成的请求：服务器收到 SYN 并发送 SYN+ACK 后进入 `SYN_RCVD ` 状态，等待客户端最终 ACK。
   - 如果一直收不到 ACK，服务器内核会按重传策略重发 SYN+ACK，最终超时清理。
   - 参数：`net.ipv4.tcp_max_syn_backlog`；在 SYN Flood 场景下可配合 `net.ipv4.tcp_syncookies`
2. 全连接队列（也称 Accept Queue）： 
   - 保存三次握手已完成但应用还没 accept 的连接：服务器收到最终 ACK 后进入 `ESTABLISHED` 状态，并进入全连接队列，等待**应用层** `accept()` 取走。
   - 队列容量受 `listen(fd, backlog)` 与系统上限 `net.core.somaxconn` 共同影响；实践中常见有效上限近似为 `min(backlog, somaxconn)`（具体行为与内核版本相关）。

总结：

| 队列                       | 作用                 | 连接状态    | 移出条件                    |
| -------------------------- | -------------------- | ----------- | --------------------------- |
| 半连接队列（SYN Queue）    | 保存未完成握手的连接 | SYN_RCVD    | 收到最终 ACK / 超时重传失败 |
| 全连接队列（Accept Queue） | 保存已完成握手的连接 | ESTABLISHED | 被应用层 accept() 取出      |

当全连接队列满时，`net.ipv4.tcp_abort_on_overflow` 会影响处理策略：

- `0`（默认）：通常不会让待加入的连接快速失败，给应用留缓冲时间（可能表现为客户端重试/超时）。
- `1`：直接对客户端回复 `RST`，让待加入的连接快速失败。

当半连接队列满时，如果开启了 `tcp_syncookies`，服务器不会丢弃后续的 SYN 包，而是计算一个 **SYN Cookie**，并放在第二次握手报文的序列号中返回给客户端，只有收到客户端的合法 `ACK` 时，才将该连接放入全连接队列中。这是抵御 **SYN Flood** 攻击的核心手段之一。

## [为什么要三次握手?](#为什么要三次握手)

**1. 确认双方的收发能力，并同步初始序列号 (ISN)**

- TCP 依赖序列号（SEQ）与确认号（ACK）保证数据**有序、无重复、可重传**。
- 三次握手通过交换并确认双方的 ISN，使两端对**从哪个序号开始收发数据**达成一致，同时让握手过程形成闭环，避免仅凭单向信息就进入连接已建立状态。
- 经过三次交互，双方都确认了彼此的收发功能完好，并完成了初始序列号的同步，为后续的可靠数据传输奠定了基础

三次握手分别证明客户端能发、服务器能收也能发、客户端能收。

**2. 防止已失效的连接请求被错误地建立**

设想一个场景：客户端发送的第一个连接请求（SYN1）因网络延迟而滞留，于是客户端重发了第二个请求（SYN2）并成功建立了连接，数据传输完毕后连接被释放。此时，延迟的 SYN1 才到达服务端。

- **如果是两次握手**：服务器收到失效的 SYN1 后，可能会误认为是一个新的连接请求，会立即分配资源、建立连接。但这将导致服务器单方面维持一个无效连接，白白浪费系统资源，因为客户端并不会有任何响应。
- **如果是三次握手**：服务器收到失效的 SYN1 并回复 SYN+ACK 后，会等待客户端的最终确认（ACK）。由于客户端认为这是失效的连接请求，它会忽略这个 SYN+ACK 或者发送一个 RST (Reset) 报文。这样，服务器就无法收到第三次握手的 ACK，最终会因为超时而关闭这个错误的连接，避免了资源浪费。

因此，三次握手是确保 TCP 连接可靠性的**最少且必需**的步骤。它不仅确认了双方的通信能力，更通过最终确认环节，防止因为网络延迟等原因的失效请求浪费系统资源。

## [第二次握手为什么要传SYN](#第-2-次握手传回了-ack-为什么还要传回-syn)

第二次握手里的 ACK 是为了确认服务器收到了客户端的 SYN，而携带 SYN 是为了把服务器自己的 ISN 也同步给客户端，并要求客户端对其进行确认。只有双方的 ISN 都同步完成，后续的可靠传输才有共同起点。

## 客户端发送的 SYN 报文丢了怎么办

当客户端想和服务器建立 TCP 连接的时候，会发送 SYN 报文，并进入 SYN_SENT 状态。

如果 SYN 报文丢了，那么服务器就不会回 SYN-ACK 报文给客户端，客户端迟迟收不到服务端的 SYN-ACK 报文，就会触发**超时重传**机制，重传 SYN 报文，而且重传的 SYN 报文的序列号都是一样的。

不同版本操作系统的超时时间可能不同，有的 1 秒，有的 3 秒，这个超时时间是写死在内核里的，如
果想要更改则需要重新编译内核，比较麻烦。

如果客户端在 1 秒后没有收到服务端的 SYN-ACK 报文，就会重发 SYN 报文。

第一次握手的最大重传次数由 `tcp_syn_retries` 参数控制，默认值一般是 5。

通常第一次超时重传在 1 秒后，第二次超时重传在 2 秒后，...，每次超时的时间是上一次的2倍。
当第五次超时重传后，会继续等待 32 秒，如果服务端仍然没有回应 ACK，客户端就不会再发送 SYN 包并断开 TCP 连接。所以总耗时是 1+2+4+8+16+32 = 63 秒，大约1分钟左右。

## 服务器发送的 SYN+ACK 报文丢了怎么办

当服务器收到客户端的 SYN 报文后，就会回 SYN-ACK 报文给客户端，并进入 SYN_RCVD 状态。

如果 SYN-ACK 报文丢了，那么客户端会迟迟收不到服务器的 SYN-ACK 报文，就会触发**超时重传**机制，重传 SYN 报文，直到收到 SYN-ACK 或者达到最大重传次数。

同时服务器也会迟迟收不到客户端的 ACK 报文，所以服务器这边也会触发**超时重传**机制，重传 SYN-ACK 报文。

第二次握手的最大重传次数由 `tcp_synack_ retries` 参数控制，超过最大重传次数后，会再等待上一次 RTO 的 2 倍时间，如果还是没有收到 ACK，就断开 TCP 连接。

## 客户端发送的 ACK 丢了怎么办

当客户端收到服务器的 SYN-ACK 报文后，就会回 ACK 报文给服务器，并进入 ESTABLISHED 状态

如果 ACK 报文丢了，那么服务器会迟迟收不到 ACK，会触发服务器的**超时重传**机制，重传 SYN-ACK 报文，直到收到 ACK 或者达到最大重传次数。

## accept()方法做了什么事情

TCP 完成三次握手后，连接会被保存到内核的全连接队列，调用 accpet() 方法就是从把连接从全连接队列中取出来给用户程序使用。

## 大量 SYN 包发给服务器会发生什么

**SYN Flood 攻击**是一种常见的 **DDoS（分布式拒绝服务）攻击**，利用 TCP 三次握手的漏洞：

- 攻击者**发送大量伪造源 IP 的 SYN 报文**给服务器；
- 服务器回复 SYN-ACK 并等待 ACK，但攻击者永不回应；
- 导致服务器**堆积大量处于 `SYN_RECEIVED` 状态的半开连接**，耗尽内存或半连接队列资源；
- 而正常用户无法建立新连接，最终**服务瘫痪**。

防御方法：

1. SYN Cookie：服务器不立即分配资源，而是通过加密算法生成 Cookie 作为初始序列号；收到 ACK 并验证后才建立连接，**无需保存半开状态**（Linux 默认支持，参数`net.ipv4.tcp_syncookies`）。
2. 限制半开连接数：设置 `net.ipv4.tcp_max_syn_backlog`（Linux）等参数，超过则丢弃新 SYN。
3. 增大半连接队列容量：
4. 缩短 SYN 超时时间：快速清理处于 SYN_REVC 状态的连接（如 `net.ipv4.tcp_synack_retries=2`）。
5. 防火墙/IPS 过滤：识别异常流量（如单一 IP 短时大量 SYN），自动拦截。

## [三次握手过程中可以携带数据吗？](#三次握手过程中可以携带数据吗)

在 TCP 三次握手中，第三次握手是可以携带数据的。客户端发送完 ACK 确认报文之后就进入 `ESTABLISHED` 状态了，根据 **RFC 793** ，第三次 ACK 报文可以携带应用层数据，如 HTTP 请求。

RFC 793 是 TCP 协议的原始官方规范。

而且直到第二次握手完成客户端才知道服务器具有接收能力，因此前两次握手不可以携带数据。

## [⭐️四次挥手](#断开连接-tcp-四次挥手)

1. **第一次挥手 (FIN)**：当客户端决定关闭连接时，它会向服务器发送一个 **FIN**（Finish）报文段，表示自己已经没有数据要发送了。该报文段包含一个**序列号** **seq=u**。发送后，客户端进入 **FIN-WAIT-1** 状态。
2. **第二次挥手 (ACK)**：当服务器收到 **FIN** 报文段后，会立即回复一个 **ACK** 确认报文段，**确认号**为 **ack=u+1**。发送后，服务端进入 **CLOSE-WAIT** 状态。客户端收到 **ACK** 后，进入 **FIN-WAIT-2** 状态。此时 TCP 连接处于**半关闭（Half-Close）**状态：客户端到服务器的发送通道已关闭，但服务器到客户端的发送通道仍可以传输数据
3. **第三次挥手 (FIN)**：当服务器确认所有数据都已发送完毕后，它也会向客户端发送一个 **FIN** 报文段，表示自己也准备关闭连接，包含一个**序列号** **seq=y**。发送后，服务端进入 **LAST-ACK** 状态，等待客户端的最终确认。
4. **第四次挥手**：客户端收到服务器的 **FIN** 报文段后，会回复一个最终的 **ACK** 确认报文段，**确认号**为 **ack=y+1**。发送后，客户端进入 **TIME-WAIT** 状态。服务端收到 **ACK** 后，立即进入 **CLOSED** 状态，完成连接关闭。客户端则会在 **TIME-WAIT** 状态下等待 **2MSL**（Maximum Segment Lifetime，报文段最大生存时间）后，才最终进入 **CLOSED** 状态。

四次挥手期间连接可能处于**半关闭（Half-Close）**状态：**先发送 FIN 的一方不再发送应用数据**，但**另一方仍可继续发送剩余数据**，直到它也发送 **FIN** 并完成后续 **ACK**。

## [为什么要四次挥手](#为什么要四次挥手)

TCP 是全双工通信：客户端和服务器的发送彼此独立。断开连接时，需要**停止发送消息的信号**都分别被对方确认，通常表现为四个报文段（FIN/ACK/FIN/ACK）。这也对应了现实世界的“双方分别确认挂断”的过程。

例如：A 和 B 打电话，通话准备结束时。

1. **第一次挥手**：A 结束通话（A 发 FIN）
2. **第二次挥手**：B 回复收到，但是 B 可能还没说完（B 回 ACK，但可能还有话要说）
3. **第三次挥手**：B 说完后结束通话（B 发 FIN）
4. **第四次挥手**：A 回复收到，这样通话才算结束（A 回 ACK）。

## [中间两次挥手能合并变成三次挥手吗](#为什么不能把服务端发送的-ack-和-fin-合并起来-变成三次挥手)

因为服务器 **回复 ACK** 与 **发送 FIN** 的时机往往不同步。

- 当服务器收到客户端的 **FIN** 时，内核协议栈**会立即回 ACK**，用于确认“我收到了你要关闭的请求”。此时服务器进入 **CLOSE_WAIT**，等待服务器把剩余事情处理完。
- 只有当服务器应用处理完毕并调用 `close()/shutdown()` 后，内核才会发送服务器的 **FIN**。
- 只有在服务器恰好也准备立即关闭时，才可能出现 **FIN+ACK** 合并在一个报文段中的情况。

## 客户端 FIN 报文丢失，服务器的状态是什么

客户端向服务器发送 FIN 报文后进入到 FIN_WAIT_1 状态。

如果 FIN 丢失，那么客户端迟迟收不到服务器的 ACK，会触发超时重传机制，重传 FIN报文，如果超过最大重传次数还未收到，那么客户端直接进入到 close 状态，而服务端始终是 ESTABLISHED 状态

## [第二次挥手的 ACK 丢了怎么办](#如果第二次挥手时服务端的-ack-没有送达客户端-会怎样)

- **客户端状态**：客户端发送第一次 `FIN` 后进入 **FIN_WAIT_1** 状态，并启动重传计时器。
- **重传逻辑**：如果客户端在超时时间内没有收到服务器对该 `FIN` 的确认 `ACK`，客户端会重传 `FIN`。
- **服务端处理**：服务器如果收到重复 `FIN`，通常会再次发送 `ACK`。但如果由于网络问题 **ACK** 一直到不了客户端，那么客户端回会在达到一定重试/超时阈值后报错或放弃。

## 第三次挥手一直没发会发生什么

当客户端收到 ACK 报文后会处于 FIN_WAIT_2 状态，表示客户端的发送通道已关闭，等待服务器发送 FIN 报文关闭发送通道。

如果连接是用 shutdown 函数关闭的，那么连接可以一直处于 FIN_WAIT_2 状态，可以发送 ACK 或接收数据。

如果连接是用 close 函数关闭的，称为孤儿连接，无法再发送 ACK 和接收数据，这个状态不可以持续太久。

`tcp_fin_timeout` 参数控制了孤儿连接状态的持续时长，默认是 60 秒，意味着对于孤儿连接，如果在 60 秒后还没有收到 FIN 报文，TCP 连接就会直接关闭。

## 挥手期间主动先断开的那端能干什么

如果主动断开的一方是调用的 shutdown 函数来关闭连接，并且只选择关闭发送能力，没有关闭接收能力的话，那么主动断开的一方还可以接收数据并发送 ACK。

如果连接是用 close 函数关闭的，则无法再发送 ACK 和接收数据，并且如果在 60 秒后还没有收到 FIN 报文，TCP 连接就会直接关闭。

## 服务器出现大量的 timewait 是什么原因

以下场景服务器会主动断开连接：HTTP 没有使用长连接、HTTP 长连接超时、HTTP 长连接的请求数量达到上限

**HTTP 没有使用长连接**

HTTP长连接（Keep-Alive）机制在 HTTP/1.0 中默认是关闭的，如果浏览器要开启 Keep-Alive，必须在请求的请求头 header 中添加：`Connection: Keep-Alive`

当服务器收到请求并作出回应的时候，也必须在响应的响应头 header 中添加：`Connection: Keep-Alive`

这样 TCP 连接才不会中断，当客户端发起另一个请求时会使用同一个 TCP 连接，持续到客户端或服务器提出断开连接。

从 HTTP/1.1 开始默认开启了 Keep-Alive，现在大多数浏览器都默认是使用 HTTP/1.1。

如果要关闭HTTP Keep-Alive，需要在 HTTP 请求或者响应的 header 里添加 `Connection:close` 信息，只要客户端和服务器任意一方的 HTTPheader 中有 `Connection:close` 信息，那么就无法使用 HTTP 长连接的机制。

关闭 HTTP 长连接机制后，每次请求都是 HTTP 短连接

请求和响应的双方都可以主动关闭 TCP 连接，不过根据大多数 Web 服务的实现，不管哪一方禁用了 HTTP Keep-Alive，都是由服务器主动关闭连接，**此时服务器就会出现 TIME_WAIT 状态**。

**HTTP 长连接超时**

HTTP 长连接的特点是，只要任意一端没有明确提出断开连接，则保持 TCP 连接状态。

为了避免资源浪费的情况，web 服务一般都会提供一个参数来指定 HTTP 长连接的超时时间，比如 nginx 提供的 keepalive_timeout 参数。

如果设置了 HTTP 长连接的超时时间为 60 秒，nginx 就会启动一个定时器，当客户端在完后一个 HTTP 请求后在 60 秒内都没有再发起新的请求，nginx 就会触发回调函数来关闭连接，**此时服务器就会出现TIME_WAIT状态。**

当服务器出现大量 TIME_WAIT 状态的连接时，如果现象是有大量的客户端建立完 TCP 连接后很长一段时间没有发送数据，那么大概率是因为 HTTP 长连接超时导致服务器主动关闭连接，产生大量处于TIME_WAIT状态的连接。

可以往网络问题的方向去排查，比如是否是因为网络问题导致客户端发送的数据一直没有被服务端接收。

**HTTP 长连接的请求数量达到上限**

Web 服务器有个参数来定义一条 HTTP 长连接上能处理的最大请求数量，当超过最大限制时就会主动关闭连接。

比如 nginx 的 keepalive_requests 参数用来记录 HTTP 长连接上已经接收并处理的客户端请求的数量。如果达到这个参数设置的最大值时，则 nginx 会主动关闭这个长连接，**此时服务器就会出现TIME_WAIT状态**。

keepalive_requests 参数的默认值是 100，意味着每个 HTTP 长连接最多只能跑 100 次请求，对于一些 QPS 比较高的场景，比如超过 10000 QPS，此时 nginx 就会频繁地关闭连接，**导致服务器就会出大量的 TIME_WAIT 状态**

针对这个场景下的解决方式也很简单，调大 nginx 的 keepalive_requests 参数就行。

## tcp粘包怎么解决?

粘包是指不知道用户消息的边界在哪，如果知道了边界在哪，接收方就可以通过边界来划分出有效的用户消息。

一般有三种分包的方式：固定长度消息；特殊字符作为边界；自定义消息结构。

**固定长度消息：**是最简单的方法，每个用户消息都是固定长度的，比如规定一个消息的长度是 64 个字节，当接收方收满 64 个字节后，就认为这个内容是一个完整且有效的消息。但是这种方式灵活性不高，实际中很少用。

**特殊字符作为边界：**可以在两个用户消息之间插入一个特殊的字符，这样接收方在接收数据时读到了这个特殊字符，就认为已经读完一个完整的消息。HTTP 设置回车、换行符作为 HTTP 报文协议的边界。

如果消息内容里刚好有这个作为边界点的特殊字符，要对这个字符进行转义，避免被接收方当作消息的边界点。

**自定义消息结构：**可以自定义一个消息结构，由包头和数据组成，包头是固定大小的，包含一个字段来说明紧随其后的数据有多大。

## [为什么第四次挥手后要等待 2MSL？](#为什么第四次挥手客户端需要等待-2-msl-报文段最长寿命-时间后才进入-closed-状态)

第四次挥手时，客户端发送给服务器的 **ACK** 有可能丢失，如果服务器因为某些原因没有收到 **ACK** 的话，会重发 **FIN**，如果客户端在 **2*MSL** 的时间内再次收到了 **FIN**，就会重新发送 **ACK** 并再次等待 2MSL，防止服务器没有收到 **ACK** 时不断重发 **FIN**。

**MSL(Maximum Segment Lifetime)** 是一个报文段在网络中的最大存活时间，2MSL 就是一次发送和一次回复的最大时间。如果直到 2MSL 客户端都没有再次收到 **FIN**，那么客户端会认为 **ACK** 已被成功接收并结束 TCP 连接。

# [TCP 与 UDP](#tcp-与-udp)

## [TCP 与 UDP 区别](#⭐️tcp-与-udp-的区别-重要)

1. 是否面向连接： 
   - TCP 是面向连接的。在传输数据之前必须先通过三次握手建立连接；数据传输完成后还需要通过四次挥手断开连接。
   - UDP 是无连接的。发送数据前不需要建立任何连接。
2. 是否是可靠传输： 
   - TCP 提供可靠的数据传输。它通过校验和、序列号、确认应答 (ACK)、重传机制、流量控制、拥塞控制等来确保数据不丢失、不重复且按顺序地传输。
   - UDP 提供不可靠的传输。它尽最大努力交付，但不保证数据一定能到达，也不保证到达的顺序，更不会自动重传。收到报文后，接收方也不会主动发确认。
3. 是否有状态： 
   - TCP 是有状态的。因为要保证可靠性，TCP 需要在连接的两端维护连接状态信息，比如序列号、窗口大小、哪些数据发出去了、哪些收到了确认等。
   - UDP 是无状态的。它不维护连接状态，发送方发出数据后不关心它是否到达以及如何到达，开销更小。
4. 传输效率： 
   - TCP 需要建立连接、发送确认、处理重传等，开销较大，传输效率相对较低。
   - UDP 没有复杂的控制机制，开销小，传输效率更高，速度更快。
5. 传输形式： 
   - TCP 面向字节流，将数据视为一连串无结构的字节流，可能会对数据包进行拆分合并。
   - UDP 面向报文。应用程序交给 UDP 多大的数据包，UDP 就照样发送，既不拆分也不合并。
6. 首部开销： 
   - TCP 的头部至少需要 20 字节，如果包含选项字段，最多可达 60 字节。
   - UDP 的头部固定只有 8 字节。
7. 服务对象： 
   - TCP 只支持一对一的两点服务通信。
   - UDP 支持一对一、一对多、多对多的通信方式。

## [什么时候选择 TCP，什么时候选 UDP?](#⭐️什么时候选择-tcp-什么时候选-udp)

选择 TCP 还是 UDP 取决于应用程序**对数据传输的可靠性、实时性和效率的要求**。

当**数据准确性和完整性至关重要，一点都不能出错**时，通常选择 TCP。因为 TCP 提供了一整套机制（三次握手、确认应答、重传、流量控制、四次挥手等）来保证数据能够可靠、有序地送达。典型应用场景如下：

- **Web 浏览 (HTTP/HTTPS)：** 网页内容、图片必须完整加载。
- **文件传输 (FTP, SCP)：**文件内容不允许有任何字节丢失或错序。
- **邮件收发 (SMTP, POP3, IMAP)：** 邮件内容需要完整无误地送达。
- **远程登录 (SSH, Telnet)：** 命令和响应需要准确传输。

当**实时性、速度和效率优先，并且应用能容忍少量数据丢失或乱序**时，通常选择 UDP。UDP 开销小、传输快，没有建立连接和保证可靠性的复杂过程。典型应用场景如下：

- **实时音视频通信 (视频会议, 直播)：** 偶尔丢失一两个数据包（可能导致画面或声音短暂掉帧卡顿）通常比 TCP 等待重传导致长时间的延迟更可接受。应用层可能会有自己的补偿机制。
- **在线游戏：** 需要快速传输玩家位置、状态等信息，对实时性要求极高，丢失少量数据包的影响通常不大。

## [HTTP 基于 TCP 还是 UDP？](#http-基于-tcp-还是-udp)

HTTP/3.0 之前基于 TCP，HTTP/3.0 弃用 TCP，改用 **基于 UDP 的 QUIC 协议**。

QUIC 协议基于 UDP，但它不是裸用 UDP，而是在应用层实现了可靠传输、拥塞控制等 TCP 特性：

- 连接迁移：QUIC 支持在网络变化时快速迁移连接，例如从 Wi-Fi 切换到移动数据，以保持连接的可靠性。
- 重传机制：QUIC 使用重传机制来确保丢失的数据包能够被重新发送，提高数据传输的可靠性。
- 前向纠错：QUIC 使用前向纠错技术，在接收端修复部分丢失的数据，降低重传需求，提高可靠性和传输效率
- 拥塞控制：QUIC 内置了拥塞控制机制，可以根据网络状况动态调整数据传输速率，以避免网络拥塞和丢包。
- 解决**队头阻塞** (Head-of-Line Blocking，HOL blocking) 问题。

# TCP 传输可靠性保障

## [TCP 如何保证传输的可靠性？](#tcp-如何保证传输的可靠性)

1. **校验和** : 发送方会将报文段的首部和数据进行二进制反码求和运算，并将结果存入首部的校验和字段。接收方收到数据后进行同样的计算，如果结果不一致说明数据在传输过程中发生了更改，接收方会直接丢弃该报文并不发送 ACK，从而触发发送方的重传机制。校验和不仅校验数据，还校验源 IP、目的 IP、协议类型等。
2. **序列号与确认应答**：TCP 给每个包一个序列号，接收方通过序列号可以将接收到的数据排序，并去掉重复序列号的数据。接收方收到数据后会返回一个确认报文 `ACK = 收到的最大连续序列号 + 1`，表示已经收到了直到 `ACK-1` 的所有数据，期望下次从 `ACK` 开始接收数据。
3. **重传机制** : **在数据包丢失或延迟的情况下，重新发送数据包，直到收到对方的 ACK。**TCP 重传机制主要有：**超时重传**（基于计时器的重传）、**快速重传**（基于接收端的 ACK 信息来重传）、**SACK**（在快速重传的基础上，返回最近收到的报文段的序列号范围，这样客户端就知道哪些数据包已经到达服务器，自己还需要重传哪些缺失的数据包）、**D-SACK**（重复 SACK，在 SACK 的基础上告知发送方有哪些数据包自己重复接收了）。
4. **流量控制** : **TCP 连接的每一方都有固定大小的缓冲空间，接收端只允许发送端发送接收端缓冲区能接纳的数据。**当接收方来不及处理发送方的数据，能提示发送方降低发送的速率，防止包丢失。TCP 使用的流量控制协议是可变大小的滑动窗口协议（**TCP 利用滑动窗口实现流量控制**）。
5. **拥塞控制** : **当网络拥塞时，减少数据的发送。**TCP 在发送数据的时候，需要考虑两个因素：一是接收方的接收能力，二是网络的拥塞程度。接收方的接收能力由滑动窗口表示，网络的拥塞程度由拥塞窗口表示，它是发送方根据网络状况自己维护的一个值，表示发送方认为可以在网络中传输的数据量。发送方发送数据的大小是接收方滑动窗口和发送方拥塞窗口的最小值，这样可以保证既不会超过接收方的接收能力，也不会造成网络的过度拥塞。

## [超时重传如何实现？超时重传时间怎么确定？](#超时重传如何实现-超时重传时间怎么确定)

发送方发送数据之后启动一个定时器，等待接收端的 ACK，如果在 RTT 时间内未收到 ACK，那么对应的数据包就会被假设为已丢失并进行重传。

- RTT（Round Trip Time）：往返时间，也就是数据包从发出去到收到对应 ACK 的时间。
- RTO（Retransmission Time Out）：重传超时时间，发送数据包后超过这个时间没有收到 ACK 便执行重传。

RTO 直接影响到 TCP 的性能和效率，如果 RTO 设置得太小会导致不必要的重传，增加网络负担；如果 RTO 设置得太大会导致数据传输的延迟，降低吞吐量。应该根据网络的实际状况动态调整 RTO。

TCP 采用了一些算法来动态调整 RTO ，这些算法**是通过对 RTT 的测量和变化来动态估计 RTO 的值。**

## 快速重传怎么实现

快速重传 (Fast Retransmission)：当发送方连续收到 **3 个重复的 ACK** 时，表示接收方收到了乱序包（如收到了 101, 102...但未收到 100），因此会一直发送序列号为 100 的 ACK。此时发送方无需等待超时，可以立即重传。

通过快速重传可以大大减少超时重传的延迟，提高了吞吐量。

## [如何实现流量控制](#tcp-如何实现流量控制)

**TCP 利用滑动窗口实现流量控制，控制发送方发送速率，保证接收方来得及接收。** 接收方发送的 ACK 中的窗口字段可以用来控制发送方的窗口大小，如果将窗口字段设置为 0 则发送方不能发送数据。

TCP 为全双工 (Full-Duplex, FDX) 通信，双方可以进行双向通信，因此客户端和服务端各有一个发送缓冲区与接收缓冲区，两端都各自维护一个发送窗口和一个接收窗口。

## [如何实现拥塞控制](#tcp-的拥塞控制是怎么实现的)

某段时间内若对网络中某一资源的需求超过了该资源所能提供的部分，网络的性能就会变差，这种情况就叫拥塞。

为了进行拥塞控制，TCP 发送方要维护一个 **拥塞窗口(cwnd)** ，大小取决于网络的拥塞程度并且动态变化。发送方的发送窗口取为拥塞窗口和接收方的接受窗口中较小的一个。

TCP 的拥塞控制采用了四种算法：**慢启动**、 **拥塞避免**、**快重传** 和 **快恢复**。

- **慢启动：** 逐渐增大拥塞窗口，初始值为 1，每经过一个传播轮次加倍，指数增长，快速探测网络带宽上限。
- **拥塞避免：** 达到阈值后让拥塞窗口缓慢增大，每经过一个往返时间 RTT 就加 1。
- **快重传与快恢复：**利用重复 ACK 进行快重传，快重传后将拥塞窗口减半并进入拥塞避免阶段，保持较高效率

# 其它协议

## [ARQ](#arq-协议了解吗)

**自动重传请求**（Automatic Repeat-reQuest，ARQ）是数据链路层和传输层的错误纠正协议之一，它通过使用确认和超时机制，在不可靠服务的基础上实现可靠的信息传输。如果发送方在发送后的一段时间内没有收到 ACK，它通常会重新发送，直到收到 ACK 或者达到重试次数。ARQ 包括停止等待 ARQ 协议和连续 ARQ 协议。

[停止等待 ARQ 协议](#停止等待-arq-协议)

停止等待协议是每发完一个分组就停止发送，等待对方的确认 ACK。如果过了一段时间（超时时间后）还没有收到 ACK 确认，说明没有发送成功，需要重新发送，直到收到确认后再发下一个分组；

在停止等待协议中，若接收方收到重复分组，就丢弃该分组，但同时还要发送确认。

[连续 ARQ 协议](#连续-arq-协议)

连续 ARQ 协议是发送方维持一个发送窗口，凡位于发送窗口内的分组可以连续发送出去而不需要等待对方确认。接收方一般采用累计确认，对按序到达的最后一个分组发送确认，表明到这个分组为止的所有分组都已经正确收到



## [NAT](#nat-的作用是什么)

**NAT（Network Address Translation，网络地址转换）** 主要用于在不同网络之间转换 IP 地址。它允许将私有 IP 地址（如在局域网中使用的 IP 地址）映射为公有 IP 地址（在互联网中使用的 IP 地址）或者反向映射，从而实现局域网内的多个设备通过一个公有 IP 地址访问互联网。



## [ARP](#arp)

[什么是 Mac 地址](#什么是-mac-地址)

MAC 地址的全称是 **媒体访问控制地址（Media Access Control Address）**。

可以将 MAC 地址理解为一个网络设备真正的身份证号，而 IP 地址只是一种不重复的定位方式（IP 地址是逻辑定位，**MAC 地址才是真正的物理地址**）。MAC 地址有一些别称，如 LAN 地址、物理地址、以太网地址等。

[ARP 协议解决了什么问题](#⭐️arp-协议解决了什么问题)

ARP 全称 **地址解析协议（Address Resolution Protocol）**。

ARP 解决的是**网络层**地址和**数据链路层**地址之间的转换问题，即 IP 地址和 MAC 地址的转换问题。

过程：

1. 主机 A 知道主机 B 的 IP 地址 IP_B，想与主机 B 通信但不知道主机 B 的MAC 地址 MAC_B。
2. 主机 A 在局域网内**广播** **ARP Request** 包：“谁是 IP_B？你的 MAC_B 是多少？请告诉 MAC_A ”。
3. 局域网内所有主机都收到，只有主机 B 识别出 IP_B，同时将 IP_A 和 MAC_A 存入主机 B 的本地 **ARP 缓存表**
4. 主机 B **单播**回复 **ARP Reply** 包：“我是 IP_B，我的 MAC 地址是 MAC_B”。
5. 主机 A 将 IP_B 和 MAC_B 存入主机 A 的本地 **ARP 缓存表**，后续通信直接使用。