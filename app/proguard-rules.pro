# Add project specific ProGuard rules here.

# --- sshj (SSH library) ---
# sshj uses reflection for crypto algorithm discovery
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**

# --- BouncyCastle (crypto provider) ---
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- EdDSA (Ed25519 keys) ---
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# --- commonmark (Markdown parsing) ---
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

