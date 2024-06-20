#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

DIR=

if [ "$PHOBRAIN_LOCAL" == "" ] ; then
    if [ ! -d $HOME/phobrain_local ] ; then
        echo "No PHOBRAIN_LOCAL or $HOME/phobrain_local - creating $HOME/phobrain_local in 5 sec"
        sleep 5
        mkdir $HOME/phobrain_local
    fi
    DIR="$HOME/phobrain_local"
else
    if [ ! -d "$PHOBRAIN_LOCAL" ] ; then
        if [ -f "$PHOBRAIN_LOCAL" ] ; then
            echo "PHOBRAIN_LOCAL=[$PHOBRAIN_LOCAL] is a file, must be a dir"
            exit 1
        fi
        echo "Env PHOBRAIN_LOCAL=[$PHOBRAIN_LOCAL] doesn't exist - creating in 5 sec"
        sleep 5
        mkdir "$PHOBRAIN_LOCAL"
    else
        echo "Using env PHOBRAIN_LOCAL: [$PHOBRAIN_LOCAL]"
    fi
    DIR="$PHOBRAIN_LOCAL"
fi

echo $DIR
