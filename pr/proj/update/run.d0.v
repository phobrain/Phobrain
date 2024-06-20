#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#


set -e

ML="`phobrain_property.sh ml.v.dir`"

FNAME=$ML/dirPB_70.pb

if [ ! -f "$FNAME" ] ; then

    N=`ls -1 $ML/*.pb|wc -l`
    if [ $N == "1" ] ; then
        path="`echo $ML/*.pb`"
        FNAME="$ML/`basename $path`"
    else
        echo "== $0 - 0 or >1 .pb's in $ML"
        exit 1
    fi
fi

echo "== $0 using $FNAME"

time java -Xmx40g -jar build/libs/update-all-1.0.jar d0 v $FNAME

