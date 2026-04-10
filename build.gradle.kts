// This tells the factory which plugins to use
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    // ✅ Updated from 1.9.0 to 1.9.23 to match Compose Compiler 1.5.11
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
}
