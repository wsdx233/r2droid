@echo off
"D:\\Software\\AndroidSDK\\ndk\\29.0.14206865\\ndk-build.cmd" ^
  "NDK_PROJECT_PATH=null" ^
  "APP_BUILD_SCRIPT=D:\\Project\\AndroidProjects\\Randroid\\terminal-emulator\\src\\main\\jni\\Android.mk" ^
  "APP_ABI=arm64-v8a" ^
  "NDK_ALL_ABIS=arm64-v8a" ^
  "NDK_DEBUG=1" ^
  "APP_PLATFORM=android-21" ^
  "NDK_OUT=D:\\Project\\AndroidProjects\\Randroid\\terminal-emulator\\build\\intermediates\\cxx\\Debug\\dj2s5c44/obj" ^
  "NDK_LIBS_OUT=D:\\Project\\AndroidProjects\\Randroid\\terminal-emulator\\build\\intermediates\\cxx\\Debug\\dj2s5c44/lib" ^
  "APP_CFLAGS+=-std=c11" ^
  "APP_CFLAGS+=-Wall" ^
  "APP_CFLAGS+=-Wextra" ^
  "APP_CFLAGS+=-Werror" ^
  "APP_CFLAGS+=-Os" ^
  "APP_CFLAGS+=-fno-stack-protector" ^
  "APP_CFLAGS+=-Wl,--gc-sections" ^
  termux
