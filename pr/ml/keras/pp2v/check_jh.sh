#!/bin/bash

#  check_jh.sh - make sure all <date>/jh* dir predictions are done

\ls -d */jh* | egrep -v _vecs | while read i ; do
    if compgen -G "${i}/*.h5" > /dev/null; then
        N=`\ls $i/*.h5|wc -l`
        D=0
        if compgen -G "${i}/m_*.pairs"  > /dev/null; then
            D=`\ls $i/m_*.pairs|wc -l`
        fi
        if [ $N -eq $D ] ; then
            echo "=== $i   $N  $D"
        else
            echo "=== $i   $N  $D - mismatch"   
        fi
    else
        echo "=== $i no h5's"
    fi
done

