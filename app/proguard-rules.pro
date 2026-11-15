# Release builds use R8 shrinking, optimization, and obfuscation.
# Do not add broad keep rules: preserve only APIs that genuinely require reflection.
-renamesourcefileattribute SourceFile
