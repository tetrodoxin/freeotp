# Only applied with -PtestMinified. The androidTest APK uses AndroidX and Kotlin library classes
# (test runner tracing, Compose test APIs) from the app APK, which R8 would otherwise strip or
# rename. The app's own classes stay obfuscated, so the backup keep rules are still exercised.
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Tests call app methods the app itself may not use (or that R8 would inline). Keep the members
# but still allow renaming: field names then survive only where proguard-rules.pro protects them.
-keep,allowobfuscation class org.fedorahosted.freeotp.** { *; }

# Library APIs used only by tests (e.g. commons-codec Base32.encodeAsString).
-keep class org.apache.commons.codec.** { *; }
