#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

PHOB_HOME=`../../bin/get_phobrain_home.sh`
ML=$PHOB_HOME/ml/keras/pp2v

RESULT="pr.pairtop_nn_v_dump.sql"

START=`date`

#USAGE="Usage: $0 <col|nn>"

echo "==== Vert: $RESULT .."

time {

./run.sh v nn \
    v0 $ML/ \
    v47 $ML/v_2022_10 \
    v48 $ML/v_2022_10/jh2 \
    v49 $ML/v_2022_10/jh3 \
    v50 $ML/v_2022_10/jh4 \
    v51 $ML/v_2022_10/jh5 \
    v51 $ML/v_2022_10/jh_all \
    v67 $ML/vb_2022_10 \
    v68 $ML/vb_2022_10/jh2 \
    v69 $ML/vb_2022_10/jh3 \
    v70 $ML/vb_2022_10/jh4 \
    v71 $ML/vb_2022_10/jh5 \
    v72 $ML/v_2022_11 \
    v73 $ML/v_2022_11/jh2 \
    v74 $ML/v_2022_11/jh3 \
    v75 $ML/v_2022_11/jh4 \
    v76 $ML/v_2022_11/jh_all \
    v77 $ML/v_2023_03/jh2 \
    v78 $ML/v_2023_03/jh4 \
    v79 $ML/v_2023_03/jh_all \
    v80 $ML/v_2023_03

ls -l $RESULT
du -sh $RESULT

echo === $0 DONE

echo START $START
echo "END   `date`"

}
