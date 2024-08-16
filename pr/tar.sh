#!/bin/bash

set -e

HOME=`bin/get_phobrain_home.sh`
# dir where source 'pr' dir lives
PR_HOME="$HOME/pr_git"
# dir to tar to
BAK_DIR="$HOME/phobrain_bak/"
BAK_TAR="$BAK_DIR/pr_`date "+%Y-%m-%d-%H_%M_%S"`.tz"

BAK_DEV=`bin/phobrain_property.sh bak.dev`
DFCMD="df -h $BAK_DEV"

echo "== $0  tar $PR_HOME/pr to"
echo "           $BAK_TAR"
echo "       and optionally copy to $BAK_DEV"

if [ ! -d "$BAK_DIR" ] ; then
    echo making bak_dir: $BAK_DIR
    mkdir -p $BAK_DIR
else
    echo === bak_dir is $BAK_DIR ===
fi

if [ -e ~/0log ] ; then
    echo "copying ~/0log to $PR_HOME/pr"
    cp -p ~/0log $PR_HOME/pr
fi

(cd $PR_HOME ; tar -c --exclude build --exclude bak --exclude .gradle --exclude tmp --exclude "*pairs_uniq_[vh]" --exclude single_proc_pairs_uniq_v --exclude "pr_dif*" -z -f $BAK_TAR .git pr)

if [ -e $PR_HOME/pr/0log ] ; then
    echo "removing $PR_HOME/pr/0log"
    rm "$PR_HOME/pr/0log"
fi

echo "== $0  Backed up to `du -sh $BAK_TAR` from `du -sh $PR`"

ls -l $BAK_TAR

if [ -e $BAK_DEV ] ; then
    $DFCMD
    echo "Copy $BAK_TAR to $BAK_DEV? [y|N]: "
    read VAL
    if [ "$VAL" == "y" ] ; then
        cp $BAK_TAR $BAK_DEV/
        $DFCMD
        ls -ltr $BAK_DEV
    fi
    echo -en "\007"
fi
