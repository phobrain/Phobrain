#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

ML=`../../bin/phobrain_property.sh ml.h.dir`

RESULT="pr.pairtop_nn_h_dump.sql"

START=`date`

echo "==== Horiz: $RESULT .."

time {

./run.sh h nn \
    h0  $ML \
    h1  $ML/h_2022_10/jh_all \
    h2  $ML/hb_2022_10/jh_all


ls -l $RESULT
du -sh $RESULT

echo === $0 DONE

echo START $START
echo "END   `date`"

}
