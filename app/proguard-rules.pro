# Readability4J logs through slf4j; with no slf4j binding on Android it falls
# back to a no-op logger, so the missing binder class is expected.
-dontwarn org.slf4j.impl.StaticLoggerBinder
