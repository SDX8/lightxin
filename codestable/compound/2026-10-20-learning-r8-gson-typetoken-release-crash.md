---
doc_type: learning
track: pitfall
severity: high
component: [proguard, gson, schedule]
title: R8 剥掉匿名 TypeToken 的泛型签名，release 包启动即秒闪退
date: 2026-10-20
tags: [proguard, r8, gson, typetoken, release, crash, signature]
---

# R8 剥掉匿名 TypeToken 的泛型签名 → release 秒闪退

## 现象

- Debug 包（模拟器、实体机）一切正常，课表 / 早自习晚自习设置都能用
- Release 包 1.3.1、1.3.2 在实体机上**点图标秒闪退**，连首页都没画出来
- 排查卡了两天：本地 `lintVitalRelease` 0 Fatal、APK 签名/ABI/versionCode 全对，静态查不出来

## 崩溃栈（实体机 crash buffer）

```
java.lang.ExceptionInInitializerError
    at com.lightxin.feature.schedule.data.ScheduleRepository.<clinit>
    at com.lightxin.MainActivity.onCreate
Caused by: java.lang.IllegalStateException: TypeToken must be created with a type argument:
    new TypeToken<...>() {}; When using code shrinkers (ProGuard, R8, ...) make sure that
    generic signatures are preserved.
    at com.lightxin.feature.schedule.data.SpecialSectionStore$Companion$SETTINGS_TYPE$1.<init>
    at com.lightxin.feature.schedule.data.SpecialSectionStore.<clinit>
```

## 根因

`SpecialSectionStore.kt:97` 的 `object : TypeToken<List<SpecialItem>>() {}.type` 靠**匿名子类的泛型父类签名**工作。R8 给这个匿名类改名后把 `Signature` 属性优化掉了，运行时 `getGenericSuperclass()` 拿不到类型参数就抛异常。

三个放大杀伤力的细节：

1. 它在 `companion object` 的 `<clinit>` 里，**类第一次被加载就炸**，不是用到才炸 —— `ScheduleRepository` 静态引用它，于是 `MainActivity.onCreate` 直接死，表现为"图标点不开"
2. `-keepattributes Signature` **单独不够**：那条只作用于被 keep 的类，匿名 TypeToken 子类是被 R8 改名的对象，属性照样被清
3. debug 构建不跑 R8，所以只有 release 复现；本地跑 `testDebugUnitTest` / `lintDebug` / 装 debug 包全都测不出来

## 解法

`app/proguard-rules.pro` 补 Gson 官方那组规则（关键是 `allowobfuscation,allowshrinking`，既保住签名又不会把整棵类树留在包里）：

```proguard
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature, InnerClasses, EnclosingMethod
```

重打 release → 装实体机 → 冷启动 `topResumedActivity=com.lightxin/.MainActivity`，crash buffer 干净，进程常驻。

## 预防

- **每次打完 release 必须装到一台真实设备上冷启动一次**，只看构建成功和 APK 元信息不算验证 —— 这是第二次栽在"release 才炸"上（前一次见 `2026-04-26-learning-proguard-mlkit-camerax-release-crash.md`）
- 新增反射/泛型取类型的代码（`TypeToken`、`JavaType`、`KClass.java`、`values()` 之外的枚举反射）时，同一次改动里就补 keep 规则
- 优先用不依赖匿名类签名的写法：`TypeToken.getParameterized(List::class.java, SpecialItem::class.java)`，或直接给具体类 `fromJson(json, Array/SpecialItem::class.java)`
- 排查同类问题的入口：`adb -s <serial> logcat -d -b crash`，栈里带 `r8-map-id-...` 的用 `app/build/outputs/mapping/release/mapping.txt` retrace
