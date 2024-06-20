#!/bin/bash

#  avg_jh.sh - per-dir avg 

\ls -d */jh* | egrep -v _vecs | while read i ; do
    if compgen -G "${i}/*.pairs_bin" > /dev/null; then
        ./3_avg_by_orient.sh $i
    fi
done

