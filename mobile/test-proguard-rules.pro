# R8 rules for the androidTest APK when testing the minified release build (-PtestMinified).
# Test code is not shipped, so keep it whole and ignore optional dependencies of the test
# libraries.
-dontobfuscate
-keep class org.fedorahosted.freeotp.** { *; }
-dontwarn edu.umd.cs.findbugs.annotations.**
# Compile-only annotations used by androidx.test.
-dontwarn com.google.errorprone.annotations.**
