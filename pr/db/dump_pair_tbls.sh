#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023,2025 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === dump_pair_tbls.sh - back up pair training data, which
#           is the most-irreplaceable part of Phobrain.

set -e

BIN=../bin

BAK_DIR=`../bin/phobrain_property.sh sql.dump.dir`
BAK_DEV=`../bin/phobrain_property.sh bak.dev`

DFCMD="df -h $BAK_DEV"


echo "========== $0"
echo "	(DB ==> $BAK_DIR ==> $BAK_DEV)" 

echo === DB
psql -d pr --pset pager=off -c "select count(*), status, vertical from pr.approved_pair group by status, vertical order by status asc, vertical desc;"
psql -d pr --pset pager=off -c "select count(*) from pr.pair_local ;"

#psql -d pr --pset pager=off -c "select count(*), flow_rating, pair_rating, vertical from pr.feeling_pair group by flow_rating, pair_rating, vertical order by flow_rating asc, pair_rating desc, vertical desc;"

LIST=($BAK_DIR/pr.approved_pair_dump.sql $BAK_DIR/pr.pair_local_dump.sql $BAK_DIR/pr.showing_pair_dump.sql $BAK_DIR/pr.feeling_pair_dump.sql)

echo === Examining $BAK_DIR

echo "--- ls"

for i in "${LIST[@]}" ; do
    if [ -e $i ] ; then
        ls -l $i | awk '{print $5,$6,$7,$8," ",$NF}'
    fi
done
echo "--- du"
for i in "${LIST[@]}" ; do
    if [ -e $i ] ; then
        du -sh $i
    fi
done
echo "--- wc -l"
for i in "${LIST[@]}" ; do
    if [ -e $i ] ; then
        wc -l $i
    fi
done
echo
for i in "${LIST[@]}" ; do
    if [ ! -e $i ] ; then
        echo "no $i"
        echo
    fi
done

echo -n "Dump db tables? [Y/n]: "
read REPLY
if [ "$REPLY" == "" -o "$REPLY" == "y" -o "$REPLY" == "Y" ] ; then

  TIMEFORMAT='dumped in %R seconds'
  time {
	./dump_tbl.sh pr.approved_pair $BAK_DIR
	./dump_tbl.sh pr.pair_local $BAK_DIR
	./dump_tbl.sh pr.showing_pair $BAK_DIR
	./dump_tbl.sh pr.feeling_pair $BAK_DIR
  }
  echo "--- wc -l"
  for i in "${LIST[@]}" ; do
    wc -l $i
  done
  echo "--- du -sh"
  for i in "${LIST[@]}" ; do
    du -sh $i
  done
fi

if [ -e $BAK_DEV ] ; then
    if [ -e ../0log ] ; then
      CMD="cp ../0log $BAK_DEV/"
      echo $CMD
      $CMD
    else
      echo no ../0log
    fi
    $DFCMD
    for i in "${LIST[@]}" ; do
      FILE=`basename $i`
      ls -l $BAK_DEV/${FILE}.gz | cut -f 5- -d ' ' --output-delimiter=$'\t'
    done
    echo -n "Copy db dumps to $BAK_DEV? [y|N]: "
    read VAL
    if [ "$VAL" == "y" ] ; then
      TIMEFORMAT='gzipped/copied in %R seconds'
      time {
        for i in "${LIST[@]}" ; do
          FILE=`basename $i`
          gzip -c $i > $BAK_DEV/${FILE}.gz ; \
             ls -l $BAK_DEV/${FILE}.gz
        done
      }
      $DFCMD
    fi
fi

echo "=== rsync -avh ~/dots/ $BAK_DEV/train/dots/"

rsync -avh ~/dots/ $BAK_DEV/train/dots/
echo ========== $0 done
