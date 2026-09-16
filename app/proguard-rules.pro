# Finch proguard rules（release 已开 R8 + 资源缩减）
#
# 依赖库自带 consumer rules 的：Room / Compose / Lifecycle / Coil / okhttp / okio /
# kotlinx-coroutines / backdrop / shapes —— 一般无需额外规则。
# 本 app 无反射、无 JNI、无 Java serialization；Room 实体在编译期生成实现。
# 若后续引入反射型库（如 Moshi/kotlinx-serialization reflect、JUnit on device），
# 在此补充 keep 规则。

# 保留行号便于 crash 反解（不影响混淆与优化）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
