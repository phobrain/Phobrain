#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === gpu_q.py - simple job distributor

import sys
import os
import threading, queue
import subprocess
import time

PROCS_GPU = 1
PREDICT = 'ppred.py'

if len(sys.argv) != 2:
    print('Usage: ' + sys.argv[0] + ' <cmd_file>')
    exit(1)

CMDS = sys.argv[1]

N_GPU = len(os.listdir('/proc/driver/nvidia/gpus/'))

print('== N_GPU: ' + str(N_GPU) + ' procs/gpu: ' + str(PROCS_GPU) + \
            ' Predict: ' + PREDICT + ' cmds: ' + CMDS)

q = queue.SimpleQueue()

cmds = 0

with open(CMDS, 'r') as f:
    for cmd in f:
        q.put(cmd)
        cmds = cmds + 1

print('== Cmds queued: ' + str(q.qsize()))

def worker(gpu):
    print(f' == gpu_q Starting worker, gpu {gpu}')
    while True:
        #if q.empty():
        #    print(f' -- Done.2 {gpu}')

        try:
            item = q.get(block=False)
        except queue.Empty:
            print(f' -- Done.2 {gpu}')
            break
            
        item = item.replace(PREDICT, PREDICT + ' -gpu ' + str(gpu))

        print(f' -- gpu_q.py: GPU {gpu} Working on [ {item} ]')
        subprocess.run(item, shell=True)
        print(f' -- gpu_q.py: GPU {gpu} Finished [ {item} ]')

threads = []

for j in range(PROCS_GPU):
    print('-- gpu_q: starting PROCS_GPU round-robin: ' + str(PROCS_GPU))
    for i in range(N_GPU):
        thread = threading.Thread(target=worker, args=(i,))
        threads.append(thread)
        thread.start()
        if len(threads) >= cmds:
            break
    if len(threads) >= cmds:
        break

print('-- gpu_q workers up/joining: ' + str(len(threads)))

while True:
    child_working = False
    for t in threads:
        if t.is_alive():
            child_working = True
        else:
            t.join()
    if not child_working:
        break
    time.sleep(10)

print('=== All work completed')
