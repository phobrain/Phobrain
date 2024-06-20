#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === gpu_q.py - simple job distributor

N_GPU = 3

PROCS_GPU = 3

PREDICT = 'pred.py'

import sys

if len(sys.argv) != 2:
    print('Usage: ' + sys.argv[0] + ' <cmd_file>')
    exit(1)

CMDS = sys.argv[1]

import threading, queue

q = queue.SimpleQueue()

cmds = 0

with open(CMDS, 'r') as f:
    for cmd in f:
        q.put(cmd)
        cmds = cmds + 1

print('== Cmds queued: ' + str(q.qsize()))

#import time
import subprocess

def worker(gpu):
    print(f' -- Starting worker, gpu {gpu}')
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
    for i in range(N_GPU):
        thread = threading.Thread(target=worker, args=(i,))
        threads.append(thread)
        thread.start()
        if len(threads) >= cmds:
            break
    if len(threads) >= cmds:
        break

print('-- workers up: ' + str(len(threads)))

for thread in threads:
    thread.join()

print('All work completed')
