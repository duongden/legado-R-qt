LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := rtmp-jni
LOCAL_SRC_FILES := rtmpmuxer.c librtmp-jni.c \
    librtmp/amf.c librtmp/hashswf.c librtmp/log.c \
    librtmp/parseurl.c librtmp/rtmp.c flvmuxer/xiecc_rtmp.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/librtmp
LOCAL_CFLAGS := -DNO_CRYPTO
# Align both load segments and RELRO boundaries for 4 KB and 16 KB devices.
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
