#!/bin/bash

IMAGE_DESC=`phobrain_property.sh image.desc`

DEST=$IMAGE_DESC/vec_geom/

echo "== $0  Moving .top to $DEST"

if [ ! -d $DEST ] ; then
    echo "=== $0 NOT A DIR: $DEST"
    exit 1
fi

ls *.top

echo === sleeping 5, then moving to $DEST

sleep 5

mv *.top $DEST
