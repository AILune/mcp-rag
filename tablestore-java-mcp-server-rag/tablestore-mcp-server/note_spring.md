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



1. 