# R8 rules for the androidTest APK when testing the minified release build (-PtestMinified).
# Test code is not shipped, so keep it whole and ignore optional dependencies of the test
# libraries (Mockito/ByteBuddy reference JNA, java.lang.instrument, findbugs annotations, ...).
-dontobfuscate
-keep class org.fedorahosted.freeotp.** { *; }
-dontwarn com.sun.jna.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.lang.**
-dontwarn org.mockito.**
-dontwarn org.opentest4j.**
