LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
# Play requires the optional module to match the base ABI set. LLM execution remains 64-bit only.
LOCAL_MODULE := dambom_translation_abi
LOCAL_SRC_FILES := abi_marker.c
include $(BUILD_SHARED_LIBRARY)
