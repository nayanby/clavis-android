# Keep Clavis names and members stable for persisted state and reflective checks.
# R8 can still optimize these classes and shrink unused dependency code.
-keep,allowoptimization class com.clavis.reader.** { *; }

# These bindings are called by native libraries through JNI by class/member name.
-keep class com.artifex.mupdf.fitz.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class org.libtorrent4j.swig.** { *; }

# Preserve useful source locations in production crash reports.
-keepattributes SourceFile,LineNumberTable
