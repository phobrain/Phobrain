#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === funwait.sh - make alerting noises when long job is done.

bark='paplay /usr/share/sounds/gnome/default/alerts/bark.ogg'
drip='paplay /usr/share/sounds/gnome/default/alerts/drip.ogg'
glass='paplay /usr/share/sounds/gnome/default/alerts/glass.ogg'

echo funwaiting - ^C to exit

BASE=1

while [ 1 ] ; do
    $bark ; $bark; $drip ; $drip ; $glass ; $glass
    sleep $BASE
    BASE=$(( BASE + 1 ))
    $bark ; $bark ; $drip ; $glass
    sleep $BASE
    BASE=$(( BASE + 2 ))
    $drip ; $glass
    $drip ; $glass
    $glass ; $glass ; $glass 
    sleep $BASE
    BASE=$(( BASE + 2 ))
    $bark ; $drip ; $glass
    $drip ; $glass
    $drip ; $glass
    $bark ; $drip ; $glass
    sleep $BASE
    BASE=$(( BASE - 1 ))
    $bark ; $glass
    $drip ; $drip ; $drip ; $drip
    $bark ; $glass
    $glass
    sleep 1
    $drip ; $glass
    $drip ; $glass
    $drip ; $glass
    sleep 1
    $drip ; $glass
    $drip ; $drip ; $drip ; $drip
    $drip ; $glass
    sleep $BASE
    BASE=$(( BASE + 3 ))
    $drip ; $glass
    $bark ; $drip ; $glass
    $drip ; $glass
    sleep 1
    $drip ; $glass
    $drip ; $glass
    $drip ; $drip ; $drip ; $drip
    $glass ; $glass ; $glass
done
