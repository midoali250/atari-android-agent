import ctypes as C, hashlib, json, sys
from pathlib import Path
lib=C.CDLL(sys.argv[1])
class Info(C.Structure): _fields_=[('library_name',C.c_char_p),('library_version',C.c_char_p),('valid_extensions',C.c_char_p),('need_fullpath',C.c_bool),('block_extract',C.c_bool)]
class Game(C.Structure): _fields_=[('path',C.c_char_p),('data',C.c_void_p),('size',C.c_size_t),('meta',C.c_char_p)]
class Geometry(C.Structure): _fields_=[('base_width',C.c_uint),('base_height',C.c_uint),('max_width',C.c_uint),('max_height',C.c_uint),('aspect_ratio',C.c_float)]
class Timing(C.Structure): _fields_=[('fps',C.c_double),('sample_rate',C.c_double)]
class Av(C.Structure): _fields_=[('geometry',Geometry),('timing',Timing)]
ENV=C.CFUNCTYPE(C.c_bool,C.c_uint,C.c_void_p)
VIDEO=C.CFUNCTYPE(None,C.c_void_p,C.c_uint,C.c_uint,C.c_size_t)
AUDIO=C.CFUNCTYPE(C.c_size_t,C.POINTER(C.c_int16),C.c_size_t)
POLL=C.CFUNCTYPE(None)
INPUT=C.CFUNCTYPE(C.c_int16,C.c_uint,C.c_uint,C.c_uint,C.c_uint)
mask=0; frames=[]; samples=[]; seen=set()
def env(cmd,data):
    if cmd==10: C.cast(data,C.POINTER(C.c_int)).contents.value=1; return True
    if cmd==16: C.cast(data,C.POINTER(C.c_bool)).contents.value=True; return True
    return cmd in (14,21,22,35,36)
def video(data,w,h,pitch):
    if data: frames.append((w,h,hashlib.sha256(C.string_at(data,w*h*2)).hexdigest()))
def audio(data,n): samples.append(n); return n
def input_state(port,device,index,id):
    seen.add(id); return int(bool(mask&(1<<id)))
callbacks=[ENV(env),VIDEO(video),AUDIO(audio),POLL(lambda:None),INPUT(input_state)]
for name,cb in zip(['retro_set_environment','retro_set_video_refresh','retro_set_audio_sample_batch','retro_set_input_poll','retro_set_input_state'],callbacks): getattr(lib,name)(cb)
lib.retro_get_system_info.argtypes=[C.POINTER(Info)]
info=Info();lib.retro_get_system_info(C.byref(info));print(info.library_name,info.library_version)
lib.retro_init();lib.retro_load_game.argtypes=[C.POINTER(Game)]
path=sys.argv[2].encode()
assert lib.retro_load_game(C.byref(Game(path,None,0,None)))
lib.retro_get_system_av_info.argtypes=[C.POINTER(Av)];av=Av();lib.retro_get_system_av_info(C.byref(av));print('fps',av.timing.fps,'audio',av.timing.sample_rate)
lib.retro_set_controller_port_device(0,1)
lib.retro_get_memory_data.restype=C.c_void_p;lib.retro_get_memory_data.argtypes=[C.c_uint]
lib.retro_get_memory_size.restype=C.c_size_t;lib.retro_get_memory_size.argtypes=[C.c_uint]
ram=lib.retro_get_memory_data(2); size=lib.retro_get_memory_size(2);print('ram',size)
def run(n):
    for _ in range(n):lib.retro_run()
run(180)
base=frames[-1];assert len(set(x[2] for x in frames))>10
results={}
for name,id in [('Start',3),('A',1),('B',0),('C',8),('Left',6),('Right',7),('Up',4),('Down',5)]:
    mask=0;run(15);before=C.string_at(ram,size)
    mask=1<<id;run(20);after=C.string_at(ram,size)
    results[name]={'core_queries':id in seen,'ram_changed':before!=after,'changed_bytes':sum(a!=b for a,b in zip(before,after)),'frame_changed':base[2]!=frames[-1][2]}
    assert results[name]['core_queries']
    mask=0;run(10)
print(json.dumps({'distinct_frames':len(set(x[2] for x in frames)),'audio_callbacks':len(samples),'inputs':results},indent=2))
assert all(x['ram_changed'] for x in results.values())
lib.retro_unload_game();lib.retro_deinit()
