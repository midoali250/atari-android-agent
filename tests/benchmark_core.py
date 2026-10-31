import ctypes as C, sys, time
lib=C.CDLL(sys.argv[1])
class Game(C.Structure): _fields_=[('path',C.c_char_p),('data',C.c_void_p),('size',C.c_size_t),('meta',C.c_char_p)]
ENV=C.CFUNCTYPE(C.c_bool,C.c_uint,C.c_void_p)
VIDEO=C.CFUNCTYPE(None,C.c_void_p,C.c_uint,C.c_size_t)
AUDIO=C.CFUNCTYPE(C.c_size_t,C.POINTER(C.c_int16),C.c_size_t)
POLL=C.CFUNCTYPE(None)
INPUT=C.CFUNCTYPE(C.c_int16,C.c_uint,C.c_uint,C.c_uint,C.c_uint)
def env(cmd,data):
    if cmd==10: C.cast(data,C.POINTER(C.c_int)).contents.value=1; return True
    if cmd==16: C.cast(data,C.POINTER(C.c_bool)).contents.value=True; return True
    return cmd in (14,21,22,35,36,65587)
callbacks=[ENV(env),VIDEO(lambda *a:None),AUDIO(lambda d,n:n),POLL(lambda:None),INPUT(lambda *a:0)]
for name,cb in zip(['retro_set_environment','retro_set_video_refresh','retro_set_audio_sample_batch','retro_set_input_poll','retro_set_input_state'],callbacks): getattr(lib,name)(cb)
lib.retro_init();lib.retro_load_game.argtypes=[C.POINTER(Game)]
assert lib.retro_load_game(C.byref(Game(sys.argv[2].encode(),None,0,None)))
lib.retro_set_controller_port_device(0,513)
for _ in range(300): lib.retro_run()
n=1800; start=time.perf_counter()
for _ in range(n): lib.retro_run()
elapsed=time.perf_counter()-start
print(f'frames={n} seconds={elapsed:.3f} fps={n/elapsed:.1f} ms_per_frame={elapsed*1000/n:.3f}')
lib.retro_unload_game();lib.retro_deinit()
