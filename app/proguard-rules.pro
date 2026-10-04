# Readability4J logs through slf4j; with no slf4j binding on Android it falls
# back to a no-op logger, so the missing binder class is expected.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# PdfBox-Android decodes JPEG 2000 images through an optional library that
# Jonaki does not ship; read_document only reads text, so the class is never needed.
-dontwarn com.gemalto.jp2.**

# export_pdf: PdfPrintJob and its callbacks must stay in the package android.print, because
# the framework's LayoutResultCallback and WriteResultCallback have package-private constructors.
# Without this rule R8 moves them to a package named "b" and the call fails with IllegalAccessError.
-keep class android.print.PdfPrintJob { *; }
-keep class android.print.PdfPrintJob$* { *; }
