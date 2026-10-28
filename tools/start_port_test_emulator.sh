#!/bin/sh
# Diagnostic Apple Silicon profile for the inspected third-party ports.
# Does not change APKs, grant permissions, or establish phone compatibility.
set -eu
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
avd=${1:-DepthWall_API37}
if [ "$#" -gt 0 ]; then shift; fi
case "$(uname -s):$(uname -m)" in
    Darwin:arm64) ;;
    *) echo 'This measured profile requires Apple Silicon and MoltenVK/native ASTC.' >&2; exit 2 ;;
esac
# This explicit ANGLE test override is not a general GLES conformance claim.
export ANGLE_FEATURE_OVERRIDES_ENABLED=exposeES32ForTesting
export ANGLE_FEATURE_OVERRIDES_DISABLED='supportsBlend*'
exec "$ANDROID_HOME/emulator/emulator" -avd "$avd" -gpu host \
    -feature GuestAngle,Vulkan,-VulkanAstcLdrEmulation -memory 4096 \
    -no-snapshot -no-audio -no-boot-anim "$@"
