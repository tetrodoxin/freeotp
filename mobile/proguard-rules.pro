# Keep line numbers for readable stack traces; hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson -------------------------------------------------------------------------------------
# Generic signatures and annotations are needed for TypeToken and @SerializedName.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Enums are serialized by constant name (e.g. Token.Type).
-keepclassmembers enum * { *; }

# --- Backup format (see MIGRATION.md, guarded by BackupFormatGoldenTest) ---------------------
# These classes are persisted with Gson reflection. MasterKey and EncryptedKey are stored under
# their raw field names (mCipher, mSalt, ...), so neither the classes nor their fields may be
# renamed or removed, or existing backups stop restoring.
-keep class org.fedorahosted.freeotp.Token { <fields>; }
-keep class org.fedorahosted.freeotp.Token$Secret { <fields>; }
-keep class org.fedorahosted.freeotp.encryptor.MasterKey { <fields>; }
-keep class org.fedorahosted.freeotp.encryptor.EncryptedKey { <fields>; }

# --- Libraries --------------------------------------------------------------------------------
# nbvcxz loads its dictionaries from jar resources relative to its classes.
-keep class me.gosimple.nbvcxz.** { *; }
-dontwarn me.gosimple.nbvcxz.**
