# The JNI layer finds the snapshot callback by name (jonaki_llama.cpp).
-keep interface app.jonaki.providers.localllama.SnapshotListener { *; }
-keepclassmembers class * implements app.jonaki.providers.localllama.SnapshotListener {
    void onSnapshot(byte[], byte[]);
}
