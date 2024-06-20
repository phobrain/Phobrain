#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 3_avg_by_orient.sh - average all .pairs_bin per v,h 
#                      into [vh]_all/[vh]_avg_N.pairs_bin.
#                        and [vh]_all/[vh]_avg_N.pairs.
#
# Third ML post-training step in adding photos.
# [vh]_all/bin_avg_all.pairs used by proj/corr/run.[vh] 
#     to make pr.pairs_[vh].d0 column)

set -e

AVG_ARG="-avg"

if [ $# != "1" ] ; then
    # echo args ne 1
    if [ $# != "2" ] ; then
        echo "Usage: $0 <dir> [-bin] # ('.' is ok)"
        exit 1
    elif [ $2 == "-bin" ] ; then
        AVG_ARG="-avgbin"
    fi
fi

BASE=$1

RESULT_DIR=


if [[ "$BASE" == "." ]] ; then
    echo "=== $0 BASE=. so running v, h -> v_all/, h_all/"
#    if [[ -e v_all/bin_avg_all.pairs || -e h_all/bin_avg_all.pairs ]]; then
#        echo "EXISTS: one or both: <v_all|h_all>/bin_avg_all.pairs"
#        echo Removing in 5 sec
#        sleep 5
#        /bin/rm -f v_all/bin_avg_all.pairs h_all/bin_avg_all.pairs
#    fi
elif [[ "$BASE" == "v" || "$BASE" == "h" ]] ; then
    RESULT_DIR="./${BASE}_all/"
    echo "=== $0 $1 so running all $1 -> $RESULT_DIR/bin_avg_all.pairs"
#    if [[ -e $RESULT_DIR/bin_avg_all.pairs ]]; then
#        echo "EXISTS: $RESULT_DIR/bin_avg_all.pairs"
#        echo Removing in 5 sec
#        sleep 5
#        /bin/rm -f $RESULT_DIR/bin_avg_all.pairs 
#    fi
else
    RESULT_DIR=$BASE
#    RESULT="$BASE/bin_avg_all.pairs"
#    if [ "$AVG_ARG" == "-avgbin" ] ; then
#        RESULT="$BASE/bin_avg_all.pairs_bin"
#    fi

    echo "=== $0 $BASE"

#    if [[ -e "$RESULT" ]]; then
#        echo "EXISTS: $RESULT"
#        echo Removing in 5 sec
#        sleep 5
#        /bin/rm -f $RESULT
#    fi
fi


TMP=/tmp/predtool.$$

# starting model names m_xxx avoids including intermediate avgs

if [[ $BASE == "h" ]] ; then
    find . -name "m_h*.pairs_bin" > $TMP
elif [[ $BASE == "v" ]] ; then
    find . -name "m_v*.pairs_bin" > $TMP
else
    find $BASE -name "*.pairs_bin" > $TMP
fi

TOT=`wc -l $TMP`
#cat $TMP
#exit

echo === start w/ $TMP, total $TOT

time {

    NV=`egrep 'm_v_|m_vb_' $TMP | wc -l`
    ALL_V=`egrep 'm_v_|m_vb_' $TMP | tr '\n' ' '`
    NH=`egrep 'm_h_|m_hb_' $TMP | wc -l`
    ALL_H=`egrep 'm_h_|m_hb_' $TMP | tr '\n' ' '`

    if [[ $NV -gt 0 ]]; then
        echo "=== v's: $NV"
        echo $ALL_V
        if [ $NH -gt 0 ] ; then
            # v's in background
            if [[ "$RESULT_DIR" != "" ]]; then
                predtool.py -v $AVG_ARG -o "$RESULT_DIR" $ALL_V &
            else
                predtool.py -v $AVG_ARG -o v_all $ALL_V &
            fi
        else
            # v's in foreground
            if [[ "$RESULT_DIR" != "" ]]; then
                predtool.py -v $AVG_ARG -o "$RESULT_DIR" $ALL_V 
            else
                predtool.py -v $AVG_ARG -o v_all $ALL_V 
            fi
        fi
    else
        echo "=== (no v_'s)"
    fi

    if [[ $NH -gt 0 ]]; then
        echo "=== h's: $NH"
        if [[ "$RESULT_DIR" != "" ]]; then
            predtool.py -h $AVG_ARG -o "$RESULT_DIR" $ALL_H
        else
            predtool.py -h $AVG_ARG -o h_all $ALL_H
        fi
    else
        echo "=== (no h_'s)"
    fi

    /bin/rm -f $TMP

    wait
}
echo === $0 $BASE DONE total $TOT
