#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

#  === pr/bak.sh 
#
#   Tar source (including ../.git in pr_git), and
#   dump database files with training data.
#

HOME=`bin/get_phobrain_home.sh`

# dir where source 'pr' dir lives

PR_HOME=$HOME/pr_git

# dir where derived pic data lives

IMAGE_DESC=`bin/phobrain_property.sh image.desc.dir`

# dirs where ML is done

ML_V_HOME=`bin/phobrain_property.sh ml.v.dir`
ML_H_HOME=`bin/phobrain_property.sh ml.h.dir`

# place to tar to

BAK_DIR=$HOME/phobrain_bak/

# place to save to

BAK_DEV=`bin/phobrain_property.sh bak.dev`

#### sanity check

echo "Settings:"
echo -e "\tPR_HOME\t$PR_HOME"
echo -e "\tIM_DESC\t$IMAGE_DESC"
echo -e "\tML_V_HOME\t$ML_V_HOME"
echo -e "\tML_H_HOME\t$ML_H_HOME"
echo -e "\tBAK_DIR\t$BAK_DIR   (for initial tar)"
echo -e "\tBAK_DEV\t$BAK_DEV   (copy all backed-up to)"
echo
echo Source devs:
df -h $PR_HOME $IMAGE_DESC $ML_V_HOME $ML_H_HOME $BAK_DIR | sort -ur | sed 's/^/\t/'
echo Dest dev:
df -h $BAK_DEV | grep dev | sed 's/^/\t/'
echo
echo "Sleeping 5 sec and proceeding"
sleep 5
echo BACKING UP: PEPARING FOR tar.sh
echo


######## no site-specific settings from here on 

PR=$PR_HOME/pr

if [ `pwd` != $PR ] ; then
	echo Sorry, "$0" needs to be run in pr
	exit 1
fi

DFCMD="df -h $BAK_DEV"

if [ ! -d "$PR" ] ; then
	echo No PR_HOME/pr/: $PR
	exit 1
fi

echo == latest in bak_dir
ls -ltr $BAK_DIR | tail -3

echo == latest log
ls -l $PR/0log

echo ==== bak device ====
$DFCMD
echo ====================

echo -n "Dump db pair tables? [Y/n]: "
read REPLY
case $REPLY in
  "") echo ok ; (cd ./db ; ./dump_pair_tbls.sh) ;;
  [yY]) echo ; echo ok ; (cd ./db ; ./dump_pair_tbls.sh) ;;
  [nN]) echo ; echo skipping db dump ;;
  *) printf " \033[31m %s \n\033[0m" "invalid input" ; exit 1 ;;
esac

if [ -d $ML_V_HOME ] ; then
    echo "== $0  Copying phobrain ml $ML_V_HOME/ \*.py|.sh into $PR/ml/keras/pp2v/"
    cp -p $ML_V_HOME/*.py $ML_V_HOME/*.sh $PR/ml/keras/pp2v/
else
    echo "(No local $ML_V_HOME)"
fi

if [ -d $ML_H_HOME ] ; then
    echo "== $0  Copying phobrain ml $ML_H_HOME \*.py|.sh into $PR/ml/keras/pp2h/"
    cp -p $ML_H_HOME/*.py $ML_H_HOME/*.sh $PR/ml/keras/pp2h/
else
    echo "(No local $ML_H_HOME)"
fi

echo copying to misc

for i in /var/phobrain/phogit.properties 
{
  echo $i " "
  if [ ! -e $i ] ; then
    echo NONE: $i
  else
    echo === $i
    cp -p $i $PR/misc/
  fi
}

echo

echo "Ready: size is `du -sh $PR` and biggest files are:"

find . -printf "%k %p\n" |
    egrep -v "build|gradle|lib|jar" | \
    sort -nr | head

echo
echo " - write 0log if you will: tar it? [y|n]"
read VAL
if [ "$VAL" == "n" ] ; then
  echo "== $0  exiting - resume with cmd: ./tar.sh"
  exit 0
fi
if [ "$VAL" == "y" ] ; then
 ./tar.sh
else
 echo "== $0  you gave $VAL No backup."
fi
