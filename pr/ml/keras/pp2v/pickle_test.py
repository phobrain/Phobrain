#!/usr/bin/python3
import datetime
import pickle

t0 = datetime.datetime.now()
unpacked_object = pickle.load(open('d.p', 'rb'))
f2, v2, h2, i2, hi2, j2 = unpacked_object
print('-- unpack: ' + str(datetime.datetime.now()-t0))

