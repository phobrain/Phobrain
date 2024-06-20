#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

if [ $# -gt 0 ] ; then
    echo "please dude - no args!"
    exit 1
fi

set -e

IMAGE_DESC_DIR=`../../bin/phobrain_property.sh image.desc.dir`

echo "=== $0 IMAGE_DESC_DIR = $IMAGE_DESC_DIR"

ML=`../../bin/phobrain_property.sh ml.v.dir`

ML_ARG=

if [ ! -d "$ML" ] ; then
    echo "== $0 [no ML dir $ML]"
    ML=""
else
    N=`ls -1 $ML/*.pb | wc -l`
    if [ "$N" == "1" ] ; then
        echo "== $0 using ML .pb in $ML"
        ML_ARGS="avg d0 $ML/"
    else
        echo "== $0 need single ML .pb in $ML"
        if [ "$N" == "0" ] ; then
            echo "==    - No ML to add: no d0 column"
            ML=""
        else
            echo "==    - exiting on .pb's > 1"
            exit 1
        fi
    fi
fi

HPU="../histogram/pairs_uniq_v"

if [ -f pr.pairs_v_dump.sql_body ] || \
   [ -f $HPU ] ; then

	echo "Output files exist (pr.pairs_v_dump.sql_body, $HPU) - sleeping 5, then removing"
	sleep 5
	rm -f pr.pairs_v_dump.sql_body $HPU
fi

echo Vert 
./run v $IMAGE_DESC_DIR -all $ML_ARGS


ls -l pr.pairs_v_dump.sql* $HPU
du -sh pr.pairs_v_dump.sql* $HPU
