#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === ck_an.sh - analyze click mousedown duration.

  SUM=0
  N=0
  LONGER_CK=0
  LONG_CK=0
  SHORT_CK=0
  SHORTER_CK=0
  CENTER=0
  while read i ; do
    SUM=$(echo "scale=2; $SUM + $i" | bc -l)
    if [ "$i" -gt "190" ] ; then
      LONGER_CK=$((LONGER_CK+1))
    elif [ "$i" -gt "170" ] ; then
      LONG_CK=$((LONG_CK+1))
    elif [ "$i" -lt "130" ] ; then
      SHORTER_CK=$((SHORTER_CK+1))
    elif [ "$i" -lt "150" ] ; then
      SHORT_CK=$((SHORT_CK+1))
    else
      CENTER=$((CENTER+1))
    fi
    N=$((N+1))
  done
  AVG=$(echo "$SUM / $N" | bc);
  echo "N, sum, avg" $N $SUM $AVG
  echo $LONGER_CK $LONG_CK @ $CENTER @ $SHORT_CK $SHORTER_CK
