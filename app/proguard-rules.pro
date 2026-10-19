# ProGuard rules for LightXin

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.lightxin.**.data.** { *; }
-keep class com.lightxin.**.domain.** { *; }

# Gson 的匿名 TypeToken 子类：R8 会把它的泛型签名优化掉，运行时 getGenericSuperclass()
# 拿不到类型参数就抛 IllegalStateException("TypeToken must be created with a type argument")。
# 这类崩溃只在 release 出现，且死在类的 <clinit> 上，看起来就是"点图标秒闪退"。
# allowobfuscation/allowshrinking 让它参与混淆但保住签名，不会因为整棵树被 keep 而变大。
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature, InnerClasses, EnclosingMethod

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# RSA keys
-keep class com.lightxin.core.auth.RSAUtils { *; }

# CameraX — release 包扫码必须保留，否则 ImageProxy/PreviewView 被优化后识别失效
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# ML Kit Barcode Scanning — 必须 keep 整个 com.google.mlkit 树，因为内部依赖注入
# 在 MlKitInitProvider 启动时解析组件，混淆后接口名/类名不匹配会导致直接崩溃
-keep class com.google.mlkit.** { *; }
