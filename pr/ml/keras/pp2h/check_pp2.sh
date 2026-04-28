#!/bin/bash

H5_L=/tmp/ct1.$$
H5_Lx=/tmp/ct1x.$$
BIN_L=/tmp/ct2.$$
TOP_L=/tmp/ct3.$$

# debug
#H5_L=/tmp/h5l
#H5_Lx=/tmp/h5lx
#BIN_L=/tmp/binl
#TOP_L=/tmp/topl

find . -name "*.h5" | egrep -v JH | sort > "$H5_L"
N_H5=`wc -l "$H5_L" | awk '{print $1}'`

find . -name "*.pairs_bin" | egrep -v '/v_all|/vb_all|/h_all|/hb_all' | sort > "$BIN_L"
N_BIN=`wc -l "$BIN_L" | awk '{print $1}'`

find . -name "*.top" | egrep -v '/v_all|/vb_all|/h_all|/hb_all' | sort > "$TOP_L"
N_TOP=`wc -l "$TOP_L" | awk '{print $1}'`

ERROR=0

if [ "$N_H5" -ne "$N_BIN" ] ; then
    ERROR=1
    echo "== ERROR:  .h5 $N_H5  .pairs_bin $N_BIN"
    echo "== Missing (d's):"
    sed -e 's:h5:pairs_bin:g' "$H5_L" > "$H5_Lx"
    diff "$H5_Lx" "$BIN_L"
fi
if [ "$N_H5" -ne "$N_TOP" ] ; then
    echo "== ERROR:  .h5 $N_H5  .top $N_TOP"
    if [ "$ERROR" -eq 0 ]; then
        echo "== Missing (d's):"
        sed -e 's:h5:top:g' "$H5_L" > "$H5_Lx"
        diff "$H5_Lx" "$TOP_L"
    fi
    ERROR=1
fi

/bin/rm -f "$H5_L" "$H5_Lx" "$BIN_L" "$TOP_L"

if [ $ERROR -eq 1 ] ; then
    echo "== nope"
    exit 1
fi

echo "------ ok: .h5's == .pairs_bin == .top: $N_H5"
