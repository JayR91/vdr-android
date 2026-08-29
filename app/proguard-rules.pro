# Add project specific ProGuard rules here.

# --- VDR ---------------------------------------------------------------
# R8 is enabled so the build produces mapping.txt for Play. The app itself
# resolves nothing by name, so almost nothing needs keeping; these are the
# few edges where a name does survive into data or across a process boundary.

# Service and activity names are written into Intents and the manifest, and
# a PendingIntent created before an update must still resolve afterwards.
-keep class com.jayr91.vdr.MainActivity { *; }
-keep class com.jayr91.vdr.service.DownloadService { *; }

# Room generates an implementation whose name it looks up at runtime.
-keep class com.jayr91.vdr.data.** { *; }

# Enum values arrive back from persisted state by name (DownloadStatus is
# stored in the Room row and in the .vdrstate.json sidecar).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep source file and line numbers in stack traces, then rename the file
# attribute so it does not leak paths. Without this the mapping file cannot
# restore line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
