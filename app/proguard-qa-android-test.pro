# Only applied when explicitly running minified QA androidTest (-Pdambom.testBuildType=qa).
# AGP strips app-classpath libraries from the androidTest APK, so stdlib/test-runner
# helpers and the MainActivity navigation test entry point must remain reachable in the QA APK.
# Note: NavKey classes remain R8-obfuscated (allowobfuscation); only their public constructors
# keep parameter lists stable so androidTest can instantiate them directly.
-keep class androidx.tracing.** { *; }
-keep class kotlin.LazyKt* { *; }
-keep class kotlin.ResultKt { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keep class kotlin.coroutines.intrinsics.** { *; }
-keep class kotlinx.coroutines.BuildersKt { *; }
-keep interface com.comst19.dambom.feature.library.subtitle.runtime.LocalSubtitleLlm { *; }
-keep class com.google.android.play.core.splitcompat.SplitCompat { public static boolean install(android.content.Context); }
-keep class com.google.android.play.core.splitinstall.** { *; }
-keep class com.google.android.gms.tasks.Task { public com.google.android.gms.tasks.Task addOnFailureListener(com.google.android.gms.tasks.OnFailureListener); }
-keep interface com.google.android.gms.tasks.OnFailureListener { *; }
-keepclassmembers class com.comst19.dambom.presentation.MainActivity {
    public com.comst19.dambom.core.navigation.NavigationDispatcher getNavigationDispatcher();
}
-keep interface com.comst19.dambom.core.navigation.NavigationDispatcher { *; }
-keep class com.comst19.dambom.core.navigation.NavigationEvent** { *; }
-keepclassmembers,allowobfuscation class com.comst19.dambom.feature.**.api.*Key {
    public <init>(...);
}
