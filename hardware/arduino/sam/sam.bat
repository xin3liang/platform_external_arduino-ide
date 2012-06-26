set Path=%ARM_GCC_TOOLCHAIN%
export Path

start "libsam" /d"system\libsam\build_gcc" /max "cd"
start "libarduino" /d"cores\sam\build_gcc" /max "cd"
start "libvariant ADK2" /d"..\..\google\sam\variants\adk2\build_gcc" /max "cd"
start "libvariant SAM3X-EK" /d"..\..\atmel\sam\variants\sam3x_ek\build_gcc" /max "cd"
start "test" /d"cores\sam\validation\build_gcc" /max "cd"
